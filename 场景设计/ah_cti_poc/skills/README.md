# 产销品数字员工 · Skills 技能包

> 本技能包将原「九思智能体平台工作流配置」实现（8 个子工作流 + 14 个插件 + 5 类知识库）迁移为 **Skills 技能包**方式实现——**产销品数字员工整体是一个技能**（单一技能包 `cpcp-product-worker`）。结构已二次精简：8 份逐工作流环节程序（P1~P8）合并为 **4 份流程文档（程序 A~D）**，SKILL.md 收归"意图路由 + 核心纪律 + 目录导航"。**当前状态：技能包已建成并通过本地自测，端到端联调进行中。**
> 设计方案：《skills/Skills技能包实现方案.md》V1.4；原方案基线：《方案/产销品加载AI应用开发方案.md》V2.7（见 1.4/6.5 节）、《方案/产销品加载AI应用-细化设计方案.md》V2.0。

## 技能包结构

```
skills/
├── README.md                                  # 本文件（迁移映射表 + 验收清单）
├── Skills技能包实现方案.md                     # 迁移方案总纲
└── cpcp-product-worker/                       # 产销品数字员工技能包（唯一技能）
    ├── SKILL.md                               # 技能入口：角色/目录导航/意图路由/核心纪律/脚本调用约定（约 50 行）
    ├── scripts/
    │   ├── cpcp_api.py                        # 14 工具统一 API 客户端（裸报文请求/超时重试/错误码归一）
    │   ├── poll_test_progress.py              # 测试进度轮询（等价原代码节点 0304）
    │   └── test_cpcp_api_local.py             # 本地功能自测（不依赖后端，7 项全通过）
    └── references/
        ├── flow-A-requirement.md              # 程序A 需求分析 ← wf_sub_01（8 步，出口A/B）
        ├── flow-B-execution.md                # 程序B 执行主干 ← wf_sub_02+03+05+04（智能配置→稽核→资费→测试，一次串行跑完）
        ├── flow-C-approval.md                 # 程序C 上线审批 ← wf_sub_06（五类自查+7章节报告+审批推送）
        ├── flow-D-query-ops.md                # 程序D 查询运维 ← wf_sub_08+07（审批进度 + 运行监控/告警，两条轻量支线）
        ├── tools-contract.md                  # 14 个工具完整入出参契约
        ├── exception-matrix.md                # 异常处理矩阵 E1~E23
        ├── ontology-fields.md                 # 四类18字段本体注册表 + 枚举/命名约定 + plan_json/plan_md/req_id 模板（三合一）
        ├── K1规范/   （3 份）                  # 业务规范（原知识库 K1）
        ├── K2资费/   （2 份）                  # 资费规则（原 K2）
        ├── K3测试/   （2 份）                  # 测试规范（原 K3）
        ├── K4存量/   （18 份）                 # 存量销售品（原 K4，按销售品 ID 单文件）
        └── K5FAQ/    （1 份）                  # 高频问答（原 K5）
```

## 技能内部工作机制

- **主 SKILL.md（常驻）**：角色定义、目录导航（按需加载，禁止一次全读）、意图→程序路由表、核心纪律 5 条、脚本调用约定；环节细节全部下沉到 4 份 flow 文档；
- **流程文档（按意图加载）**：程序A=需求分析（独立交互环）；程序B=执行主干（原 P2/P3/P5/P4 四环节合并为一份串行程序，中途不停顿、仅异常中断）；程序C=上线审批（四环节全成且用户确认后）；程序D=查询运维（审批进度 + 监控告警两条轻量支线）；
- **脚本层**：`cpcp_api.py` 17 个子命令对应原 14 工具 + 2 个代码节点逻辑（`build_plan`/`extract_record`）+ 节点结果存储直连；`poll_test_progress.py` 测试进度轮询；
- **参考文档**：契约/异常矩阵/本体注册表（含枚举命名与模板）按需读取，替代原平台"提参/为空提示/召回参数"机制。

## 迁移映射总表

### 原子工作流 → 流程文档
| 原子工作流 | 流程文档 | 关键承接物 |
| --- | --- | --- |
| wf_cpcp_main（主流程，已弃用） | SKILL.md | 意图路由 + 串行控制 + 两次确认门禁（方案确认/审批确认） |
| wf_sub_01 需求分析（9/8 节点/边） | 程序A flow-A-requirement（8 步） | 节点2/4 提示词内嵌；004a→`build_plan`；本体注册表→ontology-fields.md |
| wf_sub_02 智能配置（6/5） | 程序B flow-B-execution 环节1 | CODE_EXTRACT_RECORD→`extract_record`（脚本内亦有内置提取） |
| wf_sub_03 规格稽核（7/6） | 程序B 环节2 | 整改建议 LLM→内嵌模板 |
| wf_sub_05 资费校准（10/9） | 程序B 环节3 | K2 挂载→读取指令 |
| wf_sub_04 自动测试（13/12） | 程序B 环节4 | 轮询代码节点→`poll_test_progress.py` |
| wf_sub_06 上线审批（11/10） | 程序C flow-C-approval（6 步） | 7 章节报告提示词内嵌；四环节硬校验保留在后端 |
| wf_sub_07 监控运维（5 节点） | 程序D flow-D-query-ops 支线D-2 | 选择器→程序 if 判定 |
| wf_sub_08 审批进度查询（3 节点） | 程序D 支线D-1 | — |

