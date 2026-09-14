# 产销品数字员工 · Skills 技能包实现方案

> 场景：安徽电信 CPCP 产销品域 · 数字员工（必选场景）
> 目标：将原「智能体平台配置工作流」实现（8 个子工作流 + 14 个插件 + 5 个知识分类）迁移为 **Skills 技能包**实现
> 版本：V1.4　日期：2026-09-14
> V1.1 修订：由 9 技能划分改为**单技能包**——产销品数字员工整体一个技能，8 环节收纳为技能内环节程序，按需加载
> V1.2 修订（对齐实际实现）：① 状态标注为**已实施**（技能包已建成，本地自测通过）；② 第 3 章「逐技能设计」改写为「环节程序 P1~P8 设计」（单技能包内 8 份 references 文档，不再是 9 个 SKILL.md）；③ 子命令数按实际 `cpcp_api.py` 修正为 **17 个**（14 工具 + `build_plan`/`extract_record` 代码节点逻辑 + 节点结果存储查询）；④ 验收清单按实际完成状态勾选；⑤ P1 环节程序与实际文件逐条核对（同构键值合并/引擎单一事实源/出口A 不保存不产出 req_id）
> V1.3 修订（结构精简）：① 8 份逐工作流环节程序（P1~P8）合并为 **4 份流程文档（程序 A~D）**：程序A=需求分析（原P1）、程序B=执行主干（原P2+P3+P5+P4 四环节合为一份串行程序）、程序C=上线审批（原P6）、程序D=查询运维（原P7+P8 合并，两条轻量支线）；② SKILL.md 精简（职责收归"意图路由+核心纪律+目录导航"，约 50 行，环节细节全部下沉 flow 文档）；③ references 收敛为 4 份机制文档：枚举/命名/模板并入 ontology-fields.md（三合一），删除独立的 enums-and-naming.md 与 plan-templates.md；④ 全部业务口径零改动（四类18字段/待补充判定/取值链/req_id/E1~E23/后端硬校验/幂等）
> 原方案基线：《产销品加载AI应用开发方案.md》V2.7（6.5 节已补录 V2.6 Skills 实现说明与 V2.7 脚本修复）、《产销品加载AI应用-细化设计方案.md》V2.0
> V1.4 修订（全部接口去 contractRoot 包裹，基于实测反馈）：① 请求侧全部接口改为**裸报文**——业务参数 JSON 直接置于顶层，删除 `_tcp_cont`/`_transaction_id`/`contract_root_needed` 及包裹分支（后端 mock 只解析顶层字段，包裹后业务参数不可见返回 PARAM_MISSING/5001）；② 出参侧 `_unwrap` 兼容解包保留（后端若返回包裹格式仍可归一）；③ spec_audit 修复 `--config-json-file` 文件传参失效缺陷（原直接读 args.config_json 未走 `_read_arg`）；④ 本地自测脚本新增第 8 项裸报文契约断言（起本地 mock 回显请求体）；⑤ 4.1/4.2/4.3 同步，业务口径与接口路径/入出参契约零改动

---

## 1. 两种实现方式的本质差异

| 维度 | 原方式（智能体平台工作流） | 新方式（Skills 技能包） |
| --- | --- | --- |
| 流程控制载体 | 平台画布编排的子工作流（JSON 节点图，固定 DAG） | 技能包内**流程文档（flow 文档）**，由模型按步骤执行 |
| 工具调用 | 平台插件节点（HTTP 工具显式挂载） | 技能包 `scripts/` 内的可执行脚本（HTTP Mock / API 调用封装） |
| 知识库 | 平台知识库（切片 + 向量召回 + top_k 参数） | 技能包 `references/` 目录下的文档，按需**读取文件**检索 |
| 调度方式 | 智能体 LLM 按意图映射表直调子工作流 | 模型按用户意图**加载对应 SKILL.md** 并遵循其程序执行 |
| 状态传递 | 平台工作流变量 + 节点结果存储插件（req_id 键） | 脚本侧节点结果存储 API（req_id 键不变）+ 对话上下文 |
| 门禁保障 | 后端工具层硬校验 + 提示词纪律 | 提示词/程序纪律 + 后端工具层硬校验（保留，不变） |
| LLM 节点（温度 0.2 的结构性任务） | 平台大模型节点（要素拆解/整合/报告生成） | 主模型按 SKILL.md 中**内嵌提示词模板**执行（同等低温度纪律靠程序约束描述） |