### 原插件工具 → 脚本子命令
| 原工具 | 子命令 | 原工具 | 子命令 |
| --- | --- | --- | --- |
| 工具1 query_similar_offer | `similar_offer` | 工具8 check_billing_rule | `billing_verify` |
| 工具2 realtime_spec_audit | `spec_audit` | 工具9 submit_release_approval | `submit_approval` |
| 工具3 offer_test | `offer_test` | 工具10 query_product_monitor | `query_monitor` |
| 工具4 get_test_scenes | `test_scenes` | 工具11 send_alert | `send_alert` |
| 工具5 get_test_progress | `test_progress` | 工具13 query_approval_status | `approval_status` |
| 工具6 get_test_result | `test_result` | 工具14 field_ontology_reason | `ontology_reason` |
| 工具7 save_product_config | `save_product_config` | 节点结果存储/查询 | `save_node_result` / `query_node_result` |
| 代码节点 004a / CODE_EXTRACT_RECORD | `build_plan` / `extract_record` | | |

### 原知识库挂载点 → 文件读取指令
| 原挂载点 | 流程文档中读取指令 |
| --- | --- |
| wf_sub_01 节点2/4（K1+K4） | 程序A：本体注册表 ontology-fields.md；K4 仅读取相似产品对应单文件 |
| wf_sub_04 节点6（K3） | 程序B 环节4：生成报告前读取 K3测试_销售品测试报告模板_V1.0.md |
| wf_sub_05 节点3（K2） | 程序B 环节3：风险解读前读取 K2资费_叠加优惠约束说明_V1.0.md |
| 智能体问答（K5 及全部分类） | SKILL.md 业务问答路由：按问题类别读取对应 K1~K5 文档 |

## 使用方式
1. **安装技能**：将 `cpcp-product-worker/` 目录注册到支持 Skills 协议的 Agent 运行时（SKILL.md 为入口）；
2. **配置后端**：设置环境变量 `CPCP_BASE_URL`（默认 `http://10.86.13.201:31281`，即原模拟服务）；
3. **运行**：用户对话 → SKILL.md 意图路由 → 加载对应流程文档 → 按程序运行脚本并依据出参判定。

## 快速自测
```bash
cd skills/cpcp-product-worker
# 本地功能自测（不依赖后端，7 项）
python scripts/test_cpcp_api_local.py
# 联通后端自测（需模拟服务在线）
python scripts/cpcp_api.py similar_offer --desc "5G-A 单品套餐 月费199元 30G流量"
python scripts/cpcp_api.py query_monitor --product-id 900102308
# 字段本体推理（后端引擎空返回时正确触发 ONTOLOGY_EMPTY exit 2，E2b）
python scripts/cpcp_api.py ontology_reason --action reason --fields-json-file <fields文件>
```

## 验收清单（对齐原方案 3.5 节 21 条用例与附录 E）
- [x] `cpcp_api.py` 17 个子命令编译通过、本地自测通过（build_plan 唯一 req_id/E5/E22/5004 等）；
- [ ] 14 个后端接口子命令逐一连通后端（含错误码 PARAM_MISSING / 5002 / 5006 验证）；
- [ ] SKILL.md 意图路由：各意图命中程序 A/B/C/D；串行主干（程序B）环节1→2→3→4 顺序执行；
- [ ] 用例#2 未确认不配置（拒绝进入程序B，CRM 无写入）；
- [ ] 用例#6/#7 稽核驳回中断 + 【重新执行】续跑（已成功环节回放不重复写）；
- [ ] 用例#18 四环节不全调 `submit_approval` 被后端拒绝；
- [ ] 程序B 环节4 测试报告含受理验证结论（orderId/offerInstId 为空时标注人工核实）；
- [ ] 18 销售品全量兼容回归（原附录 E）；
- [ ] K4 单文件命中：问 900102308 套外资费 → 命中阶梯计费描述。

## 与原实现的兼容性说明
- **后端零改动**：模拟服务 14 条路由、NodeResultService 硬校验（req_id 格式 5002 / 唯一性 5006 / 四环节门禁 / 幂等）原样保留；
- **契约零改动**：脚本调用路径/入参/出参与《产销品场景部分能力接口清单.xlsx》及原插件导出 JSON 一致，后续替换真实实现仅改 BASE_URL；
- **业务口径零改动**：四类18字段、待补充判定（仅套餐固定费）、来源三态（原始需求/AI推理/本体推理）、异常矩阵 E1~E23 全部原样迁移。