### 1.1 迁移核心原则
1. **业务逻辑零改动**：四类 18 字段口径、待补充判定规则、本体推理引擎、req_id 统一键、异常矩阵全部原样保留；
2. **工作流→程序文档**：每个子工作流的节点表改写为 SKILL.md 中的分步执行程序（步骤=节点，条件分支=程序分支）；
3. **插件→脚本**：14 个工具调用封装为 `scripts/cpcp_api.py` 统一客户端（函数与工具一一对应），模型通过运行脚本发起调用，避免手拼报文；
4. **知识库→参考文档**：K1~K5 文档放入 `references/`，SKILL.md 指明"何时读哪个文件"；
5. **门禁不放松**：审批四环节硬校验、req_id 格式校验等后端校验原样保留；确认门禁沿用 V2.2 口径（语义识别，由 SKILL.md 程序约束保证）。

---

## 2. 技能包总体架构（单技能包，已实施）

> 修订说明（V1.1~V1.3）：产销品数字员工**整体是一个技能包**（`cpcp-product-worker`），不按环节拆分为多个技能。业务环节收纳为技能包内部 **4 份流程文档**（flow-A~D，程序 A~D），由主 SKILL.md 的意图路由表按需加载——等价于原"智能体常驻 + 8 个子工作流按意图直调"的平台结构。**当前状态：技能包已建成并通过本地自测（test_cpcp_api_local.py），后端联调进行中。**

### 2.1 技能包目录结构

```
skills/
├── README.md                          # 迁移映射表 + 验收清单
├── Skills技能包实现方案.md             # 本方案
└── cpcp-product-worker/               # 产销品数字员工技能包（唯一技能）
    ├── SKILL.md                       # 入口：触发条件/目录导航/意图路由/串行主干/调度纪律/开场白
    ├── scripts/
    │   ├── cpcp_api.py                # 17 子命令统一 API 客户端（14 工具 + build_plan/extract_record 代码节点逻辑；tcpCont 拼装/超时重试/错误码归一）
    │   ├── poll_test_progress.py      # 测试进度轮询（等价原代码节点 0304）
    │   └── test_cpcp_api_local.py     # 本地功能自测
    └── references/
        ├── flow-A-requirement.md      # 程序A 需求分析（对应 wf_sub_01）
        ├── flow-B-execution.md        # 程序B 执行主干（合并 wf_sub_02+03+05+04 四环节串行）
        ├── flow-C-approval.md         # 程序C 上线审批（对应 wf_sub_06）
        ├── flow-D-query-ops.md        # 程序D 查询运维（合并 wf_sub_08+07）
        ├── tools-contract.md          # 14 个工具完整契约
        ├── exception-matrix.md        # 异常处理矩阵 E1~E23
        ├── ontology-fields.md         # 本体注册表 + 枚举/命名约定 + plan 模板（三合一）
        └── K1规范|K2资费|K3测试|K4存量|K5FAQ/   # 知识库 26 份文档
```

### 2.2 技能内部工作机制（对应原平台三层结构）

| 原平台构件 | 技能包内对应物 | 加载方式 |
| --- | --- | --- |
| 智能体（提示词常驻） | 主 SKILL.md（角色/意图路由/串行主干/调度纪律） | 常驻 |
| 8 个子工作流 | references/ 4 份流程文档（程序 A~D） | 按意图加载对应单份 |
| 14 个插件 + 3 类代码节点 | scripts/cpcp_api.py（17 子命令）+ poll_test_progress.py | 按程序步骤运行 |
| 知识库（切片召回） | references/K1~K5 目录，按需读文件（K4 按销售品 ID 单文件） | 按环节程序读取指令 |
| 提参/为空提示/异常处置 | 各 flow 文档"前置检查"节 + exception-matrix.md | 程序步骤内引用 |

### 2.3 按需加载原则（上下文成本控制）
- 主 SKILL.md 保持精简（约 50 行，仅含路由与纪律）；流程文档、契约、知识库**只在命中意图后读取对应单份**；
- K4 存量销售品 18 份文件禁止全量读取，仅按 similarOfferId 精确定位单文件；
- 大报文（plan_json/config_json/fields/report）一律走脚本 `--xxx-file` 文件传参，不经模型上下文中转。

---

## 3. 流程文档设计（V1.3 精简：单技能包内 4 份 flow 文档，程序 A~D）

> 单技能包内不再设多个 SKILL.md；原 8 份逐工作流环节程序（P1~P8）已合并为 4 份流程文档（references/flow-*.md），调度与路由职责收归主 SKILL.md。合并原则：**交互节奏相同且顺序固定的环节合并为一份串行程序**（P2/P3/P5/P4 四环节一次确认后连续跑完，无需四份文档拆分打断；P7/P8 同为轻量查询支线）。每份 flow 文档结构统一为：`标题（含对应原子工作流）→ 触发条件 → 前置检查 → 执行程序（分步，步骤=原节点）→ 禁止事项`。步骤编号与原子工作流节点编号对齐，便于验收对照。

### 3.1 主 SKILL.md（总调度，常驻，V1.3 精简版约 50 行）
- **触发**：任何产销品相关用户输入（入口，常驻加载）。
- **核心内容**：
  - 角色定义（原【角色】要点）；
  - 目录导航表（按需加载，禁止一次全读；指向 flow-A~D 与参考文档/知识库）；
  - 意图→程序路由表（原意图→子工作流映射表收敛为 5 类：提报/修改需求→A、确认执行/重新执行→B、发起审批→C、查询审批进度/运行监控→D、业务问答→按类别直读 K1~K5；超出范围→拒答话术）；
  - 核心纪律 5 条（出参逐字引用/仅依据出参字段判成败、主干串行禁止并行跳过重复、未确认不配置、审批须四环节全成+用户确认、敏感信息摘要输出）；
  - 脚本调用约定与开场白（`--xxx-file` 大报文传参、`CPCP_BASE_URL`、错误码见 exception-matrix.md）。

### 3.2 程序A `flow-A-requirement.md`（需求分析）
对应 wf_sub_01 的 7 环节新链路（原 P1），节点→步骤映射：

| 原节点 | 程序步骤 | 说明 |
| --- | --- | --- |
| 节点2 需求理解与要素拆解 [LLM] | 步骤1 | 按程序内嵌提取规则提取 18 字段要素（elements_json 固定18项 + need_summary），未提及项 value 填空字符串；含强制同义映射表与自检（月费表述而固定费为空=提取失败必须回填） |
| 节点3 相似产品查询 [插件] | 步骤2 | 运行 `python scripts/cpcp_api.py similar_offer --desc "<need_summary>"`；resultCode==1 或未命中走"无相似产品"分支不中断（E1） |
| 节点4 产品信息整合 [LLM] | 步骤3 | **同构键值合并**（按 field 名逐字段对齐 offerInfo 四类18字段数组），可选读取 K4 对应销售品单文件辅助核对（禁止覆盖用户原始需求） |
| 节点31 字段本体推理 [插件] | 步骤4 | 运行 `cpcp_api.py ontology_reason --fields-json <fields_output>`（action=reason 一体推理：校验+修正回写+默认值补全，source 自动改标"本体推理"，仅套餐固定费维持待补充） |
| 节点004a 拆分方案字段 [代码] | 步骤5 | 运行 `cpcp_api.py build_plan`（req_id 系统生成 PLAN+时间戳+3位随机、plan_json 组装、plan_md 代码生成四列表格、pending_fields 反查 value=待补充） |
| 节点5 待补充项判断 [选择] | 步骤6 | pending_fields 长度==0 → 步骤7；非空 → 直接走"有待补充"结束（**不保存执行方案、不产出 req_id，从源头禁止进入智能配置**）；判定唯一事实源=引擎反查结果 |
| 节点6 保存执行方案 [存储] | 步骤7 | 运行 `cpcp_api.py save_node_result --req-id <req_id> --node requirement --result-json <plan_json>`；修改场景同键覆盖写 |
| 节点7/8 双结束 | 步骤8 | 出口A（有待补充：提示补充，确认无效）/ 出口B（已保存：返回 req_id 并等待确认中断点） |

- **references/ontology-fields.md**：字段本体注册表（18 字段的枚举/格式/默认值/兜底口径，源自主方案 3.4.2）+ 枚举/命名约定 + plan_json/plan_md/req_id 模板（三合一），供模型在步骤3 整合时对照（引擎仍是单一事实源，文档仅辅助）。

### 3.3 程序B `flow-B-execution.md`（执行主干：智能配置→稽核→资费→测试）
> V1.3 精简：原 P2/P3/P5/P4 四份环节程序合并为一份串行程序——用户确认执行后**一次跑完四环节，中途不停顿**，仅环节失败时中断。对应 wf_sub_02→03→05→04。

- **触发**：用户确认类回复；或【重新执行】按 fail_node 续跑（STAGE1~4 映射环节1~4，已成功环节凭存储记录回放，不重复调用写接口）；
- **通用步骤模式**（四环节共用）：自查上游（query_node_result + extract_record，total=0 → E5）→ 调用本环节接口 → 仅依据出参字段判定（失败按 E 编号中断引导）→ save_node_result 存储（失败/超时也存储）→ 打印【环节N/名称】结果；
- **环节1 智能配置**（原P2，6 节点）：`save_product_config`（plan_json 原文原样透传，唯一写入步骤，不自动重试；SUCCESS/PARTIAL 通过，FAIL → E6）→ 存 config；
- **环节2 规格稽核**（原P3，7 节点）：`spec_audit`（audit_scene=all，同步无轮询；pass=0 → E8，超时重试 1 次 → E7）→ 存 spec；
- **环节3 资费校准**（原P5，10 节点）：`billing_verify`（check_scene=all）→ 风险解读（先读 K2资费_叠加优惠约束说明 文档，不得新增风险结论；pass=0 → E9）→ 存 fee；
- **环节4 自动测试**（原P4，13 节点，含受理验证）：`offer_test` → `test_scenes`（场景为空 → E11）→ `poll_test_progress.py` 轮询（间隔 5s/最多 360 次/连续 5 次失败终止）→ `test_result` → 内嵌模板生成测试报告（**受理验证结论强制章节**，orderId/offerInstId 为空标注人工核实（E14）；test_passed=全部测点一致且受理凭证非空）→ 存 test；
- 四环节全部成功 → 打印成功详情，中断等待审批确认（衔接程序C）。

### 3.4 程序C `flow-C-approval.md`（上线审批）
对应 wf_sub_06（11 节点，原 P6）：
1. **触发门禁**：仅在执行主干四环节全部成功**且用户明确回复"发起审批"**后加载（用户仅说"帮我上线"未确认时，先提示"回复【发起审批】后才能提交审批流"）；
2. 串行自查 5 类结果（config/spec/fee/test/requirement，链式顺序非并行；任何一步 total==0 → E15/E18 中断）；
3. 合成结构化汇总；
4. 按内嵌模板生成 7 章节报告（需求摘要/配置落地/稽核结论/资费结论/测试统计/**受理验证结论（强制）**/上线建议；全通过→"建议上线"；只基于输入数据不新增结论）；
5. 存储 report；
6. `submit_approval`（后端硬校验 req_id 四环节结果齐全，跳步无法推送；推送失败重试 1 次后 E16 中断；幂等：同 product_id 重复提交返回原 approval_id）→ 输出审批单号。

### 3.5 程序D `flow-D-query-ops.md`（查询运维，两条轻量支线）
> V1.3 精简：原 P7（监控运维，wf_sub_07）与 P8（审批进度查询，wf_sub_08）合并为一份流程文档，按用户意图二选一执行支线。

- **支线D-1 审批进度查询**：`approval_status`（approval_id/product_id 至少一个，缺失时先追问）→ 按"审批单号｜状态｜当前环节｜意见｜更新时间"格式输出；驳回时附原因并提示可修改执行方案后重新发起；查无审批单 → E21；
- **支线D-2 运行监控与告警**：`query_monitor`（product_id 必填，缺失时先追问不编造兜底，date_range/metric 可选）→ 异常判定（error_count>0 或 fee_error_rate>0.1）→ 异常时 `send_alert`（文案含产品与异常摘要）→ 输出指标摘要；
- 轻量查询可直接运行脚本子命令，无需加载完整程序（SKILL.md 路由表标注）。

---

## 4. 脚本层设计（scripts/cpcp_api.py）

### 4.1 定位
把原平台插件层（含报文封装、超时重试、错误码归一）封装为**统一 CLI 客户端**，模型只负责"何时调、传什么参"，不手拼报文。已实现为 `scripts/cpcp_api.py`（17 个子命令，见 4.2）。**V1.3 起全部接口改为裸报文**（contractRoot/tcpCont 包裹整体移除，实测后端 mock 只解析顶层字段）。

### 4.2 子命令 ↔ 原工具映射（17 个，已实现）

| 子命令 | 原工具 | 方法/路径 |
| --- | --- | --- |
| `similar_offer` | 工具1 query_similar_offer | POST /api/v1/appstore/similar/offer/query（裸报文；出参 similarOffer 单对象含 offerInfo 同构 fields 数组，V2.5 口径） |
| `spec_audit` | 工具2 realtime_spec_audit | POST /api/v1/appstore/audit/realtime（裸报文；原 tcpCont svcCode/appKey/dstSysId 已随包裹移除） |
| `offer_test` | 工具3 offer_test | POST /api/v1/appstore/test/offer/start |
| `test_scenes` | 工具4 get_test_scenes | POST /api/v1/appstore/test/offer/scenes |
| `test_progress` | 工具5 get_test_progress | POST /api/v1/appstore/test/offer/progress（单次不重试，轮询由 poll 脚本控制） |
| `test_result` | 工具6 get_test_result | POST /api/v1/appstore/test/offer/result |
| `save_product_config` | 工具7 | POST /api/v1/appstore/product/config/save（**不自动重试**防重复写入；确认门禁已按 V2.2 移除，confirmed 为兼容字段仅记录） |
| `billing_verify` | 工具8 check_billing_rule | POST /api/v1/appstore/rules/verify |
| `submit_approval` | 工具9 | POST /api/v1/appstore/approval/submit（后端四环节硬校验；幂等） |
| `query_monitor` | 工具10 | GET /api/v1/appstore/product/monitor |
| `send_alert` | 工具11 | POST /api/v1/appstore/alert/send（枚举校验 high/middle/low） |
| `approval_status` | 工具13 | GET /api/v1/appstore/approval/status（approval_id/product_id 至少一个，缺失报 PARAM_MISSING） |
| `ontology_reason` | 工具14 field_ontology_reason | POST /api/v1/appstore/ontology/fields（action=reason 一体推理） |
| `save_node_result` / `query_node_result` | 节点结果存储查询插件 | POST /result/save、GET /result/query（直连后端；64KB 超限/req_id 格式由脚本前置+后端 5002/5004 兜底） |
| `build_plan` | 代码节点 004a（承接） | 本地逻辑，不发请求 |
| `extract_record` | 代码节点 CODE_EXTRACT_RECORD（承接） | 本地逻辑，不发请求 |

### 4.3 脚本内置的通用行为（等价原插件封装层，已实现）
- 请求侧裸报文（V1.3 基于实测反馈）：全部接口请求体直接为业务参数 JSON（业务参数置于顶层），contractRoot/tcpCont 报文头拼装逻辑整体移除（后端只解析顶层字段，包裹后业务参数不可见返回 PARAM_MISSING/5001）；
- 出参解包兼容：contractRoot 包裹 / resultObject 包裹 / 裸报文三种返回自动归一（`_unwrap`）；
- 超时：同步类 60s（稽核类重试 1 次）、异步轮询类 30s；网络错误重试 1 次；save_product_config 不自动重试；
- 错误码归一输出：PARAM_MISSING / HTTP_xxx / NET_ERROR / TIMEOUT / PARSE_ERROR / ONTOLOGY_EMPTY（参数缺失/枚举非法/64KB 超限等本地前置校验以 exit code 2 + JSON 输出）；
- ontology_reason 空返回防护（V1.3 基于实测反馈新增）：resultCode=0 但 fields_json 为空数组/空串时报 ONTOLOGY_EMPTY（exit 2），提示引擎推理能力缺失/未实现、禁止跳过该步骤直接组装方案（异常矩阵 E2b）；build_plan 对空 fields 同样拒绝组装；
- 大报文支持 `--xxx` 内联与 `--xxx-file` 文件两种传参（plan_json/config_json/fields_json/result_json/query_json/report）；
- 出参默认 JSON 原样打印（供模型读取与后续步骤引用）；
- 环境变量 `CPCP_BASE_URL`（默认 `http://10.86.13.201:31281`）。

### 4.4 build_plan 子命令（承接种子代码节点 004a，已实现）
`python scripts/cpcp_api.py build_plan --fields-json <推理后fields>`（或 `--fields-json-file`）：
- req_id = `PLAN` + datetime.now()%Y%m%d%H%M%S + 3 位随机数（LLM 不参与生成，每次唯一）；
- plan_json = {req_id, fields, pending_fields}（pending_fields 反查 value=待补充）；
- plan_md = 代码生成四列表格（字段分类/字段名称/字段值/来源），与 fields 严格一致。

### 4.5 extract_record 子命令（承接代码节点 CODE_EXTRACT_RECORD，已实现）
`python scripts/cpcp_api.py extract_record --query-json <query_node_result 出参>`：
- 提取 `list[0].result_json` 原文输出 `{"record_json": ...}`；
- list 为空（total=0）→ exit 2 + PARAM_MISSING 提示（E5），禁止数组整体透传。

### 4.6 poll_test_progress.py（承接代码节点 0304，已实现）
伪代码与细化设计 3.4.4 一致：interval=5s、max_retry=360、连续 5 次查询失败终止、退出条件 done/failed，输出 `{"done":..., "failed":..., "failIndex":..., "fail_reason":...}`；done=true 退出码 0、failed 退出码 1、超时退出码 3、连续失败退出码 2（供环节程序分支判定）。

---

## 5. 与原实现的映射总表（已实施）

| 原平台构件 | 数量 | Skills 对应物 | 迁移方式 |
| --- | --- | --- | --- |
| 主流程 wf_cpcp_main | 1（已弃用归档） | —（原 V1.7 已由 LLM 调度替代） | 无需迁移 |
| 子工作流 wf_sub_01~08 | 8 | references/ 4 份流程文档（程序 A~D） | 节点表→分步程序（逐节点对照） |
| 智能体提示词（角色+技能+限制+意图映射表） | 1 | 主 SKILL.md（总调度） | 逐字迁移 |
| 插件工具 1~11、13、14 | 14 | `scripts/cpcp_api.py` 子命令 | 契约不变，封装层逻辑入脚本 |
| 节点结果存储查询插件 | 1（复用平台） | `cpcp_api.py save/query_node_result`（直连后端 API） | 契约不变 |
| 大模型节点（温度 0.2，8 处） | 8 | flow 文档内嵌提示词模板/提取规则 | 提示词全文迁移，温度纪律改为程序约束描述 |
| 代码节点（004a 拆分、CODE_EXTRACT_RECORD、0304 轮询） | 3 类 | `build_plan` / `extract_record` 子命令 / `poll_test_progress.py` | 逻辑原样入脚本 |
| 知识库 K1~K5 | 5 类 26 份 | `references/K1规范|K2资费|K3测试|K4存量|K5FAQ/`（按需读文件） | 文件原样复制，挂载点→flow 文档读取指令 |
| 后端硬校验（四环节门禁/req_id 格式 5002/5006/幂等） | — | 后端服务原样保留 | 不变 |

---

## 6. 优势与代价评估

### 6.1 Skills 方式的收益
1. **去平台绑定**：不再依赖九思平台画布编排/插件市场/知识库切片配置，技能包可在任何支持 Skills 协议的 Agent 运行时加载（含 Claude/自建 Agent 框架）；
2. **版本化管理**：SKILL.md + 脚本 + 参考文档全部是纯文本/代码文件，可 git 版本管理、code review、diff 升级（原平台 JSON 导出难以 diff）；
3. **调试透明**：脚本调用有标准输出/错误码，环节失败可直接重放单条命令（原平台需整流重跑）；
4. **知识免切片**：K4 按文件读取替代向量召回，无切片断裂/召回参数调优问题；
5. **演进灵活**：替换真实接口只改 `cpcp_api.py` 的 BASE_URL/实现，SKILL.md 程序与契约文档不动（与原"契约不变"原则一致）。

### 6.2 需要接受的代价与对策
| 代价 | 对策 |
| --- | --- |
| 原"固定 DAG"的执行确定性由平台保证，Skills 下依赖模型遵循程序 | ① SKILL.md 程序步骤编号+强制顺序表述；② 保留后端四环节硬校验兜底（跳步审批必然被拒）；③ 每步骤"仅依据脚本出参字段判定成败"写入纪律 |
| 知识检索精度从向量召回变为模型读文件 | K4 按销售品 ID 精确单文件定位；K1~K3/K5 文档量小，全文读取可控 |
| 原平台"提参/为空提示"交互缺失 | 各 flow 文档的"前置检查"节写明必填参数与缺失中断/追问话术（沿用原为空提示文案） |
| 温度/多轮参数控制失效 | 报告/整合类步骤在 flow 文档中加"逐字引用输入数据，不新增结论"约束（与原提示词一致） |

---

## 7. 实施计划与验收

### 7.1 实施步骤（S1~S4 已完成，S6 精简改造已完成，S5 进行中）
| 阶段 | 工作项 | 状态 |
| --- | --- | --- |
| S1 | 搭建技能包目录 + `cpcp_api.py` 子命令 | ✅ 已完成（17 子命令编译通过、本地自测通过） |
| S2 | 主 SKILL.md（总调度） | ✅ 已完成（意图路由/串行纪律/中断程序） |
| S3 | 环节程序 P1~P8（含内嵌提示词模板与读取指令） | ✅ 已完成（逐环节对照原节点表） |
| S4 | references 文档迁移（契约/异常矩阵/枚举/K1~K5） | ✅ 已完成（26 份知识库文档 + 5 份机制文档） |
| S6 | 结构精简：8 份 P 文档合并为 4 份 flow 文档、枚举/模板并入 ontology-fields.md、SKILL.md 精简 | ✅ 已完成（业务口径零改动） |
| S5 | 端到端联调（正向 + 反向用例） | ⏳ 进行中（按原 3.5 节 21 条用例回归） |

### 7.2 验收清单（对齐原附录 A~E，Skills 版）
- [x] 单技能包结构成立：主 SKILL.md 路由 4 份流程文档（程序 A~D），触发描述互不冲突；
- [x] `cpcp_api.py` 17 个子命令编译通过、本地自测通过（build_plan 唯一 req_id/E5/E22/5004 等）；
- [ ] 14 个后端接口子命令逐一连通后端（含错误码 PARAM_MISSING / 5002 / 5006 验证）；
- [ ] SKILL.md 意图路由：各类意图命中程序 A/B/C/D；串行主干（程序B）环节1→2→3→4 顺序执行；
- [ ] 用例#2 未确认不配置（拒绝进入程序B，CRM 无写入）；
- [ ] 用例#6/#7 稽核驳回中断 + 【重新执行】续跑（已成功环节回放不重复写）；
- [ ] 用例#18 四环节不全调 `submit_approval` 被后端拒绝；
- [ ] 程序B 环节4 测试报告含受理验证结论（orderId/offerInstId 为空时标注人工核实）；
- [ ] 18 销售品全量兼容回归（对照原附录 E）；
- [ ] K4 单文件读取命中正确销售品（900102308 套外资费问题可答）。

## 附录 A：原工作流节点 ↔ 流程文档对照索引（V1.3 精简后）

| 原子工作流 | 节点数/边数 | 流程文档 | 说明 |
| --- | --- | --- | --- |
| wf_sub_01 需求分析 | 9/8 | 程序A flow-A-requirement（8 步） | 004a 代码→`build_plan` 子命令；双结束→双出口 |
| wf_sub_02 智能配置 | 6/5 | 程序B flow-B-execution 环节1 | 提取原文→`extract_record` 子命令（脚本内亦有内置提取） |
| wf_sub_03 规格稽核 | 7/6 | 程序B 环节2 | 整改建议 LLM→内嵌模板 |
| wf_sub_04 自动测试 | 13/12 | 程序B 环节4 | 轮询代码节点→`poll_test_progress.py` |
| wf_sub_05 资费校准 | 10/9 | 程序B 环节3 | 风险解读前读 K2 文档 |
| wf_sub_06 上线审批 | 11/10 | 程序C flow-C-approval（6 步） | 串行自查 5 类结果改为顺序 5 次调用 |
| wf_sub_07 监控运维 | 5 | 程序D flow-D-query-ops 支线D-2 | 选择器→程序 if 判定 |
| wf_sub_08 审批进度查询 | 3 | 程序D 支线D-1 | — |

## 附录 B：流程文档（flow 文档）统一模板

```markdown
# <程序中文名> —— 程序 <A/B/C/D>

（对应原子工作流 `wf_sub_XX`。参照知识库时在此声明读取指令。）

## 触发条件
<执行主干串行进入 / 用户意图命中 / fail_node 续跑>

## 前置检查
<必填参数清单与缺失中断话术（沿用原"为空提示"）>

## 执行程序
### 步骤N：<标题>（原节点X）
```bash
python scripts/cpcp_api.py <子命令> [参数]
```
- 判定：<仅依据出参字段 XXX 判定成败>
- 失败：<对应异常矩阵 E 编号处置>
...
N. 输出：<按 SKILL.md 统一模板打印环节结果>

## 禁止事项
- <原调度纪律中与本程序相关的禁止项 + 续跑回放约束>
```
