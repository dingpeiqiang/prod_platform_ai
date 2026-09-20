# 产销品加载 AI 应用 · 细化设计方案
> 实现方式：**V2.0 工作流重塑**——11 个子工作流 JSON（`wf_sub_00~10`，位于 `场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/`，由 `gen_workflows_v2.py` 生成）+ 智能体（LLM）按提示词【意图→工作流映射表】语义识别直调 + `knowledge/` 知识库 + 后端 `AppStoreV16Controller`（`/api/v1/appstore/*` 适配端点）。原 `skills/cpcp-product-worker` 技能包已废弃。
> 场景：安徽电信 CPCP 产销品域 · 数字员工（必选场景）
> 版本：V2.1（真实执行验证）　日期：2026-09-19
> 依据文档：《产销品加载AI应用开发方案.md》（V2.9，下称"主方案"）、《工作流配置重塑改造清单.md》（下称"重塑清单"）、《工作流JSON开发规范.md》（下称"工作流规范"）

## 版本记录
| 版本 | 日期 | 变更说明 |
| --- | --- | --- |
| V2.3 面板 req_id 透传 + 页面 stage 联动 | 2026-09-20 | **结束节点面板 url 透传 `req_id` + 配置工作台按 `stage` 动态联动**：`gen_panel_code` 生成的面板 url 追加 `&req_id=<实值>`（读取 `args.params.req_id`，00~06 绑定起始节点入参、07~10 空串），`panel_inputs(...)` 新增 `req_seq` 参数；`config-workbench.html` 解析 `req_id`，导航步进进度（已完成勾选 / 当前高亮）与默认 Tab 按 `stage` 动态加载（8 环节口径，08/09/10 辅助子流按就近展示），url 目标形态 `config-workbench.html?offer_id=..&name=..&chatId=..&req_id=..&stage=<N>&view=stage`。生成器 + 13 个 JSON 同步（详见《工作流JSON开发规范.md》v1.6）。 |
| V2.2 面板收敛 | 2026-09-19 | **结束节点页面地址收敛为单面板单 url（对齐参考示例形态）**：每个子工作流结束节点不再内联 xsbot-panel 代码块（此前已抽为独立代码节点 type=6 `panel_code_node` 构建 `{panel}` 片段），本次进一步把面板从「配置工作台(view=workbench) + 环节业务页(view=stage)」**双面板**收敛为**单面板、单 url**：url 统一用环节业务页 `config-workbench.html?offer_id=..&name=..&chatId=<实值>&stage=<N>&view=stage`，`title` 用环节业务名，`panel` 无 `**页面地址：**` 前缀、**JSON 紧凑无空格**、格式 `\n\n```xsbot-panel {紧凑JSON} ```\n`；移除 `product-detail.html` 单品运营看板外链与 view=workbench 冗余面板。生成器 `gen_workflows_v2.py` 同步（`gen_panel_code` 改单面板紧凑拼装、删除 `config_workbench_panel/biz_panel/xsbot_panel_block/MONITOR_PAGE`、node618 提示词去除内联看板与多余入参），11 个 JSON 重新生成、`_validate_flows.py` + 14 个 panel 代码节点执行校验全 PASS；详见《工作流JSON开发规范.md》§十一（单面板口径）。 |
| V2.1 实跑验证 | 2026-09-19 | **端到端真实执行验证（业务口径与工具契约零改动）**：以 `wf_runner.py` strict 模式（真实 LLM qwen3-30b-a3b + 真实网关端点，零回退）从「需求提报」串行跑通 8 个子工作流，实测实体 ID：`req_id=PLAN20260919182810908`、req 审批单 `AP202609191828100018`、`offer_id=982810908`、测试 `globalId=50202609191828404195108786`、上线审批单 `AP202609191829100019`（通过）。主链路端点 live 确认：result/save、result/query、audit/realtime、product/config/save、billing/rules/verify、test/offer/start、test/offer/result、approval/submit、product/monitor、similar/offer/query。实测暴露差异点（待整改）：① 渠道「APP」未显性落地（来源标注"模型推测"）；② 实跑串行顺序为 02→03→**04→05**（测试先于资费），致 wf_sub_04 读取 fee 结果为空、受理场景 S_O_TC/S_U_TC 未执行，整体结论退化为「⚠️ 评估风险后上线」；③ `test/offer/scenes` 未调用，受理凭证 orderId/offerInstId 为空；④ /report/download、/script/download 仍占位（backend_pending=1）。详见《产销品加载AI应用-端到端演示剧本.md》1.5 节。 |
| V2.0 重塑 | 2026-09-18 | **整体实现方式从"Skills 技能包"重塑回"工作流配置"（V2.0 基线，业务口径与工具契约零改动）**：① 废弃 `skills/cpcp-product-worker`（SKILL.md/flow-A~D/35 子命令/scripts/），回到并重塑 11 个子工作流 JSON（`wf_sub_00~10`，位于 `场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/`，由 `gen_workflows_v2.py` 生成），意图由智能体（LLM）按提示词【意图→工作流映射表】语义识别后直调子工作流；② 确定性逻辑从技能包脚本子命令内嵌为**类型6 代码节点**：`merge_nested(CODE_MERGE_NESTED)`、`render_table(CODE_RENDER_TABLE)`、`validate_elements(CODE_VALIDATE_ELEMENTS, wf_sub_01 节点108)`、`get_template(CODE_GET_TEMPLATE)`、`render_requirement_report(CODE_RENDER_REQ, wf_sub_00)`、`map_fixed_cases(CODE_MAP_FIXED_CASES, wf_sub_04 节点315)`、`extract_record(CODE_EXTRACT_RECORD)`、`poll_progress(CODE_POLL_PROGRESS)` 等；③ 后端新增 7 个适配端点（`AppStoreV16Controller /api/v1/appstore/*`）：`POST /ops/root-cause`、`POST /ops/work-orders`、`POST /shelf-compliance`、`POST /validate-nested`、`POST /explain`、`POST /report/download`、`POST /script/download`，网关 `BASE_URL=http://10.86.13.201:31281`；④ 知识库迁至 `knowledge/`（原 `references/`、`skills/` 废弃）；⑤ 环节覆盖对齐 V2.0：需求提报(wf_sub_00 含需求工单审批 requirement 轨)、需求分析(wf_sub_01 模板轨，含 validate_nested 本体校验闸节点110/explain)、自动测试(wf_sub_04 九章节正式版报告+31 条固定用例)、上线审批(wf_sub_06 双轨+自动上线)、监控运维(wf_sub_07 根因+工单闭环)、存量查询(wf_sub_09)、存量合规(wf_sub_10)；⑥ 接口/工具契约补充 7 个新适配端点；业务口径（字段体系/异常矩阵/门禁/契约）保持不变，仅替换实现载体表述 |
| V2.7 | 2026-09-15 | **输出结构铁律强化（防汇总块缺失/术语污染）**：① SKILL.md 纪律5 重写——每环节输出固定四要素（标题头/执行结果/关键数据/建议处理引导行），四环节输出完毕后必须输出【执行主干全部完成】汇总块（失败时输出【执行主干中断】），禁止环节4 后直接结束；② SKILL.md 纪律1 判定字段补全（环节1=status、环节2/3=pass、环节4=test_passed/测点 resultCode/场景 failTestCaseCount）并追加"禁止输出非业务自造名词"兜底；③ SKILL.md 纪律6 追加"禁止输出非模板表述"兜底禁令；④ flow-B 环节1/2/3 输出模板补"建议处理"引导行（固定输出，串行连续执行时用户无需操作），文末汇总块补注"必须输出"约束，对齐本文件 3.4.7 模板② |
| V2.6 | 2026-09-14 | **上线脚本真实落盘下载（工具7 出参强化 + 新增工具7A + 脚本模板化）**：① 工具7 出参 `script_url` 由相对路径改为**绝对 URL**——后端控制器按 X-Forwarded-Proto/X-Forwarded-Host/Host 头解析网关前置地址后拼装，头缺失退化为相对路径（脚本层补 BASE_URL 前缀）；幂等重放时按本次请求头重写 script_url 保证链接始终可用；② 脚本层新增 `download_launch_script` 子命令（工具7A，本地代码节点）——GET 下载路由响应体原样写本地文件（默认 `./launch_<offer_id>.sql`），出参 resultCode/saved_path/file_size；③ 程序B 环节1 落地后自动下载，输出模板新增"脚本文件已下载：{{saved_path}}（{{file_size}} 字节）"行（下载失败省略该行不中断主干）；④ 上线脚本改为**模板化生成**——SQL 骨架抽为 classpath 资源 `appstore/launch_script.sql.tpl`（`${xxx}` 占位符），buildLaunchScript 仅做需求产品信息替换（offer_name/月费/资源量/套外资费/商品编码等 16 变量），脚本结构维护只改模板不动 Java；⑤ 1.3 映射表/2.x 工具7/3.5 环节1 同步 |
| V2.5 | 2026-09-14 | **资费校准 8 项比对明细出参化（工具8 扩展）**：① billing_verify 出参新增 `compare_list[]`——8 项（套餐月租/流量/语音/短信赠送量/三项套外资费/商品有效期），requirement_desc 取落地配置 plan_json 字段原文、billing_desc 系统侧含折算括注（"首月按天折算"/"按天折算"/"自动续展"）、result 两态；② 环节3 输出模板改为逐行引用 compare_list（禁止模板自行拼装折算括注），表行数=出参长度；③ 1.3 映射表/3.4.7 模板②同步；④ 后端 buildFeeCompareList 实现（种子销售品资费规则 + 过渡期资费推导折算括注） |
| V2.4 | 2026-09-14 | **受理验证归并为自动测试子集（去独立环节5）**：① 执行主干由"5 环节"改回"四环节"（智能配置→稽核→资费校准→自动测试），受理验证=环节4 测试报告内子集小节（数据源不变：orderId/offerInstId+逐受理场景，不新增接口、不设触发词）；② 1.3 映射总表/3.1 程序B 表/3.3 调度时序/3.4.5 测试报告提示词/3.4.7 模板①②③ 全量同步四环节口径；③ flow-C 上线校验看板"受理验证"行并入"自动测试（含受理验证）"；④ SKILL.md description/路由表/纪律/开场白同步（"受理验证"用户单独询问时回放环节4 小节）；⑤ 工具7 出参 script_url（V2.3）在 1.3 映射表同步展示 |
| V2.3 | 2026-09-14 | **配置上线脚本下载链接（工具7 扩展）**：① 工具7 出参新增 `script_url`（相对路径 `/api/v1/appstore/product/config/script?offer_id={offer_id}`），落地成功时后端按落地配置自动生成 CRM/billing 落库 SQL 脚本（模拟，两段式 `/*run@crm*/`+`/*run@billing*/`）并存入脚本档案；② 新增附带下载路由 GET `/api/v1/appstore/product/config/script`（text/plain，附件名 launch_{offer_id}.sql，未落地 404）；③ 程序B 环节1 输出模板新增"配置上线脚本下载链接"行（script_url 逐字引用出参，缺失省略本行） |
| V2.2 | 2026-09-14 | **字段体系全量重构同步（对齐主方案 V2.9，3 模块/9 分类/24 字段）**：① 1.3 映射总表与 3.1 映射索引同步 24 字段口径（"18 字段要素"→"24 字段要素"、"四类18字段数组"→"3 模块/9 分类 24 字段数组"、"套餐固定费"→"套餐档位"、待补充唯一项=套餐档位、产品编码默认"系统待生成"）；② 来源两态归一——"AI推理"→"AI补全"，来源枚举 {原始需求, AI补全}（原"本体推理"并入 AI补全，6.3 枚举约定同步）；③ 触发词对齐——"确认执行"→"确认配置"（3.0/3.1/3.3/3.4/3.5/E3 等同步）、"发起审批"→"上线审批"、新增"确认上线"（flow-D 支线D-3 监控运维方案，审批通过后触发）；④ 3.0/3.1 程序B 四环节→5 环节（新增受理验证，复用测试结果不新增接口）、程序C 输出改上线校验看板（5 项 ✅ 表）；⑤ 3.4 待补充判定改套餐档位唯一、提示词模板同步五列模块表格（模块/分类/字段名称/字段值/备注）与"建议处理"引导话术；⑥ 3.5 用例 #3/#4/#4b 口径同步（套餐固定费→套餐档位）；⑦ 第 2 章工具契约原样保留（接口契约零改动，仅出参 offerInfo.fields 说明同步 24 字段） |
| V2.1 | 2026-09-14 | **全部接口去除 contractRoot 包裹，统一裸报文请求**（基于 599 元 5G 套餐实测反馈，接口路径/入出参契约零改动）：① 2.0.2 节整体改写为"请求报文约定"——tcpCont 报文头拼装表与 contractRoot 包裹结构删除，请求体直接为业务参数 JSON（置于顶层），变更原因与出参侧 `_unwrap` 兼容说明见节内注记；② 2.1/2.3 各工具"接口"行同步（contractRoot 报文/requestObject 报文 → 裸报文）；③ 2.4 自测项 #8 改为"裸报文契约"（mock 回显断言）、#13 本地自测 7→8 项、#14 为 ontology 空返回防护；④ 2.6 映射表 similar_offer/spec_audit/ontology_reason 备注同步；⑤ 附录 A 核对项同步；⑥ 工具2 spec_audit 文件传参缺陷修复（`--config-json-file` 未走 `_read_arg` 导致 PARAM_MISSING） |
| V2.0 | 2026-09-14 | **整体实现方式从"九思平台工作流"切换为"Skills 技能包"**（对齐主方案 V2.6/Skills技能包实现方案 V1.3，业务口径与工具契约零改动）：① 1.2 总体装配视图改写为技能包结构（SKILL.md 常驻总调度 + scripts/cpcp_api.py 17 子命令 + references/ 4 份 flow 流程文档 + K1~K5 知识目录）；② 1.3 映射总表"子工作流"列改为"承载程序"（程序 A~D，wf_sub_01~08 与 A~D 的映射见技能包方案第 5 章）；③ 第 3 章改题为"Skills 流程文档细化设计"：3.0 工作流清单改为 4 份 flow 文档清单与加载机制（原 3.1 主工作流归档配置、3.2 子工作流逐节点配置不再实施，逐节点口径已完整迁移至 flow 文档——步骤编号与原节点编号对齐；本文档 3.1 保留映射索引表供验收对照）；④ 3.3 调度时序重写为"SKILL.md 意图路由→按需加载 flow→脚本执行"（意图 6 类收敛为 5 类路由，两次中断语义不变）；⑤ 3.4 关键算法口径不变（req_id/取值链/门禁/轮询/提示词模板），承载方式改述为脚本子命令（build_plan/extract_record/poll_test_progress.py 等）；⑥ 3.5 自测用例 21 条保留，表述改脚本/程序口径；⑦ 第 4 章知识库"平台挂载点"改为"flow 文档读取指令"；⑧ 第 5 章智能体装配清单改为技能包部署清单；⑨ 第 6/7 章与附录 A~D 同步改为 skill 口径（wf_sub_XX→程序 A/B/C/D）；⑩ 第 2 章 14 工具契约**原样保留**（业务口径权威），仅"调用方/归纳设置"微调为脚本调用方口径，并新增 2.6 节"工具→脚本子命令映射" |

## 文档定位与阅读指引
| 章节 | 内容 | 面向读者 |
| --- | --- | --- |
| 第 1 章 | 与主方案的对应关系、工作流总体装配视图、环节→子工作流映射总表 | 全体 |
| 第 2 章 | **插件/代码节点细化设计**（工具契约权威，业务口径零改动）：每个工具的完整配置项（接口/入出参/为空提示/是否提参/出参归纳/超时/错误处理/裸报文请求约定）+ 2.6 工具→代码节点/后端端点映射 + V2.0 新增 7 个适配端点 | 后端开发人员 |
| 第 3 章 | **工作流配置细化设计**：11 个子工作流 JSON 清单与装配机制（3.0）、入口/环节→子工作流映射索引（3.1）、调度时序（3.3）、关键算法与提示词模板（3.4，代码节点体现实现）、自测用例（3.5） | 工作流配置/维护人员 |
| 第 4 章 | **知识库细化设计**：5 个知识分类的文档清单、切片策略、命名规范、召回参数、维护机制（承载方式=`knowledge/` 目录，随包迁移） | 知识运营人员 |
| 第 5 章 | 工作流配置部署清单（逐项核对） | 部署人员 |
| 第 6 章 | 变量命名约定 | 全体 |
| 第 7 章 | 异常处理矩阵 | 全体 |
| 附录 | 实施核对清单 | 项目管理 |

> 约定：本文档与主方案、《工作流配置重塑改造清单》保持术语一致。文中"子工作流"指 11 个子工作流 JSON（`wf_sub_00~10`），按环节承载业务；"代码节点"指内嵌于子工作流的类型6（CODE_*）确定性逻辑节点；`req_id`（统一键）指各环节结果在节点结果存储中的 key；`globalId` 指测试流水号。

---

## 1. 总览

### 1.1 与主方案的对应关系
| 主方案章节 | 本细化文档对应章节 | 细化内容 |
| --- | --- | --- |
| 2.1 原子能力→工具清单 | 第 2 章 | 每个工具补齐：为空提示、是否提参、出参归纳、超时、错误处理、裸报文请求约定、调用方（代码节点/后端端点） |
| 3 智能体设计 | 第 5 章 | 落地到工作流配置的部署清单（逐项勾选） |
| 4 插件设计 | 第 2 章 | 同上（含 2.6 工具→代码节点/后端端点映射） |
| 5 知识库设计 | 第 4 章 | 文档清单、切片大小/重叠、命名规范、召回参数、更新机制 |
| 6 工作流设计 | 第 3 章 | 11 个子工作流 JSON 的分步细化：节点=代码节点（CODE_*）与插件端点、判定分支、提示词模板、关键算法、装配机制（3.0/3.3）；生成器 `gen_workflows_v2.py` |
| 8 测试与验收 | 附录 | 实施核对清单（含工具级/子工作流级自测项） |

### 1.2 总体装配视图（一图看全，V2.0 工作流重塑版）
```
工作流配置（数字员工统一入口，V2.0 重塑版·11 个子工作流 JSON + 智能体语义识别直调）
  ├── 智能体（LLM，统一入口）：按 3.2 提示词【意图→工作流映射表】语义识别用户意图后直调对应子工作流
  │     （确认/调度纪律、串行不跳步、req_id 沿用、超范围拒答等由提示词【调度纪律】与【限制】约束；
  │     四环节门禁/req_id 校验等后端硬校验仍保留）
  ├── 子工作流（按环节承载，场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/）：
  │     wf_sub_00 需求提报（含需求工单审批 requirement 轨）→ wf_sub_01 需求分析（模板轨）
  │     → wf_sub_02 智能配置 → wf_sub_03 规格稽核 → wf_sub_05 资费校准 → wf_sub_04 自动测试
  │     → wf_sub_06 上线审批（双轨+自动上线）→ wf_sub_07 监控运维（根因+工单闭环）
  │     → wf_sub_08 审批进度查询 → wf_sub_09 存量产品查询（只读）→ wf_sub_10 存量合规扫描
  ├── 代码节点（类型6，确定性逻辑内嵌，替代原技能包脚本子命令）：
  │     CODE_RENDER_REQ / CODE_GET_TEMPLATE / CODE_VALIDATE_ELEMENTS / CODE_MERGE_NESTED /
  │     CODE_OP_VALIDATE_NESTED / CODE_RENDER_TABLE / CODE_EXTRACT_RECORD / CODE_POLL_PROGRESS /
  │     CODE_MAP_FIXED_CASES / CODE_OP_ROOT_CAUSE / CODE_OP_CREATE_WO /
  │     CODE_OP_SHELF_COMPLIANCE / CODE_SUMMARY_APPROVAL 等（由 gen_workflows_v2.py 生成）
  ├── knowledge/（随包迁移：K1规范/K2资费/K3测试/K4存量/K5FAQ/K5存量报文 + ontology-fields +
  │     seed_offer_groups + 存量产品目录_清洗后；K4 按销售品 ID 单文件精确定位）
  ├── 后端服务（AppStoreV16Controller /api/v1/appstore/*）：13 工具模拟实现（18 销售品种子）
  │     + 7 个新增适配端点（ops/root-cause、ops/work-orders、shelf-compliance、validate-nested、
  │     explain、report/download、script/download）+ NodeResultService（pd_ai_node_results 持久化）
  │     + 后端硬校验（req_id 格式 5002/同键重写覆盖/四环节门禁/幂等；确认门禁已移除）
  └── 模型纪律：温度 0.2（严谨输出）、出参逐字引用不加工、仅依据出参字段判定成败
```

### 1.3 环节 → 子工作流 → 代码节点/插件 → 知识库 映射总表（V2.0 工作流重塑版）
| 业务环节 | 承载子工作流 | 调用代码节点/插件（后端端点） | 知识库依赖 | 关键输出 |
| --- | --- | --- | --- | --- |
| 需求提报 | wf_sub_00（需求提报） | 需求字段抽取 LLM → `CODE_RENDER_REQ`（渲染需求提报单+req_id 生成）→ save_node_result → submit_release_approval（approval-type=requirement 需求工单审批轨） | 产销品业务规范（K1，引导话术） | requirement_report / req_id / 需求审批单号 |
| 需求分析 | wf_sub_01（模板轨） | query_node_result → `CODE_EXTRACT_RECORD` → 产品识别 LLM → query_similar_offer → `CODE_GET_TEMPLATE` → 要素提取 LLM → `CODE_VALIDATE_ELEMENTS`（节点108 质量闸）→ `CODE_MERGE_NESTED`（节点109）→ `CODE_OP_VALIDATE_NESTED`（节点110 本体校验闸+explain）→ `CODE_RENDER_TABLE`（节点111）→ save_node_result | 业务规范（K1）+ **存量销售品资料库（K4 单文件）** + templates 模板注册表 | req_id / 方案表格 / pending_required / 本体校验结论 |
| 用户确认 | 智能体（LLM）按 3.2 提示词【意图→工作流映射表】识别确认语义（V2.2 起后端无确认门禁）后直调 wf_sub_02 | — | — | — |
| **执行主干自动化串行**（智能配置→规格稽核→资费校准→自动测试→受理验证，一次跑完，仅异常中断） | wf_sub_02→wf_sub_03→wf_sub_05→wf_sub_04 | 见下列各环节行 | 各环节对应知识库 | 每环节结果打印；异常中断并引导重新执行/修改执行方案 |
| 智能配置 | wf_sub_02（智能配置） | query_node_result → `CODE_EXTRACT_RECORD` → save_product_config → `CODE_FUSION_GROUP_ECHO` → save_node_result | — | offer_id / save_result / script_url（绝对 URL，后端 script/download 回退） |
| 配置规格稽核（实时） | wf_sub_03（规格稽核） | query_node_result → `CODE_EXTRACT_RECORD` → realtime_spec_audit（工具2）→ save_node_result | 业务规范（K1，稽核标准参照） | pass / error_list / audit_summary |
| 资费校准 | wf_sub_05（资费校准） | query_node_result → `CODE_EXTRACT_RECORD` → check_billing_rule（工具8）→ save_node_result | **资费规则库（K2）** | pass / risk_list / compare_list（8 项比对明细） |
| 自动测试（含受理验证独立成节） | wf_sub_04（自动测试） | offer_test → get_test_scenes → `CODE_POLL_PROGRESS`（轮询）→ get_test_result → `CODE_MAP_FIXED_CASES`（节点315：31 条固定用例 / ACC|BILL|CUST / P0|P1|P2 / 九章节正式版报告）→ `CODE_DOWNLOAD_TEST_REPORT`（/report/download）→ save_node_result | **测试规范库（K3，V2.0 九章节模板）** + 存量销售品资料库（K4 预期值核对） | globalId / 《销售品自动化测试报告》正式版（受理验证独立成节：orderId/offerInstId + 逐受理场景结论） |
| 成功结果详情与审批确认 | wf_sub_02→05→04 串行结束后衔接 wf_sub_06 | 无（汇总引用各环节输出） | — | 上线校验看板 / 用户确认后发起审批 |
| 上线审批 | wf_sub_06（双轨+自动上线） | query_node_result×5（串行自查）→ `CODE_SUMMARY_APPROVAL` → LLM 报告 → submit_release_approval（approval-type=launch）→ `CODE_APPROVAL_POLL`（审批轮询）→ 通过后自动生成监控运维方案+xsbot-panel 看板 + `CODE_DOWNLOAD_LAUNCH_SCRIPT`（/script/download） | — | approval_id / status / 《上线审批建议》报告 |
| **审批进度查询** | wf_sub_08（审批进度查询） | query_approval_status（双轨按 approval_type 区分回显） | — | 审批状态 / 当前环节 / 意见 |
| **监控结果查询与告警** | wf_sub_07（监控运维） | query_product_monitor → send_alert → `CODE_OP_ROOT_CAUSE`（/ops/root-cause 根因推理）→ `CODE_OP_CREATE_WO`（/ops/work-orders 建工单闭环） | FAQ（K5 运维问答） | 指标 / 告警记录 / 根因 / 工单闭环 |
| **存量产品查询** | wf_sub_09（存量产品查询，只读） | `CODE_OP_QUERY_OFFER`（内嵌 knowledge/存量产品目录_清洗后.json） | K4 存量 | 结构化回显 / 多命中收敛 |
| **存量合规扫描** | wf_sub_10（存量合规扫描，可选） | `CODE_OP_SHELF_COMPLIANCE`（POST /shelf-compliance，R-C* 批量合规） | K1 业务规范 | R-C* 合规结论 + 整改引导 |

---

## 2. 插件/代码节点细化设计（工具契约权威，业务口径零改动）

> **V2.0 承载方式说明**：本章 13 个工具的接口/入出参/错误处理契约**原样保留**（后端模拟服务按此实现，工作流插件节点按此调用）。确定性逻辑由内嵌**类型6 代码节点（CODE_*）**承接（见 2.6 节映射与 3.1 节节点索引），插件能力由后端 `AppStoreV16Controller`（`/api/v1/appstore/*`）提供，子工作流节点直接调用，不再经技能包脚本命令行中转。

### 2.0 插件集公共约定（所有工具共用）

#### 2.0.1 插件集基本信息
| 项 | 值 |
| --- | --- |
| 插件名称 | 产销品加载插件集（V1.6：13 个工具全部自研实现，模拟结果输出；V2.0 起由子工作流插件节点调用后端 `/api/v1/appstore/*`，确定性逻辑由内嵌代码节点承载） |
| 插件描述 | 封装产销品加载全流程所需的能力：相似度分析、实时规格稽核、自动化测试（发起/场景/进度/结果）、配置落地、计费校验、上线审批、监控告警、存量合规；V1.6 起不再对接外部 ApiID，模拟结果须兼容《产品信息.txt》全部 18 个销售品（见 2.5） |
| 接口协议 | http/https（网关 `BASE_URL=http://10.86.13.201:31281`，代理 `/api/v1/appstore/*` 到后端） |
| 鉴权方式 | 自研服务自行实现（token/签名由封装层处理，工作流与模型不感知）；契约定义仍参考《产销品场景部分能力接口清单.xlsx》，便于后续替换真实实现 |

#### 2.0.2 请求报文约定（V2.7 起：全部接口裸报文，contractRoot 包裹移除）

**请求体直接为业务参数 JSON**（业务参数置于顶层），不再使用 contractRoot/tcpCont 包裹：

```json
{ "...": "业务参数，如 {\"businessDesc\": \"5G-A 单品套餐 月费199元\"}" }
```

> V2.7 变更说明（基于 599 元 5G 套餐实测反馈）：原 2.0.2 节 tcpCont 报文头拼装（transactionId/reqTime/globalId/version/sign/svcCode/appKey/dstSysId）与 contractRoot 包裹结构**整体移除**——实测后端 mock 只解析顶层业务字段，包裹后业务参数埋于 `svcCont.requestObject` 内不可见，返回 PARAM_MISSING/5001；相似产品查询以裸报文直连后端验证命中成功（900113045 + 完整 offerInfo）。出参侧 `_unwrap` 兼容解包保留（后端若返回 contractRoot/resultObject 包裹格式仍自动归一）。接口路径/入出参契约零改动。

#### 2.0.3 通用超时与重试
| 项 | 同步类工具 | 异步轮询类工具 |
| --- | --- | --- |
| 单次请求超时 | 60s（`realtime_spec_audit` 建议 60s，超时重试 1 次） | 30s |
| 重试 | 网络类错误重试 1 次（`save_product_config` 除外：写操作不自动重试，防重复写入） | 由 wf_sub_04 内嵌 `CODE_POLL_PROGRESS` 代码节点控制（见 3.4.4） |
| 超时后行为 | 返回 `resultCode=TIMEOUT`，程序转人工提示并保留请求报文供人工重放 | 程序按超时上限（30 分钟）终止轮询 |

#### 2.0.4 通用错误处理
| 错误类型 | 判定条件 | 封装层/后端处理 | 返回给子工作流 |
| --- | --- | --- | --- |
| 网络异常 | 连接失败/超时 | 重试 1 次后放弃 | `resultCode="NET_ERROR"`，`resultMsg=异常描述` |
| 网关错误 | HTTP 非 200 | 直接返回 | `resultCode="HTTP_"+状态码` |
| 业务失败 | `resultCode="1"` | 原样返回 | 透传 resultCode/resultMsg |
| 参数缺失 | 必填入参为空 | 拦截，不发起请求 | `resultCode="PARAM_MISSING"`，`resultMsg=缺失参数名` |
| 出参解析失败 | 报文结构不符合接口定义 | 记录原始报文 | `resultCode="PARSE_ERROR"` + 原始报文片段 |

#### 2.0.5 出参归纳设置口径（V2.0 调整：插件出参 JSON 原样透出，归纳由子工作流内 LLM 节点按模板完成）
- 查询类工具（工具2/4/5/6/10）：插件出参 JSON 原样透出，由子工作流内 LLM 节点按模板归纳提炼；
- 执行类工具（工具1/3/7/8/9/11）：插件出参 JSON 原样透出，子工作流结束节点**逐字引用**拼装结果打印，避免二次加工；
- 节点结果存储查询（save/query_node_result 插件）：JSON 原样输出，`CODE_EXTRACT_RECORD` 代码节点可提取 `list[0].result_json` 原文。

#### 2.0.6 入参"为空提示"与"是否提参"通用口径（V2.0：参数缺失由插件/结束节点判定）
- **是否提参**：等价于"参数来源"——由模型从上下文/用户输入提取的参数直接作为插件节点入参传入；须取自上一环节结果的大报文（如 `config_json`/`plan_json`），一律经自查链路 `query_node_result` + `CODE_EXTRACT_RECORD` 取 `result_json` 原文后作为入参引用，不经模型上下文中转。
- **为空提示**：每个必填参数必须填写，格式：`缺少{参数中文名}，请{获取方式}`（示例见各工具定义）；插件/结束节点对必填参数缺失报 `PARAM_MISSING`，子工作流按异常矩阵（第 7 章）处置。

---

### 2.1 自研能力接口工具定义（工具1~6，V1.6 全部自研模拟实现）

#### 工具1：相似度分析 `query_similar_offer`
| 项 | 配置 |
| --- | --- |
| 实现方式 | **自研模拟实现（V1.6）**：基于《产品信息.txt》18 个销售品构建相似度匹配模拟服务，返回结构化模拟结果；契约仍参考《产销品场景部分能力接口清单.xlsx》行2 定义，便于后续替换真实实现 |
| 接口 | POST（V2.7 起裸报文，业务参数置于顶层，见 2.0.2） |
| 工具描述 | 通过业务需求描述查询相似销售品，返回相似产品列表及相似度评分。用于需求分析环节匹配历史产品，支撑 AI补全 |
| 调用方 | wf_sub_01 需求分析（节点105 相似产品检索：要素摘要 need_summary 之后、接口返回 offerInfo 供 merge_nested 补全） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `businessDesc` | string | 是 | 是 | 缺少业务需求描述，请提供需求原文或需求文档摘要 | 业务需求描述文本，≤5000字符，超出由工作流节点先做摘要压缩 |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `resultCode` | string | 0 成功 / 1 失败 |
| `resultMsg` | string | 处理结果描述 |
| `similarOffer` | object | 相似度最高的产品（仅返回1个），未命中时为空对象 |
| `similarOffer.similarOfferId` | string | 相似销售品 ID（如 900102308） |
| `similarOffer.similarOfferName` | string | 相似销售品名称 |
| `similarOffer.similarityScore` | string | 相似度评分（0~1） |
| `similarOffer.similarityDesc` | string | 相似原因描述 |
| `similarOffer.offerInfo` | object | **完整产品配置信息（与需求要素同构）**：similarOfferId/similarOfferName/series/sub_type + fields 3 模块/9 分类 24 字段数组（field/category/value，字段名与本体注册表一致），后端 toFields24 转换 |

| 归纳 | 否（由 wf_sub_01 大模型节点消费，作为 AI补全依据） |
| --- | --- |
| 超时/重试 | 60s / 重试 1 次 |
| 错误处理 | resultCode=1 时，wf_sub_01 继续走"无相似产品"分支（仅用知识库存量资料补全），不中断流程 |

#### 工具2：实时规格稽核 `realtime_spec_audit`
| 项 | 配置 |
| --- | --- |
| 实现方式 | **自研模拟实现（V1.6）**：解析配置 JSON 与《产品信息.txt》规则库比对，同步返回模拟稽核结果；契约仍参考《产销品场景部分能力接口清单.xlsx》定义 |
| 接口 | POST（V2.7 起裸报文，业务参数置于顶层，见 2.0.2；原 tcpCont svcCode/appKey/dstSysId 已随包裹移除） |
| 工具描述 | 配置落地后按销售品/配置内容实时发起稽核，同步返回稽核结果（通过/驳回 + 问题明细 + 整改建议） |
| 调用方 | wf_sub_03 规格稽核（自查 config + `CODE_EXTRACT_RECORD` 取 offer_id/config_json 后调用；由智能体 LLM 确认语义识别后直调触发） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `offer_id` | string | 是 | 是 | 缺少销售品ID，请先完成配置落地 | 配置落地返回的销售品 ID（save_product_config 出参 offer_id） |
| `config_json` | string | 是 | 否（工作流变量引用） | 缺少落地配置JSON，请先完成配置落地 | 落地配置 JSON 原文（来自执行方案 JSON 落地后的配置快照） |
| `audit_scene` | string | 否 | 是 | 缺少稽核场景时默认 all | 稽核场景枚举：spec/fee/all，默认 all |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `pass` | int | 1 通过 / 0 不通过 |
| `error_list` | array | 问题明细 |
| `error_list[].item` | string | 问题项（对应配置字段/规则） |
| `error_list[].level` | string | 严重级别（error/warning） |
| `error_list[].desc` | string | 问题描述 |
| `error_list[].suggest` | string | 整改建议 |
| `audit_summary` | string | 稽核总结（一句话） |
| `resultCode` | string | 0 成功 / 1 失败 / NET_ERROR / TIMEOUT |

| 归纳 | 是（LLM 提炼"通过/驳回 + 整改建议"） |
| --- | --- |
| 超时/重试 | 60s / 重试 1 次；超时转人工提示并保留请求报文 |
| 错误处理 | pass=0 时 LLM 中断调度走异常引导（可选 send_alert + 终止返回建议）；resultCode 非 0 时提示"稽核接口异常，请稍后重试或人工稽核" |

#### 工具3：销售品测试发起 `offer_test`
| 项 | 配置 |
| --- | --- |
| 实现方式 | **自研模拟实现（V1.6）**：按销售品 ID 匹配《产品信息.txt》种子数据，异步模拟测试执行，生成并返回 `50` 开头的模拟 globalId；契约仍参考接口清单行7 |
| 接口 | POST（V2.7 起裸报文，业务参数置于顶层，见 2.0.2） |
| 工具描述 | 发起动作：按销售品 ID 发起自动化测试（异步执行，测试平台自动完成受理类场景执行与受理验证），返回测试流水 globalId |
| 调用方 | wf_sub_04 自动测试（节点302 发起测试，自查 config 取 offer_id 后调用） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `offerId` | string | 是 | 是 | 缺少销售品ID，请提供被测销售品ID | offer 表主键，如 `10090` |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `resultCode` | string | 0 处理成功 / 1 处理失败 |
| `resultMsg` | string | 处理结果描述 |
| `globalId` | string | 测试流水号，格式 `50 + yyyyMMddHHmmss + 10位随机数`（如 50202608252017364447983718），后续三个查询接口必传 |

| 归纳 | 否 |
| --- | --- |
| 超时/重试 | 30s / 重试 1 次（发起动作需防重复发起，重试前检查上次发起是否已成功） |
| 错误处理 | resultCode=1 时终止 wf_sub_04 并返回 resultMsg；globalId 为空视为失败 |

#### 工具4：查询测试场景 `get_test_scenes`
| 项 | 配置 |
| --- | --- |
| 实现方式 | **自研模拟实现（V1.6）**：按 globalId 返回该销售品在《产品信息.txt》规则推导的模拟测试场景集合（套餐新装/副卡加装/套餐退订）；契约仍参考接口清单行8 |
| 接口 | POST（V2.7 起裸报文，业务参数置于顶层，见 2.0.2） |
| 工具描述 | 测试发起后查询本次测试匹配的测试场景集合（即受理验证覆盖范围，如 套餐新装（C网）/副卡加装/套餐退订（C网）） |
| 调用方 | wf_sub_04 自动测试（节点303 查询场景，offer_test 之后、轮询之前） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `globalId` | string | 是 | 否（工作流变量引用 offer_test 出参） | 缺少测试流水号，请先发起测试 | 取 offer_test 返回的 resultObject.globalId |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `resultCode` | string | 0 成功 / 1 失败 |
| `testScenes` | array | 测试场景列表 |
| `testScenes[].testSceneId` | string | 场景 ID |
| `testScenes[].testSceneName` | string | 场景名称 |
| `testScenes[].testSceneNbr` | string | 场景编码：S_O_TC 套餐新装（C网）/ S_ADD_CARD 副卡加装 / S_U_TC 套餐退订（C网） |
| `testScenes[].testSceneDesc` | string | 场景描述 |
| `testScenes[].sort` | int | 排序 |

| 归纳 | 是 |
| --- | --- |
| 超时/重试 | 30s / 重试 1 次 |
| 错误处理 | 为空场景列表（testScenes=[]）时终止并提示"该销售品未匹配到测试场景，请检查销售品配置" |

#### 工具5：查询测试进度 `get_test_progress`
| 项 | 配置 |
| --- | --- |
| 实现方式 | **自研模拟实现（V1.6）**：按 globalId 推进模拟测试进度状态机（场景数+2 步骤模型），支持轮询返回 done/failed；契约仍参考接口清单行9 |
| 接口 | POST（V2.7 起裸报文，业务参数置于顶层，见 2.0.2） |
| 工具描述 | 轮询测试任务当前步骤、是否完成、是否失败 |
| 调用方 | wf_sub_04 内嵌 `CODE_POLL_PROGRESS` 代码节点（节点304 轮询，间隔 5s，超时 30 分钟）进程 |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `globalId` | string | 是 | 否（工作流变量引用） | 缺少测试流水号，请先发起测试 | 同工具4 |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `totalSteps` | int | 总步骤数 = 场景数 + 2（前 2 步固定为 智能匹配测试场景&用例、智能匹配测试资源） |
| `activeIndex` | int | 当前步骤下标（从 0 计） |
| `done` | bool | 测试是否全部完成 |
| `failed` | bool | 是否存在失败/中止（RESULT_CODE='1'/'2'）场景 |
| `failIndex` | int | 第一个失败场景步骤下标，无失败为 -1 |
| `totalSceneCount` | int | 场景总数 |
| `finishedSceneCount` | int | 已完成场景数 |
| `failedSceneCount` | int | 失败（含中止）场景数 |

| 归纳 | 否 |
| --- | --- |
| 超时/重试 | 30s / 不重试（由 wf_sub_04 内嵌 `CODE_POLL_PROGRESS` 轮询控制） |
| 错误处理 | 单次查询失败不终止，下一轮重查；连续 5 次失败终止循环转人工 |

#### 工具6：查询测试结果 `get_test_result`
| 项 | 配置 |
| --- | --- |
| 实现方式 | **自研模拟实现（V1.6）**：按 globalId 生成逐测点比对明细，**presetValue 取自该销售品在《产品信息.txt》中的规则值**，testValue 模拟生成（默认与 presetValue 一致，可构造不一致用例），受理凭证 orderId/offerInstId 模拟生成；契约仍参考接口清单行10 |
| 接口 | POST（V2.7 起裸报文，业务参数置于顶层，见 2.0.2） |
| 工具描述 | 测试全部完成后查询逐场景测试点比对明细与 AI 场景总结；orderId/offerInstId 为实际受理生成的订单号/销售品实例 ID，作为受理验证结论依据 |
| 调用方 | wf_sub_04 自动测试（节点305 查询结果，done=true 后调用一次） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `globalId` | string | 是 | 否（工作流变量引用） | 缺少测试流水号，请先发起测试 | 须在测试进度全部完成后查询，结果才有完整数据 |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `resultCode` | string | 0 成功 / 1 失败 |
| `resultMsg` | string | 处理结果描述 |
| `testRequestId` | string | 测试请求 ID（auto_test_request 表主键） |
| `testRequestName` | string | 测试名称（销售品系统名+_测试验证） |
| `offerName` | string | 被测销售品名称 |
| `orderId` | string | **实际受理生成的订单号（受理验证依据）** |
| `offerInstId` | string | **实际受理生成的销售品实例 ID（受理验证依据）** |
| `testScenes[]` | array | 逐场景结果 |
| `testScenes[].testSceneNbr/Name/Desc` | string | 场景编码/名称/描述 |
| `testScenes[].testCaseCount` | int | 测试点总数 |
| `testScenes[].successTestCaseCount` | int | 成功数 |
| `testScenes[].failTestCaseCount` | int | 失败数 |
| `testScenes[].testCasePointResults[]` | array | 测点明细 |
| `...testPointNbr` | string | 测点编码：P_EFF_DATE 生效时间 / P_EXP_DATE 失效时间 / P_STATUS 实例状态 / P_MAIN_PROD 主产品构成 / P_RELY_REL 依赖关系 / P_MUTEX_REL 互斥关系 / P_ORD_CNT 订购数量 / P_OFFER_NAME 名称 / P_OFFER_TYPE 类型 / P_PAY_MODE 付费方式 |
| `...presetValue` | string | 规格规定值（预期值） |
| `...testValue` | string | 实测值（CRM 实际生成结果） |
| `...resultCode` | string | 0 一致 / 1 不一致 |
| `...resultMsg` | string | 比对结论 |
| `testScenes[].objTestSceneRel` | object | AI 场景总结：resultMsg 场景测试总结 / summaryDesc 汇总描述 / suggestion 优化建议 |

| 归纳 | 是（工作流大模型节点汇总生成测试报告，报告必须包含受理验证结论） |
| --- | --- |
| 超时/重试 | 30s / 重试 1 次 |
| 错误处理 | orderId/offerInstId 为空时，测试报告中受理验证结论标记"未获取到受理凭证，需人工核实" |

---

### 2.2 节点结果存储查询（不自研，后端通用 API）：save_node_result / query_node_result（V1.6 更新）

| 项 | 配置 |
| --- | --- |
| 来源 | 后端通用 API，**不自研**（V1.3 起替代原自研 save_plan_json/get_plan_json）；V1.6 起后端由 `NodeResultService`（MyBatis-Plus）落库 `pd_ai_node_results` 表持久化（H2/MySQL 双 DDL），服务重启结果不丢失；V2.0 起由子工作流插件节点（save_node_result / query_node_result）直连后端调用 |
| 保存（save_node_result） | POST `/api/v1/appstore/result/save`，入参 req_id/node_name/result_json/status（默认 ok）；**V1.7 环节结果存储已下沉到各子工作流内部**：wf_sub_00(req_id=CODE_RENDER_REQ 生成键, node_name=requirement_report)、wf_sub_01(node_name=requirement)、wf_sub_02~05 各环节（req_id=入参统一键, node_name=config/spec/fee/test）、wf_sub_06 报告存储(node_name=report) |
| 查询（query_node_result） | **GET** `/api/v1/appstore/result/query`，入参 req_id（必填）/node_name（可选）/latest_only（默认1）；出参 code/msg/total/list（取 list[0].result_json 为结果原文）；**各子工作流开始后自查上游环节结果（可用 `CODE_EXTRACT_RECORD` 代码节点提取原文）** |
| key（req_id）规范 | 统一键：需求提报与执行主干共用单键 `PLAN` + yyyyMMddHHmmss + 3位随机数（如 `PLAN20260913143025087`，由 wf_sub_00 `CODE_RENDER_REQ` 以系统时钟生成、每次唯一，LLM 不参与生成，环节1 生成后 2~9 全程沿用）；同键**覆盖写**；后端硬校验 PLAN 格式（5002） |
| node_name 枚举 | requirement_report（V2.0 新增，需求工单）/ requirement / config / spec / fee / test / **report**（V1.6 新增） |
| value 大小 | result_json ≤64KB（超限返回 5004）；执行方案 JSON 一般 <20KB，超限时压缩仅保留 fields/similar_offers/pending_fields 三段 |
| 读写一致性自测 | 保存后立即按 req_id+node_name 查询，比对 result_json 一致；服务重启后可查询（持久化验证） |

---

### 2.3 自研能力接口工具定义（工具7~11、13，V1.6 统一自研+模拟结果输出）

#### 工具7：配置落地 `save_product_config`（V1.2 核心新增）
| 项 | 配置 |
| --- | --- |
| 接口 | POST `{BASE_URL}/api/v1/appstore/product/config/save`（`BASE_URL=http://10.86.13.201:31281`） |
| 实现方式 | **自研模拟实现（V1.6）**：写入模拟 CRM 销售品配置库（内存/存储模拟），生成 offer_id，模拟结果兼容 18 个销售品；**V2.2 确认门禁已移除**（详见下方） |
| 工具描述 | 读取执行方案 JSON，将基础信息/资源配置/营销资源/销售规则四类字段写入 CRM 销售品配置 |
| 调用方 | wf_sub_02 智能配置（节点103 配置落地；**唯一写入步骤，plan_json 原文原样透传，中间无任何模型改写**） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `req_id` | string | 是 | 是 | 缺少执行方案key，请先完成需求分析并确认执行方案（回复"确认配置"） | 执行方案存储 key（V1.7 统一键，方案key由后端从 plan_json 的 req_id 键提取） |
| `plan_json` | string | 是 | 否（工作流变量引用） | 缺少执行方案JSON，请先完成需求分析并确认 | **必须为节点结果存储查询插件取回的 JSON 原文，原样透传** |
| `operator` | string | 否 | 是 | 可为空 | 操作人（从会话上下文取） |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `offer_id` | string | CRM 产品 ID |
| `offer_id` | string | 销售品 ID（后续稽核/测试入参） |
| `save_result` | object | 各字段分类写入结果（基础信息/资源配置/营销资源/销售规则 各自 success/fail 及原因） |
| `status` | string | SUCCESS / PARTIAL / FAIL |
| `script_url` | string | V2.6 起为**绝对 URL**（后端按 X-Forwarded-Proto/Host 头解析网关前置地址后拼装，可直接下载；头缺失退化为相对路径 `/api/v1/appstore/product/config/script?offer_id={offer_id}`），环节1 输出模板引用 |

| 归纳 | 否 |
| --- | --- |
| 超时/重试 | 60s / **不自动重试**（写操作防重复写入；失败由用户重新触发） |
| **确认门禁（V2.2 移除）** | 原 V1.7 后端硬校验（confirmed==true + 存储中 req_id 的 CONFIRMED 标记，无标记返回 NOT_CONFIRMED）**已删除**——联调发现 LLM 跳步/漏写标记导致合法调用被误拒，确认与否改由外层智能体 LLM 语义识别保证；后端保留幂等（同 plan_json 重放返回原结果，并按本次请求头重写 script_url）与 plan_json 合法性校验；插件入参 confirmed 保留为兼容字段（后端仅记录不校验） |
| **上线脚本（V2.5 新增，V2.6 模板化+强化）** | 落地成功时按落地配置自动生成 CRM/billing 落库 SQL 脚本（模拟，两段式：`/*run@crm*/` 定价信息 PD_GOODSPRC_DICT/PD_GOODSCLASS_REL/PD_GOODSOPCODE_REL/PD_GOODSRELEASE_DICT + `/*run@billing*/` 优惠/累计 FAV_INDEX/CUMULATE_VALUE_CTRL/VOICEFAV_CFEE_PLAN/PRICING_COMBINE/REMIND_ITEM_PROPERTY/REMIND_GROUP_MEMBER），存入脚本档案；**V2.6 起脚本骨架抽为 classpath 模板 `appstore/launch_script.sql.tpl`**（`${xxx}` 占位符 16 变量：offer_name/offer_id/goods_id/prc_id/class_id/month_fee/flow/voice/sms/out_flow/out_voice/out_sms 等），生成时从落地配置+种子规则提取需求产品信息逐项替换——脚本表结构维护只改模板，不动 Java；附带下载路由 GET `/api/v1/appstore/product/config/script?offer_id={offer_id}`（text/plain，附件名 launch_{offer_id}.sql；未落地返回 404）；V2.6 起 `script_url` 为**绝对 URL**（后端按 X-Forwarded-Proto/Host 头解析网关前置地址后拼装，可直接下载；头缺失退化为相对路径，见 出参 script_url 行）；V2.0 起 wf_sub_06（节点621 `CODE_DOWNLOAD_LAUNCH_SCRIPT`）经 `POST /api/v1/appstore/script/download` 下载脚本，端点不可达回退下载引导文本（`backend_pending=1`、download_url 空） |
| 错误处理 | status=PARTIAL 时返回失败分类明细供用户修正；status=FAIL 终止 wf_sub_02 |

#### 工具8：计费规则校验 `check_billing_rule`
| 项 | 配置 |
| --- | --- |
| 接口 | POST `{BASE_URL}/api/v1/appstore/billing/rules/verify` |
| 实现方式 | **自研模拟实现（V1.6）**：按《产品信息.txt》该销售品的资费/叠加/互斥规则校验配置 JSON，输出模拟风险清单（默认通过，可构造冲突用例）；契约保持不变 |
| 工具描述 | 校验套餐计费逻辑、优惠叠加规则，输出资费风险清单 |
| 调用方 | wf_sub_05 资费校准（自查 config + `CODE_EXTRACT_RECORD` 取 config_json 后调用，节点404） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `config_json` | string | 是 | 否（工作流变量引用） | 缺少落地配置JSON，请先完成配置落地 | 落地配置 JSON |
| `check_scene` | string | 否 | 是 | 默认 all | 枚举：fee/overlay/superposition/all（计费/叠加/互斥叠加/全量） |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `pass` | int | 1 通过 / 0 不通过 |
| `risk_list` | array | risk_type 风险类型 / risk_desc 风险描述 / suggest 建议 |
| `compare_list` | array | V2.6 新增：8 项资费比对明细（环节3 比对表逐行引用）——project_name（套餐月租/流量赠送量/语音赠送量/短信赠送量/流量超出资费/语音超出资费/短信超出资费/商品有效期）、requirement_desc（需求侧值，取落地配置 plan_json 字段原文）、billing_desc（系统侧值，含折算括注如"29元（首月按天折算）"、"长期有效（自动续展）"）、result（一致/不一致） |

| 归纳 | 是 |
| --- | --- |
| 超时/重试 | 60s / 重试 1 次 |
| 错误处理 | 不通过走资费驳回分支（终止 + 返回风险清单）；接口异常提示重试或人工校准 |

#### 工具9：上线审批推送 `submit_release_approval`
| 项 | 配置 |
| --- | --- |
| 接口 | POST `{BASE_URL}/api/v1/appstore/approval/submit` |
| 实现方式 | **自研模拟实现（V1.6）**：生成模拟审批单号 approval_id 并写入模拟审批状态库（供工具13 查询），幂等规则保持不变；契约保持不变 |
| 工具描述 | 汇总测试与稽核报告，推送上线审批流 |
| 调用方 | wf_sub_06 上线审批（串行自查五类结果、`CODE_SUMMARY_APPROVAL` 合成汇总、LLM 生成报告后调用 submit_release_approval，approval-type=launch 轨） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `req_id` | string | 是 | 是 | 缺少执行方案key，请先完成执行主干 | 执行方案存储 key（V1.7 统一键，后端四环节硬校验依据） |
| `offer_id` | string | 是 | 是 | 缺少产品ID，请先完成配置落地 | CRM 产品 ID |
| `report_url` | string | 是 | 否（工作流变量引用报告全文） | 缺少上线报告，请先完成测试报告生成 | 报告内容或链接（大模型节点 report 输出） |
| `approval_flow` | string | 否 | 是 | 默认 standard | 枚举：standard/urgent |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `approval_id` | string | 审批单号 |
| `status` | string | 提交状态 |

| 归纳 | 否 |
| --- | --- |
| 超时/重试 | 30s / 重试 1 次（幂等：同 offer_id 重复提交返回原 approval_id） |
| **后端硬校验（V1.7）** | 后端校验 req_id 入参存在 + 逐一查询 `NodeResultService.latestRecord(req_id, "config"/"spec"/"fee"/"test")` 四条记录全部非空（V1.7 统一键，原 execution_id 参数合并为 req_id）；缺失任一环节拒绝推送，防止 LLM 跳步/绕过执行主干发起审批 |

#### 工具10：监控查询 `query_product_monitor`
| 项 | 配置 |
| --- | --- |
| 接口 | GET `{BASE_URL}/api/v1/appstore/product/monitor` |
| 实现方式 | **自研模拟实现（V1.6）**：按销售品返回模拟运行指标（订单量/异常量/计费差错率/告警列表），可构造 error_count>0 演示告警分支；契约保持不变 |
| 工具描述 | 查询上线后销售品运行指标（订单量、异常量、计费差错） |
| 调用方 | wf_sub_07 监控运维（节点701 监控查询；每日定时巡检或对话触发） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `offer_id` | string | 是 | 是 | 缺少产品ID，请提供要查询的销售品 | — |
| `date_range` | string | 否 | 是 | 默认最近1天 | 如 `2026-09-11~2026-09-12` |
| `metric` | string | 否 | 是 | 默认 all | 枚举：order/error/fee/all |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `order_count` | int | 订单量 |
| `error_count` | int | 异常量 |
| `fee_error_rate` | float | 计费差错率 |
| `alarm_list` | array | 已产生告警列表 |

| 归纳 | 是 |
| --- | --- |

#### 工具11：异常告警 `send_alert`
| 项 | 配置 |
| --- | --- |
| 接口 | POST `{BASE_URL}/api/v1/appstore/alert/send` |
| 实现方式 | **自研模拟实现（V1.6）**：生成模拟告警单号 alert_id 并返回推送成功状态，模拟结果输出；契约保持不变 |
| 工具描述 | 推送异常告警到运维群/工单 |
| 调用方 | wf_sub_07 监控运维（异常分支）、wf_sub_03 稽核驳回分支（可选联动） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `offer_id` | string | 是 | 是 | 缺少产品ID，请提供告警关联销售品 | — |
| `alarm_level` | string | 是 | 是 | 缺少告警级别 | 枚举：high/middle/low |
| `content` | string | 是 | 否（工作流变量引用，由大模型节点生成告警文案） | 缺少告警内容 | 告警正文（含环节、问题描述、建议） |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `alert_id` | string | 告警单号 |
| `status` | string | 推送状态 |

| 归纳 | 否 |
| --- | --- |

#### 工具13：审批进度查询 `query_approval_status`（V1.1/V1.5 新增）
| 项 | 配置 |
| --- | --- |
| 接口 | GET `{BASE_URL}/api/v1/appstore/approval/status` |
| 实现方式 | **自研模拟实现（V1.6）**：从模拟审批状态库（工具9 写入）查询并返回审批状态/当前环节/意见；契约保持不变 |
| 工具描述 | 按 approval_id 或 offer_id 查询上线审批单当前状态，支撑用户发送消息查询审批进度 |
| 调用方 | wf_sub_08 审批进度查询（节点801；智能体 LLM 按【意图→工作流映射表】识别查询意图后直调命中） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `approval_id` | string | 条件必填（与 offer_id 至少一个） | 是 | 请提供审批单号或销售品ID，以便查询审批进度 | 审批单号（submit_release_approval 出参），优先使用 |
| `offer_id` | string | 条件必填（与 approval_id 至少一个） | 是 | 请提供审批单号或销售品ID，以便查询审批进度 | 产品 ID，缺失 approval_id 时按其查最新审批单 |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `approval_id` | string | 审批单号 |
| `status` | string | 审批状态：审批中 / 通过 / 驳回 |
| `current_node` | string | 当前审批环节（如：产品经理审核 / 部门主管审批） |
| `approver` | string | 当前审批人 |
| `opinion` | string | 审批意见（最近一条） |
| `submit_time` | string | 提交时间 |
| `update_time` | string | 最近更新时间 |

| 归纳 | 是（大模型归纳为"审批单号+状态+当前环节+意见"摘要） |
| --- | --- |
| 超时/重试 | 30s / 重试 1 次 |
| 错误处理 | 查无审批单时提示"未找到该销售品的审批单，请确认是否已发起审批" |

### 2.4 工具级自测要点（对应主方案 8.1 分层测试第 1 层；V2.0 起经子工作流插件节点与后端端点逐项自测）
| # | 自测项 | 通过标准 |
| --- | --- | --- |
| 1 | 工具1 businessDesc 5000 字符上限 | 5000 字符正常返回；5001 字符被 wf_sub_01 内 LLM 节点先做摘要压缩后调用 |
| 2 | 工具2 同步返回 | 调用后 60s 内返回 pass/error_list；不产生任何文件/异步任务 |
| 3 | 工具3 globalId 格式 | `50` 开头 + 19 位（50 + yyyyMMddHHmmss + 10位随机数） |
| 4 | 工具5 轮询字段 | done/failed/failIndex 与场景状态映射（0 成功/1 失败/2 已中止/NULL 进行中）一致 |
| 5 | 工具6 受理凭证 | orderId/offerInstId 非空（测试环境有效销售品） |
| 6 | 工具7 门禁（V2.2 修订） | 确认门禁已移除——confirmed 任意值均可落地（入参仅记录）；同 plan_json 幂等不产生重复写入 |
| 7 | 工具7 幂等 | 同 plan_json 重复提交不产生重复销售品（或返回已存在 offer_id） |
| 8 | 裸报文契约（V2.7，替代原 tcpCont 拼装项） | 请求体捕获/自建 mock 回显验证 | 请求体为业务参数 JSON（顶层可直接读到 businessDesc 等字段），无 contractRoot/tcpCont 包裹；出参侧 contractRoot/resultObject 包裹返回仍能被 `_unwrap` 解出 |
| 9 | 节点结果存储读写一致 | 保存→按 req_id+node_name 查询 result_json 逐字节一致；覆盖写后查询为新值；服务重启后可查询（H2/MySQL 持久化） |
| 10 | 每个必填入参 PARAM_MISSING | 置空必填参数调用插件节点 | 插件/结束节点返回 PARAM_MISSING 与提示文案，子工作流按异常矩阵处置 |
| 11 | 模拟数据 18 套餐覆盖（V1.6） | 依次以 18 个销售品 ID 作为输入调用工具1/2/3/6/8/10 | 每个销售品均返回结构化模拟结果，资费规则值与《产品信息.txt》该销售品记录一致；无"写死单一套餐"回退 |
| 12 | 测试预期值口径（V1.6） | 抽取 900102308、900117022 两类套餐对比工具6 presetValue | presetValue 与《产品信息.txt》对应销售品规则值逐项一致 |
| 13 | 工作流全量校验（V2.0） | `gen_workflows_v2.py` 重建后执行 11 个子工作流 JSON 全量校验 | 11 个子工作流全部 ALL_OK、边/节点编号正确（含 wf_sub_01 节点108~113、wf_sub_04 节点315、wf_sub_06/07/09/10 关键代码节点） |
| 14 | 要素提取质量闸防护（V2.0，承接原 ontology_reason 空返回防护） | 模拟/构造 `CODE_VALIDATE_ELEMENTS`（wf_sub_01 节点108）输入：schema 与 elements 无法提取可提取必填项 | quality_gate=FAIL，missing_required 输出待补路径（E31），禁止直接进入 merge_nested/组装方案；`CODE_OP_VALIDATE_NESTED`（节点110）端点不可达回退 backend_pending=1、valid 为空 |

### 2.5 模拟结果兼容性要求（V1.6 新增，与主方案"模拟结果兼容性要求"对齐）
全部自研接口（工具1~11、13）的模拟输出统一遵守：
1. **种子数据**：以《产品信息.txt》全部 **18 个销售品**为种子数据（5G-A 系列 10 个 + 权益随心选系列 8 个），不写死单一套餐样例；
2. **可复现性**：任一销售品作为输入时，相似度分析、稽核、资费校验、测试（场景/进度/结果/受理验证）、监控等接口均能返回与该销售品资费规则一致的结构化模拟结果，保证端到端演示对任意套餐可复现；
3. **预期值口径**：测试结果中的预期值（presetValue）取自该销售品在《产品信息.txt》中的规则值；testValue 模拟生成（默认与 presetValue 一致，支持构造不一致用例验证异常分支）；
4. **契约不变**：接口路径/入参/出参契约仍参考《产销品场景部分能力接口清单.xlsx》设计，后续替换真实实现时**仅需将网关环境变量 `BASE_URL` 指向真实网关（默认 `http://10.86.13.201:31281`），子工作流与模型层零改动**。

### 2.6 工具 → 代码节点/后端端点映射（V2.0 重塑，对齐工作流配置与后端 AppStoreV16Controller）
插件能力由后端 `AppStoreV16Controller`（`/api/v1/appstore/*`）提供，子工作流插件节点直接调用（不再经技能包脚本 `cpcp_api.py` 命令行中转）；确定性逻辑由内嵌**类型6 代码节点（CODE_*）**承接。网关 `BASE_URL=http://10.86.13.201:31281`。

| 工具 | 承载代码节点 / 调用子工作流 | 方法/路径 | 备注 |
| --- | --- | --- | --- |
| 工具1 query_similar_offer | wf_sub_01（节点105） | POST /api/v1/appstore/similar/offer/query | V2.7 起裸报文（原 contractRoot 包裹已移除） |
| 工具2 realtime_spec_audit | wf_sub_03（自查 config + `CODE_EXTRACT_RECORD`） | POST /api/v1/appstore/audit/realtime | svcCode=5012010056/appKey=eOrder1/dstSysId=OrderCenter；同步重试 1 次 |
| 工具3 offer_test | wf_sub_04（节点302 发起） | POST /api/v1/appstore/test/offer/start | offerId 保持后端 camelCase 参数 |
| 工具4 get_test_scenes | wf_sub_04（节点303 查场景） | POST /api/v1/appstore/test/offer/scenes | — |
| 工具5 get_test_progress | wf_sub_04 `CODE_POLL_PROGRESS`（轮询） | POST /api/v1/appstore/test/offer/progress | 单次不重试，轮询由代码节点控制 |
| 工具6 get_test_result | wf_sub_04（节点305 查结果） | POST /api/v1/appstore/test/offer/result | done=true 后查询 |
| 工具7 save_product_config | wf_sub_02（自查 + `CODE_EXTRACT_RECORD`） | POST /api/v1/appstore/product/config/save | **不自动重试**防重复写入；确认门禁已按 V2.2 移除，confirmed 为兼容字段仅记录；script_url 绝对 URL（V2.6） |
| 工具8 check_billing_rule | wf_sub_05（自查 + `CODE_EXTRACT_RECORD`） | POST /api/v1/appstore/billing/rules/verify | — |
| 工具9 submit_release_approval | wf_sub_06（`CODE_SUMMARY_APPROVAL` 合成后调用） | POST /api/v1/appstore/approval/submit | 后端四环节硬校验；幂等；requirement/launch 双轨 |
| 工具10 query_product_monitor | wf_sub_07（节点701 监控查询） | GET /api/v1/appstore/product/monitor | — |
| 工具11 send_alert | wf_sub_07（异常分支）、wf_sub_03（稽核驳回分支联动） | POST /api/v1/appstore/alert/send | 枚举校验 high/middle/low |
| 工具13 query_approval_status | wf_sub_08（节点801） | GET /api/v1/appstore/approval/status | approval_id/offer_id 至少一个，缺失报 PARAM_MISSING；双轨按 approval_type 区分回显 |
| 节点结果存储（2.2 节） | 各子工作流 save_node_result / query_node_result 插件节点 + `CODE_EXTRACT_RECORD` | POST /api/v1/appstore/result/save、GET /api/v1/appstore/result/query | 直连后端；64KB 超限/req_id 格式由后端 5002/5004 兜底；`CODE_EXTRACT_RECORD` 提取 list[0].result_json 原文（list 为空 → E5） |
| CODE_RENDER_REQ | wf_sub_00（需求提报） | 内嵌代码节点 | req_id 系统生成/需求提报单渲染/pending 反查 |
| CODE_GET_TEMPLATE | wf_sub_01（节点106） | 内嵌代码节点 | 模板注册表选择，读 knowledge/templates |
| CODE_MERGE_NESTED | wf_sub_01（节点109） | 内嵌代码节点 | elements/offer/template 嵌套同构合并+pending_required |
| CODE_RENDER_TABLE | wf_sub_01（节点111） | 内嵌代码节点 | 逻辑模型报文→业务分节表格 |
| CODE_VALIDATE_ELEMENTS / CODE_OP_VALIDATE_NESTED | wf_sub_01（节点108 质量闸 / 节点110 本体校验闸） | 内嵌代码节点 + POST /api/v1/appstore/validate-nested | CODE_OP_VALIDATE_NESTED 调 validate-nested；端点不可达回退 backend_pending=1；需解释调 POST /explain |
| CODE_POLL_PROGRESS | wf_sub_04（轮询） | 内嵌代码节点 | 间隔5s/最大360次/连续5次失败 E12/超时 E13 |
| CODE_MAP_FIXED_CASES | wf_sub_04（节点315） | 内嵌代码节点 | 31 条固定用例 ACC/BILL/CUST + 九章节正式版报告数据 |
| CODE_DOWNLOAD_TEST_REPORT | wf_sub_04 | POST /api/v1/appstore/report/download | 端点不可达回退下载引导文本 |
| CODE_SUMMARY_APPROVAL | wf_sub_06 | 内嵌代码节点 | 串行自查五类结果合成汇总 |
| CODE_APPROVAL_POLL / CODE_DOWNLOAD_LAUNCH_SCRIPT | wf_sub_06 | 内嵌代码节点 + POST /api/v1/appstore/script/download | 审批轮询；自动上线脚本下载（端点不可达回退引导，backend_pending=1） |
| CODE_OP_ROOT_CAUSE | wf_sub_07 | POST /api/v1/appstore/ops/root-cause | 监控根因推理 |
| CODE_OP_CREATE_WO | wf_sub_07 | POST /api/v1/appstore/ops/work-orders | 建工单闭环 |
| CODE_OP_QUERY_OFFER | wf_sub_09 | 内嵌代码节点（读 knowledge/存量产品目录_清洗后.json） | 存量产品结构化回显/多命中收敛 |
| CODE_OP_SHELF_COMPLIANCE | wf_sub_10 | POST /api/v1/appstore/shelf-compliance | 存量 R-C* 批量合规 |
| CODE_DOWNLOAD_REPORT / 其余下载 | 按需 | POST /api/v1/appstore/report/download、/script/download | 见上 |

### 2.7 V2.0 新增适配端点（AppStoreV16Controller，7 个，网关 `BASE_URL=http://10.86.13.201:31281`）

> 本组端点为 V2.0 工作流重塑新增的后端适配端点，承接确定性逻辑（本体校验/根因/工单/合规/下载等）与既有 13 工具接口（2.1/2.3 节）分工；请求报文遵循 2.0.2 裸报文约定。

| # | 端点 | 方法 | 承载/调用方 | 说明/关键出入参 |
| --- | --- | --- | --- | --- |
| 1 | `/api/v1/appstore/ops/root-cause` | POST | wf_sub_07 `CODE_OP_ROOT_CAUSE` | 基于监控指标做异常根因推理（根因类型/置信度/建议），支撑监控运维闭环 |
| 2 | `/api/v1/appstore/ops/work-orders` | POST | wf_sub_07 `CODE_OP_CREATE_WO` | 按根因创建运维工单，形成根因→工单闭环（wo_no/状态/处理人） |
| 3 | `/api/v1/appstore/shelf-compliance` | POST | wf_sub_10 `CODE_OP_SHELF_COMPLIANCE` | 存量产品批量合规扫描（R-C* 规则），输出 R-C* 合规结论 + 整改引导 |
| 4 | `/api/v1/appstore/validate-nested` | POST | wf_sub_01 `CODE_OP_VALIDATE_NESTED`（节点110 本体校验闸） | 对 MERGE_NESTED 合并后的嵌套报文做本体校验（valid/合规性/待修正），端点不可达回退 backend_pending=1 |
| 5 | `/api/v1/appstore/explain` | POST | wf_sub_01（节点110 校验闸解释分支） | 对校验失败/待修正项输出可解释说明（解释/更正路径） |
| 6 | `/api/v1/appstore/report/download` | POST | wf_sub_04 `CODE_DOWNLOAD_TEST_REPORT` | 按 record_id 下载正式版测试报告（download_url/消息），端点不可达回退下载引导文本 |
| 7 | `/api/v1/appstore/script/download` | POST | wf_sub_06 `CODE_DOWNLOAD_LAUNCH_SCRIPT` | 按 record/offer_id 下载上线脚本（V2.6 起绝对 URL 回退引导），端点不可达回退 backend_pending=1、download_url 空 |

---

## 3. 工作流配置细化设计（V2.0 工作流重塑：11 个子工作流 JSON + 智能体语义识别 + 代码节点 + 后端端点）

### 3.0 工作流 JSON 清单与装配机制（V2.0 重塑版，对齐《工作流配置重塑改造清单》与《工作流JSON开发规范》）

> **V2.0 结构说明**：实现载体由「Skills 技能包」（SKILL.md/flow-A~D/35 脚本子命令/cpcp_api.py/references）**重塑回工作流配置**——由 `gen_workflows_v2.py` 生成 **11 个子工作流 JSON**（`wf_sub_00~10` 按环节承载业务），位于 `场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/`。意图由**智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别后直调**对应子工作流。确定性逻辑不再走技能包脚本子命令，而是内嵌为**类型6 代码节点（CODE_*）**；插件能力由后端 `AppStoreV16Controller`（`/api/v1/appstore/*`）提供，子工作流节点直接调用，不经技能包脚本命令行中转。装配经 `gen_workflows_v2.py` 重建 → 11 个子工作流 JSON 全量校验（ALL_OK）→ 部署。

| # | 工作流 JSON | 环节承载 | 核心代码节点（CODE_*） | 后端端点 |
| --- | --- | --- | --- | --- |
| 1 | `wf_sub_00` 需求提报 | 需求工单审批（requirement 轨） | `CODE_RENDER_REQ`（渲染需求提报单+req_id 生成）→ submit_release_approval（approval-type=requirement） | /result/save、/approval/submit |
| 2 | `wf_sub_01` 需求分析（模板轨） | 相似检索→取模板→要素提取→质量闸→合并→本体校验闸→表格渲染 | `CODE_EXTRACT_RECORD` → `CODE_GET_TEMPLATE`（节点106，内嵌代码节点读模板注册表）→ `CODE_VALIDATE_ELEMENTS`（节点108 质量闸）→ `CODE_MERGE_NESTED`（节点109）→ `CODE_OP_VALIDATE_NESTED`（节点110 本体校验闸）→ `CODE_RENDER_TABLE`（节点111） | /similar/offer/query、/validate-nested、/explain |
| 3 | `wf_sub_02` 智能配置 | 方案落地+融合组回显 | `CODE_EXTRACT_RECORD`、`CODE_FUSION_GROUP_ECHO` | /product/config/save、/script/download |
| 4 | `wf_sub_03` 规格稽核（实时） | 规格稽核 | `CODE_EXTRACT_RECORD` | /audit/realtime（工具2） |
| 5 | `wf_sub_05` 资费校准 | 资费校验+8 项比对 | `CODE_EXTRACT_RECORD` | /billing/rules/verify（工具8） |
| 6 | `wf_sub_04` 自动测试 | 发起→场景→轮询→结果→31 条固定用例→九章节正式版报告 | `CODE_POLL_PROGRESS`（轮询）、`CODE_MAP_FIXED_CASES`（**节点315：31 条固定用例 / ACC|BILL|CUST / P0|P1|P2 / 九章节正式版报告**）、`CODE_DOWNLOAD_TEST_REPORT` | /test/offer/start、/test/offer/scenes、/test/offer/progress、/test/offer/result、/report/download |
| 7 | `wf_sub_06` 上线审批（双轨+自动上线） | 串行自查→合成汇总→报告→审批→审批轮询→自动上线 | `CODE_SUMMARY_APPROVAL`、`CODE_APPROVAL_POLL`、`CODE_DOWNLOAD_LAUNCH_SCRIPT` | /result/save、/result/query、/approval/submit、/approval/status、/script/download |
| 8 | `wf_sub_07` 监控运维（根因+工单闭环） | 监控查询→告警→根因推理→建工单闭环 | `CODE_OP_ROOT_CAUSE`（/ops/root-cause）、`CODE_OP_CREATE_WO`（/ops/work-orders） | /product/monitor、/alert/send、/ops/root-cause、/ops/work-orders |
| 9 | `wf_sub_08` 审批进度查询 | 审批状态回显（双轨按 approval_type 区分） | — | /approval/status |
| 10 | `wf_sub_09` 存量产品查询（只读） | 存量结构化回显/多命中收敛 | `CODE_OP_QUERY_OFFER`（内嵌 knowledge/存量产品目录_清洗后.json） | — |
| 11 | `wf_sub_10` 存量合规扫描（可选） | 存量 R-C* 批量合规 | `CODE_OP_SHELF_COMPLIANCE` | /shelf-compliance |

**装配机制（gen_workflows_v2.py 生成，V2.0 重塑）**：
- 智能体（LLM）为统一入口，按 3.2 提示词【意图→工作流映射表】语义识别用户意图后直调对应子工作流；确认/调度纪律、串行不跳步、req_id 沿用、超范围拒答由提示词【调度纪律】与【限制】约束（四环节门禁/req_id 校验等后端硬校验仍保留）；
- 11 个子工作流 JSON 全部由 `gen_workflows_v2.py` 生成（工作流配置/代码节点/条件分支/端点在生成器中声明，非手工逐节点配置）；生成后对 11 个 JSON 做全量结构校验（边/节点编号正确，含 wf_sub_01 节点108~113、wf_sub_04 节点315、wf_sub_06/07/09/10 关键代码节点），全部 ALL_OK 方可部署；
- 知识库迁移至 `knowledge/`（K1 规范/K2 资费/K3 测试/K4 存量/K5 FAQ/K5 存量报文 + ontology-fields + seed_offer_groups + 存量产品目录_清洗后；原 `references/`、`skills/` 废弃），随包迁移，`CODE_*` 代码节点按需读取，K4 按销售品 ID 单文件精确定位，**禁止全量读取 18 份**；
- 大报文（plan_json/config_json/fields/report）一律经节点结果存储（query_node_result + `CODE_EXTRACT_RECORD` 取 result_json 原文）由节点内引用传递，不经模型上下文中转。
- **页面地址输出（单面板约定）**：每个子工作流结束节点统一在末尾输出 `{panel}`（由独立代码节点 type=6 `panel_code_node` 构建的 ```` ```xsbot-panel ```` 片段，单面板单 url、**JSON 紧凑无空格逐字对齐参考示例**）。url 用环节业务页 `config-workbench.html?offer_id=..&name=..&chatId=<实值>&req_id=<实值>&stage=<N>&view=stage`，`title` 用环节业务名；`chatId` 运行期以 `chat_id` 实值填充，`req_id` 运行期以 `req_id` 实值填充（有 req_id 的环节 00~06 透传、07~10 为空串）。有 `offer_id` 的环节取 `offer_id`，需求/方案阶段（00/01）取 `req_id` 兜底；页面导航进度与默认 Tab 按 `stage` 动态加载（8 环节口径）。生成辅助与口径见《工作流JSON开发规范.md》§十一。

**节点通用模式（各工作流内反复出现）**：自查上游（query_node_result + `CODE_EXTRACT_RECORD`，total==0 → E5）→ 调用插件端点（经 `/api/v1/appstore/*`）→ 仅依据出参字段判定（失败按第 7 章 E 编号中断引导）→ save_node_result 存储（失败/超时也存储）→ 按模板打印（逐字引用出参，不加工）。

> **配置规范符合性（对齐《SitechAI开发平台配置规范.md》）**：本节 11 个子工作流 JSON 的**节点类型全集、条件分支 sourcePort（-1 否则 / 0 如果）、nid UUID 形态（`a1b2c3d4-0000-4000-8000-{12位seq}` 且 seq 按子流分段）、入参两态（常量/引用）与引用三层一致（blockID、nameValue[0]、currValue 前缀=上游 id，`nameValue[1]`/`currValue`=上游 id+出参名）、array 出参必配 item 树（ARRAY_ITEM_FIELDS 白名单）、snake_case 命名（唯一 camelCase 特例 `offerId`）、源码驱动纪律（改生成器后重新生成 + 全量 ALL_OK 校验、禁止手改 JSON）、type=6 代码节点规范（`async def main(args)`/args.params/BASE_URL 占位符/含 `_json` 出参名存 JSON 字符串/后端不可达一律 `backend_pending=1` 优雅回退）、无循环节点（轮询用 CODE_POLL_PROGRESS asyncio.sleep 5s×360/30min）、req_id 代码节点系统时钟生成且 LLM 不参与** 均按配置规范实现；逐条符合性映射见主方案 6.5.6 节。凡与本规范冲突处一律以配置规范为准。

---

### 3.1 环节 → 子工作流映射索引（验收对照用，与 1.3 一致；V2.0 重塑版）

> 11 个子工作流 JSON 节点编号/口径（判定字段/打印模板/提示词/门禁/续跑映射）以 1.3 映射总表、本节索引与 3.4 节为准，业务口径与重塑前（技能包 era）零改动。逐环节关键链路如下（节点编号为生成器产物，验收时按 JSON 实际核对）。

**wf_sub_01 需求分析（模板轨）关键链路**
| 环节步骤 | 说明（口径不变） |
| --- | --- |
| 需求要素提取（LLM） | 提取 24 字段要素（elements_json 固定24项 + need_summary ≤5000字符），未提及项 value 填空字符串、不输出 pending_fields；含强制同义映射表与自检（月费表述而套餐档位为空=提取失败必须回填） |
| 相似产品查询（节点105） | query_similar_offer（要素摘要 need_summary）→ 出参 similarOffer（offerInfo=同构 fields 3 模块/9 分类 24 字段数组）；resultCode==1 或未命中走"无相似产品"分支不中断（E1） |
| 取模板（`CODE_GET_TEMPLATE` 节点106） | 按 K4/模板注册表精确定位模板，供要素提取字段模板对照 |
| 产品信息整合 / 要素提取（LLM） | **同构键值合并**（按 field 名逐字段对齐 offerInfo）：需求有值→原始需求；无值→offerInfo（AI补全）；皆缺失→留空由引擎补全；source 仅"原始需求/AI补全"两种；可辅助读取 K4 对应销售品单文件（禁止覆盖用户原始需求） |
| 质量闸（`CODE_VALIDATE_ELEMENTS` 节点108） | schema 与 elements 无法提取可提取必填项 → quality_gate=FAIL，missing_required 输出待补路径（E31），禁止直接进入合并/组装方案 |
| 合并嵌套（`CODE_MERGE_NESTED` 节点109） | elements 与 offerInfo/template 嵌套同构结构合并，产出 fields 数组 |
| 本体校验闸（`CODE_OP_VALIDATE_NESTED` 节点110） | 调后端 `POST /validate-nested`（本体校验），端点不可达回退 backend_pending=1、valid 为空；失败/需解释时调 `/explain` 输出待补/更正解释 |
| 表格渲染（`CODE_RENDER_TABLE` 节点111） | plan_md 代码生成五列模块表格（模块/分类/字段名称/字段值/备注，同模块/同分类合并展示）+ pending_fields 反查 value=待补充、req_id 系统生成 |
| 待补充项判断 | pending_fields 长度==0 → 保存后进入确认；非空 → 直接"有待补充"结束，**不保存执行方案、不产出 req_id，从源头禁止进入智能配置**；判定唯一事实源=引擎反查结果 |
| 保存执行方案 | save_node_result --node requirement；**仅无待补充项时执行**；修改场景同键覆盖写；尚含需求工单审批 requirement 轨（submit_release_approval approval-type=requirement） |
| 双结束 | 出口A（有待补充：提示补充，确认无效）/ 出口B（已保存：返回 req_id 并等待确认中断点，输出模板见 3.4.7 模板①） |

**执行主干（wf_sub_02→wf_sub_03→wf_sub_05→wf_sub_04，用户确认后一次串行跑完，四环节，中途不停顿，仅异常中断；受理验证为环节4 自动测试子集，不单列环节）**
| 子工作流（环节） | 链路要点 | 关键口径（不变） |
| --- | --- | --- |
| wf_sub_02（环节1 智能配置） | 自查 requirement → `CODE_EXTRACT_RECORD` 提取执行方案原文 → `save_product_config`（plan_json 原文原样透传，**唯一写入步骤，中间无任何模型改写**；不自动重试；出参含 script_url 绝对 URL）→ `CODE_FUSION_GROUP_ECHO` 融合组回显；落地失败时 `CODE_DOWNLOAD_LAUNCH_SCRIPT`/`/script/download` 回退引导文本 | status==SUCCESS/PARTIAL 通过（按 3.4.7 模板②打印，含"脚本文件已下载"行）；FAIL → E6 中断（fail_node=STAGE1_CONFIG）；存 config |
| wf_sub_03（环节2 规格稽核） | 自查 config+`CODE_EXTRACT_RECORD`（取 offer_id/config_json）→ `realtime_spec_audit --audit-scene all`（同步无轮询）→ 整改建议（pass=0 时整理 error_list，不新增稽核结论） | pass==1 通过；pass==0 → E8 中断（可联动 send_alert(high)）；超时重试1次仍异常 → E7（STAGE2_AUDIT）；存 spec |
| wf_sub_05（环节3 资费校准） | 自查 config+`CODE_EXTRACT_RECORD` → `check_billing_rule --check-scene all` → 风险解读（先读 K2 叠加优惠约束说明，可作解释依据但不得新增风险结论） | pass==1 通过（risk_list 为空说明）；pass==0 → E9 中断（STAGE3_FEE）；存 fee |
| wf_sub_04（环节4 自动测试含受理验证子集） | 自查 config+`CODE_EXTRACT_RECORD`（取 offer_id）→ `offer_test`（resultCode=1 直接终止 E10）→ `get_test_scenes`（空场景 E11）→ `CODE_POLL_PROGRESS` 轮询（间隔5s/最多360次/连续5次失败终止 E12/30分钟超时 E13）→ `get_test_result`（done=true 后查询）→ `CODE_MAP_FIXED_CASES`（节点315：31 条固定用例 / ACC|BILL|CUST / P0|P1|P2 → 九章节正式版报告，**受理验证为报告内子集小节**，orderId/offerInstId 为空标注人工核实 E14）→ `CODE_DOWNLOAD_TEST_REPORT`（/report/download） | test_passed==通过 打印成功（含受理验证小节）；失败/超时中断（STAGE4_TEST）；存 test |
| 成功结果详情汇总（衔接 wf_sub_06） | 四环节全成后按 3.4.7 模板②打印成功详情，**中断等待用户回复"上线审批"** | 逐字引用各环节出参，不加工；模板中 ✅/统计值与出参一一对应 |

**wf_sub_06 上线审批（双轨+自动上线）**
| 环节步骤 | 说明（口径不变） |
| --- | --- |
| 触发门禁 | 仅四环节（config/spec/fee/test）全部成功且用户明确回复"上线审批"后进入；用户仅说"帮我上线"未确认时先提示"回复【上线审批】后才能提交审批流" |
| 串行自查 | 依次 query_node_result 查 config/spec/fee/test/requirement（链式顺序非并行）；任何一步 total==0 → E15/E18 中断 |
| 合成汇总（`CODE_SUMMARY_APPROVAL`） | 提取需求摘要关键字段、offer_id/save_result、audit_summary、risk_list、场景/测点统计、orderId/offerInstId |
| 报告生成（LLM） | 按 3.4.7 模板③（强制 7 章节：需求摘要/配置落地/稽核结论/资费结论/测试统计/受理验证（orderId/offerInstId）/上线建议；全通过→"建议上线"，任一未通过→"暂缓上线"；只基于输入数据不新增结论） |
| 报告存储 | save_node_result --node report |
| 审批推送 + 审批轮询（`CODE_APPROVAL_POLL`） | submit_release_approval（approval-type=launch；后端硬校验 req_id 四环节结果齐全，跳步必被拒；幂等：同 offer_id 重复提交返回原 approval_id；推送失败重试1次后 E16 中断）→ `CODE_APPROVAL_POLL` 轮询审批状态 → 审批通过后自动生成监控运维方案 + xsbot-panel 看板 + `CODE_DOWNLOAD_LAUNCH_SCRIPT`（/script/download）→ 输出审批单号 |

**wf_sub_07/wf_sub_08/wf_sub_09/wf_sub_10（查询/运维/存量支线）**
| 子工作流 | 链路要点 | 关键口径（不变） |
| --- | --- | --- |
| wf_sub_08 审批进度查询 | `approval_status`（approval_id/offer_id 至少一个，缺失先追问不发起调用；双轨按 approval_type 区分回显）→ 按固定格式归纳输出 | "审批单号｜状态｜当前环节（审批人）｜最近意见｜更新时间"；驳回时附原因并提示可修改执行方案后重新发起；查无审批单 → E21 |
| wf_sub_07 监控运维 | `query_product_monitor`（offer_id 必填，缺失先追问不编造兜底；date_range/metric 可选）→ 异常判定 → 生成告警文案 → `send_alert` → 异常走 `CODE_OP_ROOT_CAUSE`（/ops/root-cause 根因推理）→ `CODE_OP_CREATE_WO`（/ops/work-orders 建工单闭环） | error_count>0 或 fee_error_rate>0.1 → 告警（文案含产品与异常摘要）；接口失败 → E17 终止本轮；审批状态==通过后用户回复"确认上线" → 自动生成监控运维方案（销售监控/受理监控/推送规则；数据引用 query_product_monitor 输出，不虚构指标），审批未通过时严禁生成 |
| wf_sub_09 存量产品查询（只读） | `CODE_OP_QUERY_OFFER`（内嵌 knowledge/存量产品目录_清洗后.json）→ 结构化回显/多命中收敛 | 只读，不发起任何写接口 |
| wf_sub_10 存量合规扫描（可选） | `CODE_OP_SHELF_COMPLIANCE`（POST /shelf-compliance）→ R-C* 批量合规结论 | 按 K1 业务规范判定，输出 R-C* 合规结论 + 整改引导 |

**wf_sub_07/wf_sub_08 轻量直达**：轻量查询可由智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别后直调对应子工作流（approval_status/query_product_monitor），无需走完整执行主干。

---

### 3.3 智能体语义识别调度与调度时序说明（V2.0 重写：LLM 意图识别→直调子工作流→代码节点/后端端点）

```
第一轮：
用户输入需求 → 智能体（LLM 按 3.2 提示词【意图→工作流映射表】语义识别）命中【提报需求】→ 直调 wf_sub_00
→ 需求字段抽取 LLM（仅提取，未提及项留空，同义映射+自检）→ CODE_RENDER_REQ（渲染需求提报单
  + req_id 生成，req_id 由系统时钟生成、LLM 不参与）→ submit_release_approval（approval-type=requirement
  需求工单审批轨）→ 进入 wf_sub_01 需求分析（模板轨）
wf_sub_01：query_node_result → CODE_EXTRACT_RECORD → 相似产品查询（节点105 query_similar_offer，
  要素摘要，返回相似度最高的1个产品+offerInfo 完整配置信息；未命中走"无相似产品"分支不中断 E1）
  → CODE_GET_TEMPLATE（节点106 取模板）→ 要素提取 LLM → 同构键值合并（按 field 名逐字段对齐）
  → CODE_VALIDATE_ELEMENTS（节点108 质量闸）→ CODE_MERGE_NESTED（节点109）→
  CODE_OP_VALIDATE_NESTED（节点110 本体校验闸，调 POST /validate-nested；失败/需解释调 /explain；
    端点不可达回退 backend_pending=1）→ CODE_RENDER_TABLE（节点111 五列模块表格）→
  待补充判定 → save_node_result（key=req_id 统一键, node=requirement）
→ 向用户输出《加载方案》五列模块表格 + 确认提示 → 【中断：等待执行方案确认】

用户侧：核对表格 → 回复"确认配置"（V2.2 起后端无确认门禁，确认语义由智能体 LLM 语义识别保证）

第二轮：
智能体（LLM）按 3.2 提示词【意图→工作流映射表】命中【确认配置】→ 直调 wf_sub_02 起串行执行主干
（严格串行，不等用户再发消息）：
  ① 取上下文中执行方案存储键 req_id（沿用原值，不新生成）
  ② 串行运行四环节（每环节返回后打印结果并附"建议处理"引导，环节结果由 save_node_result 落库）：
    环节1 wf_sub_02 智能配置（自查 requirement→CODE_EXTRACT_RECORD 提取执行方案原文→
      save_product_config 落地 + CODE_FUSION_GROUP_ECHO +存储 config；V2.2 起后端不校验
      CONFIRMED 标记，确认语义由意图识别保证）
    → 环节2 wf_sub_03 规格稽核（自查 config+CODE_EXTRACT_RECORD 取 offer_id/config_json，
      realtime_spec_audit 同步返回，存储 spec）
    → 环节3 wf_sub_05 资费校准（自查 config+CODE_EXTRACT_RECORD 取 config_json，
      check_billing_rule，存储 fee）
    → 环节4 wf_sub_04 自动测试（自查 config+CODE_EXTRACT_RECORD 取 offer_id，
      offer_test→get_test_scenes→CODE_POLL_PROGRESS 轮询→get_test_result→CODE_MAP_FIXED_CASES
      （节点315 31 条固定用例→九章节正式版报告）→CODE_DOWNLOAD_TEST_REPORT（/report/download），
      存储 test；输出内含受理验证小节——复用测试结果出具受理验证结论：
      orderId/offerInstId+逐受理场景比对，不新增接口调用、不设独立环节）
→ 全部成功 → 打印上线校验看板 + 提示"是否发起上线审批" → 【中断：等待审批发起确认】
（任一环节异常 → 立即中断：打印异常环节+原因+建议，引导【重新执行】/【修改执行方案】）

第三轮（用户回复"上线审批"）：
智能体（LLM）按 3.2 提示词【意图→工作流映射表】命中【上线审批】→ 直调 wf_sub_06（req_id 入参）
→ 串行自查 5 类环节结果 → 上线校验看板（5 项 ✅ 表+风险检查+AI审批建议）
→ CODE_SUMMARY_APPROVAL 合成汇总 → 按内嵌模板生成 7 章节报告并存储 node=report
→ submit_release_approval 推送（approval-type=launch；后端硬校验 req_id 四环节结果齐全）
→ CODE_APPROVAL_POLL 审批轮询 → 通过后自动生成监控运维方案+xsbot-panel 看板 +
  CODE_DOWNLOAD_LAUNCH_SCRIPT（/script/download）→ 输出审批单号
（提示可消息查询审批进度；审批通过后可回复"确认上线"进入 wf_sub_07 监控运维）

异常处置后的续跑（可选）：
用户回复【重新执行】→ 按 fail_node 映射从失败环节续跑
  （STAGE1_CONFIG→wf_sub_02、STAGE2_AUDIT→wf_sub_03、STAGE3_FEE→wf_sub_05、STAGE4_TEST→wf_sub_04）
  （已成功环节凭 req_id 存储记录回放，不重复调用写接口）
用户回复【修改执行方案】+ 修改意见 → 重入 wf_sub_00/wf_sub_01（requirement_text=<原需求+修改意见>，覆盖保存）
```

- 两次中断（执行方案确认/审批发起确认）由对话交互层承担：确认语义识别由智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别控制，审批引导回复由 wf_sub_04 末步模板控制。
- **调度纪律（智能体提示词【调度纪律】约束）**：严禁并行调用接口、严禁跳过环节、严禁凭语义推断环节成败（仅依据出参字段 status/pass/test_passed 判定）、严禁不带参数重复运行需求分析冒充执行；输出模板中的 ✅/统计值必须与出参一一对应，禁止补 ✅ 凑数、禁止虚构出参不存在的数据。

【意图→工作流映射表（智能体提示词 3.2 语义识别直调，V2.0 收敛意图路由）】
| 用户意图 | 直调动作（串行） | 入参来源 |
| --- | --- | --- |
| 提报需求 / 修改需求 | 直调 wf_sub_00→wf_sub_01（需求提报+需求分析模板轨） | requirement_text=用户需求原文含修改意见 |
| 确认配置 / 重新执行失败环节 | 直调 wf_sub_02→03→05→04 串行执行主干（V2.2：无需写 CONFIRMED 确认标记，确认语义由意图识别保证） | req_id=<上一次执行方案存储键>（沿用原值，不新生成）；重新执行按 fail_node 续跑（STAGE1~4 映射 wf_sub_02/03/05/04），已成功环节凭存储回放 |
| 上线审批 | 直调 wf_sub_06（仅四环节全部成功且用户明确确认后） | req_id=<原值> |
| 查询审批进度 | 直调 wf_sub_08 | approval_id 或 offer_id |
| 运行监控 / 确认上线（审批通过后） | 直调 wf_sub_07（监控运维） | offer_id / approval_id |
| 存量产品查询 | 直调 wf_sub_09 | 产品描述 / 产品 ID |
| 存量合规扫描 | 直调 wf_sub_10 | 存量产品清单 |
| 业务规范/资费/测试/存量/FAQ 问答 | 独走知识库 `knowledge/`：业务规范→K1、资费→K2、测试→K3、存量销售品→K4 单文件、高频问答→K5 | — |
| 超出产销品范围 | 拒答话术："抱歉，我仅支持产销品加载相关业务，请更换问题或联系管理员。" | — |

- req_id 以多轮对话内最近一次值为准（上下文记忆）；若用户确认时 req_id 未知（如新会话），先运行 `query_node_result` 检索最近执行方案，取回 req_id 后再执行确认流程。
- **确认门禁（V2.2 修订）**：`save_product_config` 确认门禁已移除（原 CONFIRMED 标记校验删除，不再返回 NOT_CONFIRMED）——"未确认不配置"由智能体 LLM 语义识别保证；`submit_release_approval` 后端仍硬校验 req_id 入参 + 四环节（config/spec/fee/test）结果齐全（跳步执行主干也无法推送审批）。

### 3.4 关键算法与提示词细化

#### 3.4.1 req_id 生成规则（V1.7 统一键，wf_sub_00 CODE_RENDER_REQ 系统生成）
```
req_id 由 wf_sub_00 内嵌代码节点 CODE_RENDER_REQ 以系统时钟生成，LLM 不参与生成（承接原
004a 拆分代码节点/技能包 build_plan 子命令口径）：
req_id = "PLAN" + datetime.now().strftime("%Y%m%d%H%M%S") + 3位随机数(%03d)
示例：PLAN20260913143025087（系统时钟保证取真实当前时刻，每次分析重新生成、必然不同）
plan_json 内的 req_id 键亦由 CODE_RENDER_REQ 注入（LLM 输出空字符串，禁止自行生成；
req_id 以会话最近一次值为准，智能体语义识别禁止重新生成）
```
后端唯一性硬校验（NodeResultService.save，双保险）：
```
① 格式校验：req_id 须匹配 PLAN\d{17}（PLAN+14位时间戳+3位随机数），非法返回 5002；
② requirement 环节同 req_id 重写：无论 result_json 内容是否相同，一律删除旧记录后
   重新插入（同键覆盖语义），避免旧方案与确认标记/环节结果错位（原 5006 冲突拦截已移除）。
```

#### 3.4.2 待补充字段判定逻辑与组合补全策略（wf_sub_01 提示词内含，此处为程序化校验口径，V2.0 更新）
```
【取值链（V2.2 字段本体推理引擎，字段形态与默认值由后端 FieldOntologyService 统一保证；字段体系=3 模块/9 分类/24 字段）】
逐级降级取值：原始需求 → 相似产品（最高相似度） → 本体默认值（推理引擎补全） → 待补充（禁止跳级虚构）
字段形态校验与默认值补全（wf_sub_01「字段本体推理」，集成于 CODE_VALIDATE_ELEMENTS（节点108 质量闸）
+ CODE_OP_VALIDATE_NESTED（节点110，POST /validate-nested，串行闭环：校验→非法值修正回写→
缺失与待补充字段默认值推理补全），方案以推理后结果为准；字段名/枚举以后端
FieldOntologyService 注册表 24 字段为唯一口径）：
  套餐属性 ∈ {主资费, 可选包, 增值包}（默认主资费）
  付费方式 ∈ {后付费, 预付费}（月付/包月归一为后付费）
  销售渠道 ⊆ {实体渠道, 电子渠道, 直销渠道}（无参照默认三者全选；营业厅/门店→实体渠道、
    APP/网厅/线上→电子渠道、直销/客户经理/政企→直销渠道同义词映射归一）
  数值类字段必须带单位（GB/分钟/条/元），缺单位自动补全（60G→60GB）；
  是否允许办理副卡 ∈ {允许, 不允许}（默认"允许"）；
  生效方式默认"立即生效（新入网）/次月1日生效（老用户）"；流量结转规则 ∈ {结转, 不结转}（默认结转）

【待补充判定（V2.2 口径：唯一不可推理项=套餐档位）】
套餐档位未提取到
    → 值="待补充"（价格禁止推理、禁止从相似产品照搬），引擎维持"待补充"加入 pending_fields
其他分类字段缺失或值="待补充"
    → 引擎一律按本体默认值推理补全（三类资源→"无"、断网授权→默认口径、
      生效方式→按新入网/老用户分别补全、退订/变更规则→默认口径等），来源="AI补全"
套餐编码：恒填"系统待生成"（不计入 pending_fields）
来源枚举校验：模型步骤3 标注仅允许 {原始需求, AI补全}；两态由模型与引擎统一标注
（引擎补全/修正后 source 标"AI补全"），出现其他值 → 校验失败重新生成
枚举/格式值校验：字段值不符合本体推理引擎枚举/格式 → 引擎自动修正回写
  （枚举归一：月付/包月→后付费；渠道同义词映射；套餐档位金额归一 "312元/月"→"312元"；
  套餐名称用户自命名保留，仅剥离修饰语）；
  无法修正的经 violations 在 wf_sub_01 输出时提示用户（引擎兜底闭环）
枚举/默认值/同义映射参照：knowledge/ontology-fields（引擎仍是单一事实源，文档仅辅助）
字段名校验：fields 数组字段名必须与 24 字段注册表逐字一致，出现旧口径字段名
（产品名称/套餐固定费/渠道类型/销售品状态/优惠条件等）视为提取失败重新生成
```

#### 3.4.3 门禁与续跑条件（V1.7：门禁下沉工具层；V2.2：确认门禁移除，确认语义由智能体 LLM 语义识别保证）

> 原平台选择器门禁随工作流承载方式废止，改由以下机制保障（V2.0 口径）：

```
① 确认门禁（V2.2 修订：已移除后端硬校验，确认语义由智能体 LLM 语义识别保证）：
   save_product_config 不再校验 confirmed 入参与存储 CONFIRMED 标记
   （原 NodeResultService.latestRecord(req_id,"CONFIRMED") 校验与 NOT_CONFIRMED
   拒绝逻辑已删除；NodeResultService.existingNodes 诊断方法一并删除）；
   "未确认不配置"由智能体 LLM 语义识别保证——未识别到确认类回复
   （确认配置/同意/可以/执行吧等）不得直调 wf_sub_02 执行智能配置；
   后端保留：plan_json 合法性校验（5001）+ 同 plan_json 幂等重放

② 串行纪律（智能体提示词【调度纪律】约束）：
   严禁并行调用接口；严禁跳过环节；仅依据出参字段判定成败
   （status==SUCCESS / pass==1 / test_passed==通过），失败即中断引导；
   环节结果由各子工作流 save_node_result 落库（失败/超时也存储）。

③ 续跑映射（fail_node → 子工作流）：
    STAGE1_CONFIG → wf_sub_02（智能配置，须重新落地）
    STAGE2_AUDIT  → wf_sub_03（规格稽核，自查 config+CODE_EXTRACT_RECORD 复用 offer_id/config_json）
    STAGE3_FEE    → wf_sub_05（资费校准，自查 config+CODE_EXTRACT_RECORD 复用 config_json）
    STAGE4_TEST   → wf_sub_04（自动测试，自查 config+CODE_EXTRACT_RECORD 复用 offer_id）
   续跑前置：按 req_id=入参统一键、node_name=config/spec/fee/test 自查回放
   已成功环节结果（不重复调用写接口）；req_id 沿用原值。

④ 审批发起门禁（工具层硬校验，V1.7）：
   submit_release_approval 后端校验：req_id 入参存在 + 逐一查询
   latestRecord(req_id, "config"/"spec"/"fee"/"test") 四条记录全部非空
   ├─ 四环节齐全 → 放行审批推送
   └─ 缺失任一环节 → 校验拒绝（提示先完成执行主干）
   配套：智能体按 3.2 提示词【调度纪律】约定四环节全部成功且用户明确确认后才直调 wf_sub_06。

⑤ 兜底：用户确认时 req_id 未知 → 先运行 query_node_result 检索最近执行方案；
   检索不到 → 提示用户重新提报需求，不得凭空编造 req_id。
```

#### 3.4.4 测试进度轮询（wf_sub_04 内嵌 CODE_POLL_PROGRESS 代码节点，承接原 0304 代码节点）
> V2.0 实现方式：轮询逻辑由 wf_sub_04 内嵌类型6 代码节点 `CODE_POLL_PROGRESS` 承载（原技能包独立脚本 poll_test_progress.py 意境，逻辑原样迁移为节点实现）。轮询结果三态供分支判定：done=true、failed=true、连续失败/超时。
```
# CODE_POLL_PROGRESS 逻辑（与原 0304 代码节点/技能包 poll_test_progress.py 一致）
interval = 5            # 轮询间隔（秒）
max_retry = 360         # 最多 360 次（30 分钟超时）
consecutive_fail = 0    # 连续查询失败计数

for i in range(max_retry):
    resp = get_test_progress(globalId)      # POST /api/v1/appstore/test/offer/progress
    if resp 查询失败:
        consecutive_fail += 1
        if consecutive_fail >= 5:
            输出 {"done": False, "failed": True, "fail_reason": "连续查询失败，转人工（保留 globalId）"}; 分支 E12
        sleep(interval); continue
    consecutive_fail = 0

    if resp.done == true:
        输出 {"done": True, "failed": False}; 分支通过
    if resp.failed == true:
        输出 {"done": False, "failed": True, "failIndex": resp.failIndex,
               "fail_reason": "测试失败/中止，仍取完整结果供报告定位失败原因"}; 分支 E12 引导
    sleep(interval)

输出 {"done": False, "failed": True, "fail_reason": "测试超时（30 分钟），请凭 globalId 人工续查"}; 分支 E13
```

#### 3.4.5 测试报告生成提示词（wf_sub_04 CODE_MAP_FIXED_CASES 节点315 内嵌模板，九章节正式版报告）
> CODE_MAP_FIXED_CASES（节点315）先行确定性构建 31 条固定用例表（ACC-001~012 / BILL-001~010 / CUST-001~009，维度 ACC|BILL|CUST、等级 P0 拦截/P1 警告/P2 提示）+ 维度汇总（dimension_summary）+ 整体结论（overall_conclusion）+ 缺陷清单（defect_list）+ 场景覆盖核对（scene_cover）+ 被测一致性核对（E26）；LLM 按 K3 模板 V2.0 渲染《销售品自动化测试报告》正式版，**9 章节结构**如下：
```
你是产销品自动测试报告生成助手。基于逐场景测试结果（testScenes）、31条固定用例确定性映射结果
（cases_json/dimension_summary/overall_conclusion/defect_list/scene_cover/e26）与受理凭证
（orderId/offerInstId）按 K3 模板 V2.0 生成《销售品自动化测试报告》正式版，9 章节结构：
一、报告概述（目的/范围/依据/等级定义 P0拦截/P1警告/P2提示）；
二、基础信息（12 项：报告编号/测试任务ID testRequestId/被测销售品名称 offerName/销售品编码 offerId/
   产品类型/所属业务域 产销品域/所属部门 产商品中心CRM_POS/生效时间/测试方式 全自动智能测试/
   测试时间/关联加载方案 req_id/测试流水号 globalId）；
三、测试总体结论（总校验用例数=各场景 testCaseCount 合计逐字引用；通过/警告/阻断/通过率/整体上线结论）；
四、分项测试结果：4.1 受理验证（ACC-001~012，结果逐行引用 cases_json ACC 行原值，未覆盖标
   "本销售品未覆盖"，不判❌不计入阻断；受理凭证 orderId/offerInstId 为空按 E14 标注人工核实；
   **受理验证独立成节**，标题"环节7/9·受理验证"，逐字引用受理凭证与逐受理场景 S_O_TC/S_ADD_CARD/
   S_U_TC 结论与关键测点比对，融合品含 S_GROUP_BIND/S_ADDON_SUB）；
   4.2 计费验证（BILL-001~010，引用 cases_json BILL 行，可引用资费自检 compare_list/risk_list）；
   4.3 客服验证（CUST-001~009，引用 cases_json CUST 行，可引用稽核自检 error_list）；
五、缺陷问题明细清单（引用 defect_list，无则写"无"）；
六、业务风险汇总（引用 risk_list 连同 P1 ❌ 项；无警告级风险固定输出"未发现警告级风险。"）；
七、整改修复建议（无阻断/警告问题固定输出"无需整改"；有则逐条给出可落地整改建议）；
八、最终测试结论与审批建议（三选一=overall_conclusion，判定规则见 K3 规范第6章；e26=0 时结论
   须从严标注并提示人工核实，不输出通过性明细）；
九、版本说明（V1.0）。
严格遵守：31 条固定用例清单禁止增删改；用例结果仅依据出参字段判定；只基于输入数据生成，
禁止虚构测点/结论/统计值；e26 不一致时按 E26 中断口径输出（不输出通过性明细）。
```

#### 3.4.6 模型纪律（温度与输出约束）
> V2.0：原平台"逐节点温度配置"改为**全局模型纪律**（各工作流 LLM 节点固定配置），口径不变：温度 0.2（严谨输出）；所有生成/汇总/解读场景"逐字引用输入数据，不新增内容"。
>
> **配置规范对齐**：LLM 节点固定 `model=qwen3-30b-a3b`、`temperature=0.2`、`top_p=0.5`、`max_tokens=2048`（对齐《SitechAI开发平台配置规范.md》LLM 节点纪律），各节点提示词末尾统一追加"**仅输出对应出参字段**"约束，禁止输出推理过程或额外文档。

| 场景 | 纪律 | 理由 |
| --- | --- | --- |
| wf_sub_01 要素拆解、产品信息整合 | 温度 0.2 | 结构化输出，抑制幻觉 |
| wf_sub_03 整改建议 | 温度 0.2 | 忠实于稽核结果 |
| wf_sub_04 测试报告（九章节正式版） | 温度 0.2 | 数据引用型生成 |
| wf_sub_05 风险解读 | 温度 0.2 | 忠实于校验结果（可引用 K2 作解释依据，不得新增风险结论） |
| wf_sub_06 报告生成（7 章节上线报告） | 温度 0.2 | 汇总引用型，报告结构强制 |
| 环节结果打印/成功详情汇总/异常处置说明（3.4.7 模板②④⑤） | 温度 0.2 | 逐字引用出参与模板，不加工不臆测 |
| wf_sub_07 告警文案 | 温度 0.2 | — |
| wf_sub_08 审批状态摘要 | 温度 0.2 | 结构化摘要 |
| 交互层（全局） | 温度 0.2 | 主方案 3.3 统一配置，严谨场景 |

#### 3.4.7 输出模板汇总（承接原 3.1.1~3.1.5 提示词模板，全文迁入各子工作流 LLM/代码节点，此处保留口径）
**模板① 执行方案输出（wf_sub_01 出口B，承接原结束节点A）**
```
{{plan_md（Markdown 表格，固定5列：模块/分类/字段名称/字段值/备注；备注两态【原始需求】/【AI补全】；
同模块/同分类合并展示，模块列加粗）}}

【待补充字段】{{pending_fields | 为空时显示"无，所有字段均已明确"}}

【若存在待补充字段】执行方案暂未保存、暂不能执行（回复【确认配置】无效）：
- 请直接补充套餐档位字段值，将更新执行方案并再次确认；
【若待补充字段为空（req_id 已生成）】已识别并生成《××》销售品需求单，已保存（req_id：{{req_id}}），请核对：
- 回复【确认配置】：将自动串行执行 智能配置→配置规格稽核→资费校准→自动测试（含受理验证子集） 四个环节
  （每环节执行后向您打印结果，仅异常时中断）；
- 如需调整：请直接说明修改意见（仅套餐档位须由您补充，其余字段已按相似产品补全）。
- 建议处理：可输入"确认配置"进入【智能配置】。
```

**模板② 每环节结果打印（wf_sub_02/03/05/04 各环节结束，承接原 3.1.2）**
```
【环节N/{环节名称}】✅ 执行成功
- 关键数据：{该环节关键输出（✅/统计值与出参一一对应，出参没有的数据省略该行）}
  环节1：offer_id / 各模块字段写入结果（save_result）+ script_url（绝对 URL）
  环节2：稽核通过 + audit_summary
  环节3：资费校准通过 + 8 项比对表（compare_list 全部一致） + risk_list 为空说明
  环节4：场景数/测点数统计 + 测试结论 + 受理验证小节（orderId/offerInstId + 逐受理场景结论）
- 已自动进入下一环节……（环节4 时改为"- 执行主干全部完成"）
- 建议处理：可输入"××"进入【××】。
```
> 实现：执行类环节结果直接引用插件端点出参拼装（不经过模型加工）；模型仅做格式化时提示词注明"逐字引用输入数据，不新增内容"。

**模板③ 成功结果详情汇总（执行主干末步，承接原节点14/3.1.3；wf_sub_06 输出=上线校验看板+7 章节报告）**
```
执行主干四个环节全部成功，请按以下模板输出（逐字引用输入数据，不新增结论）：

【执行主干全部完成】✅ 共4个环节执行成功：
1. 智能配置：offer_id={...}，各模块字段全部写入成功；
2. 配置规格稽核：通过，{audit_summary}；
3. 资费校准：通过，未发现叠加/互斥冲突；
4. 自动测试：场景 N 个、测点 M 个全部一致；受理验证（测试子集）：orderId={...}，offerInstId={...}，各受理场景均通过。

是否发起上线审批？回复【上线审批】将汇总以上结果提交审批流；回复【暂不】可稍后发送"上线审批"继续。
```

**模板④ 异常处置（各子工作流统一异常出口，承接原节点21/3.1.5）**
```
执行主干在某一环节异常中断，请生成异常处置说明：
1. 异常环节名称（按 fail_node 映射：STAGE1_CONFIG 智能配置 / STAGE2_AUDIT 配置规格稽核 /
   STAGE3_FEE 资费校准 / STAGE4_TEST 销售品自动测试）；
2. 异常原因（引用接口返回原文 resultCode/resultMsg，不得臆测）；
3. 关键明细（稽核问题清单 / 资费风险清单 / 测试失败测点 / 超时信息，按实际输入展开）；
4. 整改建议；
5. 结尾固定引导：
请选择下一步：
① 回复【重新执行】：将自动从失败环节继续（已成功环节不重复执行）
② 回复【修改执行方案】：请说明修改意见，将重新生成执行方案并再次确认
不得自行发起重试，不得跳过失败环节。
```
可选联动：稽核驳回（STAGE2_AUDIT）等严重异常 → send_alert(alarm_level=high)。

### 3.5 子工作流级自测用例（对应主方案 8.1 分层测试第 2 层，V2.0 对齐智能体语义识别直调与子工作流）
| # | 用例 | 步骤 | 预期 |
| --- | --- | --- | --- |
| 1 | 正向全流程 | 输入《产品信息.txt》900102308 改写需求 → 确认 → 串行执行 wf_sub_02→03→05→04 → 审批确认 | 执行方案生成→确认（智能体语义识别确认语义，V2.2 无需写标记）→四环节串行执行（每环节打印结果、不停顿）→成功详情+审批提示→确认→wf_sub_06 审批单生成 |
| 2 | 未确认不配置 | 第一轮结束后直接输入"帮我配置落地"（不携带确认） | 拒绝并提示先确认执行方案（回复"确认配置"）；save_product_config 未被调用（CRM 无记录） |
| 3 | 执行方案修改 | 回复修改意见（如"套餐档位改为 39 元"） | 重入 wf_sub_00/wf_sub_01 覆盖写同 req_id，重新等待确认；确认后落地为修改后版本 |
| 4 | 待补充字段 | 需求不含套餐档位 | 套餐档位=待补充列入 pending_fields；其余字段均为 AI补全且无"待补充"残留；套餐编码="系统待生成"；req_id 为空、无法进入确认流程 |
| 4b | 资源部分提供不拦截 | 需求仅含流量资源（无语音/短信、无价格） | 仅套餐档位=待补充；语音/短信资源按相似产品 AI补全（资源缺失按本体默认值补全不算缺失）；套餐编码="系统待生成" |
| 5 | 串行连续性 | 确认后观察执行过程 | wf_sub_02→03→05→04 四环节一次完成，中途无多余人工询问、无并行调用；每个环节执行后均打印结果（引用出参原文） |
| 6 | 稽核驳回中断与引导 | 构造配置缺陷（如互斥优惠叠加） | 实时稽核同步返回 pass=0 → 立即中断 → 打印"异常环节：配置规格稽核+原因+error_list 明细+建议" → 引导重新执行/修改执行方案 |
| 7 | 重新执行续跑 | 用例6 后回复【重新执行】 | 从稽核环节续跑（不重复运行环节1 save_product_config，CRM 无重复记录）；已成功环节结果回放打印 |
| 8 | 修改执行方案回退 | 用例6 后回复【修改执行方案】+ 修改意见 | 重入 wf_sub_01 生成执行方案，再次等待确认 |
| 9 | 资费驳回中断 | 构造资费冲突样例 | 资费校准 pass=0 → 中断，打印异常环节+风险清单+引导 |
| 10 | 测试失败中断 | 构造配置错误使测点不一致 | 中断，打印异常环节+失败测点明细+受理验证结论+引导 |
| 11 | 测试超时中断 | 模拟进度长期不 done | 30 分钟超时（CODE_POLL_PROGRESS 分支 E13）→ 打印"自动测试超时+globalId"+引导 |
| 12 | 落地与方案一致性 | 对比 plan_json 与 save_result | 四类字段逐项一致，无二次生成痕迹 |
| 13 | 审批发起门禁 | 主干全部成功后不回复"上线审批"，直接要求"帮我上线" | 按智能体语义识别【调度纪律】提示先确认发起审批；wf_sub_06/submit_release_approval 未被调用；回复"暂不"后隔轮发送"上线审批"可续办 |
| 14 | 审批进度消息查询 | 审批推送后发送"查询审批进度" | 运行 wf_sub_08 query_approval_status 返回"审批单号+状态+当前环节+审批人+意见"摘要；未发起审批时提示"未找到审批单" |
| 15 | 监控结果消息查询 | 发送"查询销售品 900102308 监控结果" | 运行 wf_sub_07 query_product_monitor 返回订单量/异常量/计费差错率/告警列表；error_count>0 时附告警建议 |
| 16 | 监控告警分支 | wf_sub_07 定时巡检且构造 error_count>0 | send_alert 触发，告警文案含产品与异常摘要；异常走 CODE_OP_ROOT_CAUSE（/ops/root-cause）→ CODE_OP_CREATE_WO（/ops/work-orders）工单闭环 |
| 17 | 确认语义识别（V2.2 修订，原确认标记硬校验废止） | 未回复确认直接要求配置 | 按智能体语义识别拒绝执行智能配置（后端门禁已移除，confirmed 任意值均可落地）；CRM 无写入 |
| 18 | 四环节硬校验（V1.7） | 模拟跳步：环节不全直接调 submit_release_approval | 后端校验拒绝（四环节结果缺失）；引导先完成执行主干 |
| 19 | 18 套餐全量兼容（V1.6） | 依次以 18 个销售品（5G-A 系列 10 个 + 权益随心选系列 8 个）改写需求走正向全流程 | 每个销售品需求分析→确认→四环节串行→成功详情均正常；测试报告 presetValue 与《产品信息.txt》规则值一致 |
| 20 | 权益随心选类套餐兼容（V1.6） | 以 900117022（娱乐版19.9元）改写需求走全流程 | 权益类字段（权益内容/档次资费）AI补全正确，无"待补充"误标；稽核与资费校验通过 |
| 21 | 未收录销售品降级（V1.6） | 输入不在 18 个销售品之列的产品 ID | 系统提示"该销售品不在存量资料范围，请提供完整需求信息"，不返回伪造模拟数据 |
| 22 | 存量查询/合规（V2.0） | 触发存量产品查询 / 存量合规扫描 | wf_sub_09 CODE_OP_QUERY_OFFER 结构化回显/多命中收敛；wf_sub_10 CODE_OP_SHELF_COMPLIANCE（POST /shelf-compliance）输出 R-C* 合规结论 + 整改引导 |

---

## 4. 知识库细化设计（V2.0 重塑：承载方式=knowledge/ 目录，随包迁移，由代码节点/子工作流按读取指令按需读取）

### 4.1 知识分类总表
| # | 分类名称 | 用途 | 读取时机（代码节点/子工作流读取指令，替代原平台挂载点） |
| --- | --- | --- | --- |
| K1 | 产销品业务规范库 | 规格稽核判定依据、需求解析参照、需求提报引导 | wf_sub_01 要素提取/本体校验闸；wf_sub_10 存量合规；交互层业务规范问答 |
| K2 | 资费规则库 | 资费校准依据、风险解读增强 | wf_sub_05 风险解读（先读 K2资费_叠加优惠约束说明） |
| K3 | 测试规范库 | 测试用例设计规范、受理/计费测试标准、报告模板 | wf_sub_04 CODE_MAP_FIXED_CASES 报告生成（九章节模板） |
| K4 | 存量销售品资料库 | AI补全取值字段参照、相似度结果解读、测试预期值人工核对基准 | wf_sub_01 要素提取（核心，按销售品 ID 单文件）；wf_sub_09 CODE_OP_QUERY_OFFER |
| K5 | FAQ | 直接问答（需大模型归纳） | 交互层 FAQ 问答 |

### 4.2 文档清单与命名规范

#### 4.2.1 文档清单
| 分类 | 文档 | 来源 | 说明 |
| --- | --- | --- | --- |
| K1 | 《产销品管理办法》 | 业务部门 | 上架流程、职责分工 |
| K1 | 《产销品配置规范》 | 业务部门 | 配置工作台字段定义与填写规范 |
| K1 | 《产销品命名规则》 | 业务部门 | 产品/销售品命名模板 |
| K2 | 《资费模板手册》 | 计费部门 | 资费结构与计费口径 |
| K2 | 《叠加优惠约束说明》 | 计费部门 | 互斥/依赖/叠加规则（资费校准依据） |
| K3 | 《销售品测试用例设计规范》 | 测试部门 | 受理/计费测试标准 |
| K3 | 《销售品测试报告模板》 | 测试部门 | 报告结构基准（与 3.4.5 提示词对齐） |
| K4 | 《产品信息.txt》 | 需求方提供 | 全部 18 个销售品全量规则：5G-A 系列 10 个 + 权益随心选系列 8 个（拆分为单文件，见 4.2.3） |
| K5 | 《产销品加载FAQ》 | 自编 | 高频问答（稽核不通过怎么办、如何查询存量产品等） |

#### 4.2.2 命名规范
```
[分类代码]_[文档名]_[版本号]
分类代码：K1规范 / K2资费 / K3测试 / K4存量 / K5FAQ（目录名与文件名前缀一致）
示例：knowledge/K2资费/K2资费_叠加优惠约束说明_V1.0.md
版本更新：同名新版本替换旧文件（先放新再删旧，避免读取空窗）
```

#### 4.2.3 《产品信息.txt》拆分入库方案（K4 核心）
| 项 | 策略 |
| --- | --- |
| 源内容 | 18 条销售品记录：5G-A 系列 10 个（900102308 5G-A套餐199元 / 900113043 单品239元 / 900113046 融合199元 / 900102307 融合299元 / 900102313 融合399元 / 900113044 融合239元 / 900102310 单品299元 / 900102312 单品399元 / 900113045 单品199元 / 900102306 融合199元）+ 权益随心选系列 8 个（900117022/900117020/900117027/900117026/900117023/900117024/900117025/900117021，19.9元/29.9元 各娱乐/生活/出行/商超版） |
| 一级拆分 | 按销售品 ID 拆分为 18 个独立文档（每条记录一个文档），解决单条记录过长（>2000字符）导致的上下文浪费 |
| 文件结构 | 每个文档内部按章节组织：套内资费 / 套外资费 / 过渡期资费 / 副卡 / 流量结转 / 断网授权 / 停机规则 / 计费周期与付费方式 / 销售渠道 / 套餐订购 / 套餐变更 / 退订拆机携出 |
| 章节标题前缀 | 每个章节标题加元信息：`[销售品ID] [销售品名称] [章节名]`（如 `[900102308] [5G-A套餐199元] [套外资费]`），提升定位与引用可读性 |
| 用途边界 | 仅用于：① AI补全取值字段参照（wf_sub_01 CODE_MERGE_NESTED 读取）；② 需求样例改写测试；③ 测试预期值（presetValue）人工核对基准。**不得作为新需求字段来源覆盖用户原始需求**（写入 wf_sub_01 约束） |
| 读取纪律 | **禁止全量读取 18 份文件**；仅按 similarOfferId 精确定位单文件（如 `knowledge/K4存量/K4存量_产品信息900102308_V1.0.md`） |

### 4.3 文件粒度与读取参数（替代原切片/召回参数，V2.0）
| 项 | 配置 |
| --- | --- |
| 粒度 | 原平台"切片 512 token/片（K4 按章节自然边界，单片 ≤800 token）、重叠 50 token"策略**已固化为文件拆分结构**（K4 一销售品一文件、按章节分节），无需切片配置 |
| 读取方式 | 全文读取单文件（模型 read 指令），无检索参数 |
| 定位方式 | 按文件名精确定位（销售品 ID / 分类代码），不依赖语义检索 |
| 引用展示 | 开启（回复中标注所读文档名，便于核验） |
| 读取时机 | wf_sub_01 要素提取（K1+K4 单文件）、wf_sub_04 报告生成（K3）、wf_sub_05 风险解读（K2）、交互层 FAQ（K5 及全部分类兜底）、wf_sub_09 存量查询（K4 单文件/存量产品目录） |

### 4.4 知识库维护机制
| 机制 | 说明 |
| --- | --- |
| 更新触发 | 新规范发布 / 新资费政策 / 新销售品上线后，由知识运营人员按 4.2.2 命名规范替换 knowledge/ 对应目录文件 |
| 版本管理 | 同名文档先放新后删旧；每月核对目录内文档版本清单 |
| 质量校验 | 每次更新后执行 3 组固定问答回归（例：查 900102308 套外资费 → 应命中阶梯计费描述） |
| 过期清理 | 下线销售品资料保留 6 个月后归档（不删除，避免历史追溯断档） |
| 权限 | knowledge/ 目录读写权限限于项目组；FAQ 更新需业务复核 |

---

## 5. 工作流配置部署清单（V2.0 重塑，逐项核对）

| # | 部署项 | 配置值 | 核对要点 |
| --- | --- | --- | --- |
| 1 | 工作流 JSON | 11 个子工作流（`wf_sub_00~10`），位于 `场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/` | 由 `gen_workflows_v2.py` 生成；全量结构校验（边/节点编号）ALL_OK |
| 2 | 智能体语义识别提示词 | **意图→工作流映射表**（3.2）：按用户意图语义识别后直调 `wf_sub_00~10`；含确认语义识别（V2.2 无需写确认标记）+ 调度纪律 + 超范围拒答话术 | 确认含确认语义识别（V2.2：无需写确认标记）、串行纪律、超范围拒答话术 |
| 3 | 子工作流节点 | wf_sub_00~10 逐个部署，节点=代码节点（CODE_*）与插件端点 | 各子流节点编号与 3.1 映射索引一致（wf_sub_01 节点106~113、wf_sub_04 节点315 等） |
| 4 | knowledge/ 知识库 | K1规范/K2资费/K3测试/K4存量/K5FAQ/K5存量报文 + ontology-fields + seed_offer_groups + 存量产品目录_清洗后（随包迁移） | K4 需确认 18 个销售品单文件齐全；原 references/、skills/ 已废弃 |
| 5 | 生成器 | `gen_workflows_v2.py`（阶段1.3 代码节点内嵌 CODE_RENDER_REQ/CODE_GET_TEMPLATE/CODE_MERGE_NESTED/CODE_RENDER_TABLE 等） | 固定用例映射（CODE_MAP_FIXED_CASES 节点315 31 条）与九章节报告模板内嵌一致 |
| 6 | 网关地址 | `BASE_URL=http://10.86.13.201:31281`（代理 `/api/v1/appstore/*` 到后端） | 指向后端模拟服务；替换真实实现仅改此值 |
| 7 | 后端依赖 | 13 工具模拟服务 + 7 新适配端点（AppStoreV16Controller）+ NodeResultService（pd_ai_node_results 表，H2/MySQL DDL 已执行） | 联通自测通过；硬校验（5002/四环节门禁/幂等）生效 |
| 8 | 模型纪律 | 温度 0.2（严谨输出） | 出参逐字引用不加工；仅依据出参字段判成败 |
| 9 | 环境 | 工作流平台/Agent 运行时加载 11 个子工作流 JSON + knowledge/ + 后端服务 | 11 个子工作流 JSON 全量导入后端/agent 框架 |
| 10 | 引导问题 | 智能体语义识别开场引导（查询审批进度/监控结果/重新执行） | 覆盖"确认配置"演示路径 |
| 11 | 超范围拒答 | "抱歉，我仅支持产销品加载相关业务，请更换问题或联系管理员。" | 智能体提示词【限制】超范围拒答分支 |
| 12 | 运行时适配 | 工作流配置平台/Agent 框架（Claude/自建 Agent 框架） | 大报文经节点结果存储/代码节点取原文，不经模型上下文中转 |

> **配置规范符合性自测（对齐《SitechAI开发平台配置规范.md》源码驱动纪律）**：部署前逐项核对——① 11 个子工作流 JSON 均由 `gen_workflows_v2.py` 生成，**禁止手改 `智能体工作流集V1.6/*.json`**；改业务逻辑→改生成器→重新生成→全量结构校验 ALL_OK。② 导入平台后人工复核节点契约：每子流开始节点入参两态（常量/引用）正确、跨节点引用三层一致（blockID/nameValue[0]/currValue 前缀=上游 id，`nameValue[1]`=上游 id+出参名）、array 出参均配 item 树（ARRAY_ITEM_FIELDS 白名单）、无未枚举节点类型、无平台循环节点（轮询仅 CODE_POLL_PROGRESS）、条件分支 sourcePort=-1/0 正确、nid 满足 `a1b2c3d4-0000-4000-8000-{12位seq}` 且 seq 按子流分段。③ 所有 JSON 出参键 snake_case（唯一 camelCase 特例 `offerId`）。④ HTTP 插件与代码节点统一走网关 `BASE_URL=http://10.86.13.201:31281`。凡冲突处以配置规范为准。符合性明细见主方案 6.5.6 节。

---

## 6. 变量命名约定

### 6.1 子工作流节点变量与参数（V2.0 重塑：原工作流变量/脚本参数→工作流 JSON 节点变量/入出参键）
| 风格 | 适用 | 示例 |
| --- | --- | --- |
| 小写下划线 | 工作流节点变量/入出参键/JSON 键 | `requirement_text`、`req_id`（统一键，各子工作流节点贯穿传递）、`test_report`、`pending_fields`、`resume_action`、`fail_node`、`approve_confirmed`、`result_json`（自查/存储载荷） |
| 驼峰 | 接口原始出参字段（与接口清单保持一致，不做改名；特例：`offerId` 为后端工具3 参数名，保持 camelCase） | `globalId`、`offerId`、`testScenes`、`orderId`、`offerInstId`、`presetValue`、`testValue` |
| 环节产出引用 | 上一环节出参作为下一环节入参（经节点结果存储 query_node_result + `CODE_EXTRACT_RECORD` 取原文） | 环节1 `offer_id` → 环节2/3/4 经 config 原文取 offer_id；config 结果原文 → 环节2/3/4 入参引用 |
| req_id + node_name | 节点结果存储寻址（V1.7 统一键：req_id=执行方案存储键；node_name=requirement/config/spec/fee/test/report（V2.2 起 CONFIRMED 废止）） | req_id=`PLAN20260913143025087`、node_name=`spec` |

> **命名符合性（对齐《SitechAI开发平台配置规范.md》）**：本方案 JSON 数据传输统一 snake_case；唯一 camelCase 特例为后端接口原始出参字段 `offerId`（配置规范特许），跨契约其余参数一律 snake_case。跨节点引用出参名取值 `nameValue[1]`/`currValue`=上游 id+出参名（snake_case），满足引用三层一致。

### 6.2 存储与流水
| 变量 | 规则 | 示例 |
| --- | --- | --- |
| `req_id` | `PLAN` + yyyyMMddHHmmss + 3位随机数（V1.7 统一键，wf_sub_00 CODE_RENDER_REQ 以系统时钟生成、每次分析重新生成；会话内沿用最近值禁止重新生成） | `PLAN20260913143025087` |
| `globalId`（测试流水） | 接口返回原值，不得重生成 | `50202608252017364447983718` |
| `transactionId` | yyyyMMddHHmmssSSS + 4~6位随机数（测试流水生成，仅作报文流水） | `20260912102450123456` |
| node_name 枚举 | requirement / config / spec / fee / test / report（V1.6 新增 report）；CONFIRMED 已废止（V2.2 确认门禁移除） | `report` |
| 存储介质 | 后端 `pd_ai_node_results` 表持久化（NodeResultService 落库；H2/MySQL 双 DDL），同键覆盖 | — |

### 6.3 枚举值约定（提示词/程序判定统一使用）
| 枚举 | 取值 |
| --- | --- |
| 字段来源 | 原始需求 / AI补全（两态；相似产品取值与引擎补全/修正均标 AI补全） |
| 待补充范围 | 唯一触发情形：套餐档位（价格）未提供且知识库无参照（价格禁止推理与照搬） |
| 套餐编码默认值 | "系统待生成"（由智能配置环节落地后生成，不计入 pending_fields） |
| 产品编码 | 不补全：由智能配置环节落地后生成，需求未提供时值填"由智能配置生成" |
| 稽核场景 | spec / fee / all |
| 资费校验场景 | fee / overlay / superposition / all |
| 告警级别 | high / middle / low |
| 确认状态 | 智能体语义识别（V2.2：后端 CONFIRMED 确认标记校验已删除，"未确认不配置"由智能体语义识别保证；node_name 枚举不再使用 CONFIRMED） |
| 审批发起确认 | 用户对话确认"上线审批"（V1.1 新增；门禁由 submit_release_approval 四环节硬校验保障） |
| 确认上线 | 审批状态==通过后用户对话"确认上线"（V2.2 新增；触发监控运维方案生成，审批未通过严禁生成） |
| 续跑指令 | retry_from_fail（从失败环节续跑）/ revise_plan（修改执行方案）（V1.1 新增；按 fail_node 映射续跑对应子工作流） |
| 失败环节编码 | STAGE1_CONFIG / STAGE2_AUDIT / STAGE3_FEE / STAGE4_TEST（V1.1 新增；对应 wf_sub_02/03/05/04） |
| 错误码/分支 | PARAM_MISSING / HTTP_xxx / NET_ERROR / TIMEOUT / PARSE_ERROR / 5002（req_id 格式）/ 5004（64KB 超限）；CODE_POLL_PROGRESS 分支（done/failed/连续失败 E12/超时 E13） |
| 审批状态 | 审批中 / 通过 / 驳回（V1.1 新增） |
| 测试场景编码 | S_O_TC（套餐新装）/ S_ADD_CARD（副卡加装）/ S_U_TC（套餐退订）/ S_GROUP_BIND（融合组绑定）/ S_ADDON_SUB（融合附加订购） |
| 测点编码 | P_EFF_DATE / P_EXP_DATE / P_STATUS / P_MAIN_PROD / P_RELY_REL / P_MUTEX_REL / P_ORD_CNT / P_OFFER_NAME / P_OFFER_TYPE / P_PAY_MODE |

---

## 7. 异常处理矩阵（V2.0 重塑：触发点表述改为子工作流/代码节点/后端端点口径，处置逻辑不变）

| # | 异常场景 | 触发点 | 系统行为 | 用户感知 | 恢复方式 |
| --- | --- | --- | --- | --- | --- |
| E1 | 相似度分析接口失败 | wf_sub_01（节点105 相似产品查询） | 跳过相似产品，仅用知识库 K4 补全 | "相似产品服务暂不可用，已基于存量资料补全" | 自动降级，无需干预 |
| E2 | 模型输出来源枚举违规 | wf_sub_01（要素提取/校验） | 程序化校验失败 → 重新生成（最多2次） | 无感知或"解析重试中" | 2次失败后转人工 |
| E3 | 未确认即触发配置 | 智能体语义识别（V2.2） | 拒绝进入执行主干 | "请先确认执行方案后再触发智能配置" | 用户回复"确认配置"后直调 wf_sub_02 执行主干 |
| E4 | 绕过确认直调 save_product_config（跳步/外部直调） | V2.2 门禁已移除——后端不再拦截，仅做 plan_json 合法性与幂等校验 | 正常落地（确认与否由调用方保证） | 调用方收到落地结果 | 防跳步职责回归智能体语义识别 |
| E5 | 自查无执行方案（req_id/node_name 查无记录或 total=0） | wf_sub_02 自查（query_node_result + `CODE_EXTRACT_RECORD`） | 终止并返回提示（CODE_EXTRACT_RECORD total==0） | "未找到执行方案，请先完成需求分析" | 重新走需求分析 |
| E6 | 配置落地 FAIL | wf_sub_02 → 出参判定 | **主干中断** → 模板④异常处置输出 | 打印"异常环节：智能配置"+失败分类明细+建议，引导重新执行/修改执行方案 | 用户二选一后续跑 |
| E7 | 实时稽核超时 | wf_sub_03 | 重试1次后仍超时 → **主干中断** → 模板④异常处置 | "异常环节：配置规格稽核（超时）"，保留请求报文 | 回复【重新执行】续跑稽核环节 |
| E8 | 稽核 pass=0 | wf_sub_03 出参判定 | **主干中断** → 模板④异常处置（可联动 send_alert(high)） | 打印异常环节+error_list 明细+整改建议+引导 | 重新执行（不推荐）或修改执行方案 |
| E9 | 资费校验 pass=0 | wf_sub_05 出参判定 | **主干中断** → 模板④异常处置 | 打印异常环节+资费风险清单+引导 | 修改执行方案（修正资费） |
| E10 | 测试发起失败 resultCode=1 | wf_sub_04 → 出参判定 | **主干中断** → 模板④异常处置 | 打印"异常环节：销售品自动测试"+resultMsg+引导 | 排查销售品状态后重新执行 |
| E11 | 测试场景为空 | wf_sub_04 | **主干中断** → 模板④ | "该销售品未匹配到测试场景，请检查配置"+引导 | 修改执行方案后重测 |
| E12 | 进度查询连续5次失败 | wf_sub_04 `CODE_POLL_PROGRESS`（连续失败分支） | **主干中断** → 模板④ | 提示凭 globalId 人工续查+引导 | 人工查询后决定重新执行 |
| E13 | 测试超时（30分钟） | wf_sub_04 `CODE_POLL_PROGRESS`（超时分支） | **主干中断** → 模板④ | 提示超时 + globalId + 引导 | 人工续查或重新执行 |
| E14 | orderId/offerInstId 为空 | wf_sub_04 `CODE_MAP_FIXED_CASES` 报告生成/受理验证成节 | 报告正常生成（不算异常，不中断） | 受理验证结论标注"未获取到受理凭证，需人工核实" | 人工核实受理结果 |
| E15 | 未经确认发起审批 | wf_sub_06 触发门禁 / submit_release_approval 后端硬校验 | 四环节结果不齐时拒绝推送 | "执行结果已保留，回复【上线审批】后才能提交审批流" | 用户确认后直调 wf_sub_06 |
| E16 | 审批推送失败 | wf_sub_06（审批推送） | 重试1次后终止 | 返回失败原因 | 修复后重新推送（幂等） |
| E17 | 监控接口失败 | wf_sub_07 | 终止本轮 | "监控查询失败" | 下一周期自动重试 |
| E18 | 续跑参数非法 | fail_node 非法或 req_id 查无记录 | 按首次执行处理（重新串行执行主干） | "未找到可续跑的执行记录，已从头开始执行主干" | 无需干预 |
| E19 | 重新执行时写接口重复调用防护 | 各环节自查前置校验 | 查 req_id=入参统一键 + node_name=本环节名 已存在成功记录 → 跳过写接口直接回放 | "环节已成功，直接从失败环节继续" | 无需干预 |
| E20 | 主干中段接口异常（稽核/资费/测试接口网络错误） | wf_sub_03/05/04 出参 resultCode=NET_ERROR/TIMEOUT | **主干中断** → 模板④异常处置 | 打印异常环节+网络异常说明+引导 | 稍后回复【重新执行】续跑 |
| E21 | 审批进度查询无审批单 | wf_sub_08 | 返回查无记录 | "未找到该销售品的审批单，请确认是否已发起审批" | 先发起审批后再查询 |
| E22 | 监控/审批查询缺少必填参数 | wf_sub_07/08 前置检查 | 不发起调用，先追问用户 | "请提供销售品ID（或审批单号）" | 用户补齐参数后重查 |
| E23 | 环节结果存储写入失败 | 各环节结束前 save_node_result | 打印结果不受影响；记录存储失败日志 | 无感知（仅影响续跑回放） | 续跑退化为全量重跑，提示用户 |
| E24 | 审批未通过即要求确认上线 | wf_sub_06/wf_sub_07 触发门禁（V2.2 新增） | 拒绝生成监控运维方案 | "审批尚未通过，暂不能生成监控运维方案" | 审批通过后回复"确认上线"再生成 |
| E26 | 被测一致性核对不一致（e26=0） | wf_sub_04 `CODE_MAP_FIXED_CASES` 节点315 | 九章节报告八章最终结论须从严标注并提示人工核实，不输出通过性明细 | 报告标注"被测一致性需人工核实" | 人工核实被测销售品后重测 |
| E31 | 要素提取质量闸 FAIL | wf_sub_01 `CODE_VALIDATE_ELEMENTS` 节点108 | quality_gate=FAIL，missing_required 输出待补路径，禁止进入合并/组装方案 | 提示补充缺失必填要素 | 用户补充后重新执行需求分析 |

---

## 附录：实施核对清单

### A. 工具与后端端点阶段（对应主方案阶段2）
- [ ] 13 个工具后端路由全部实现 + 7 个新适配端点（AppStoreV16Controller `/api/v1/appstore/*`：ops/root-cause、ops/work-orders、shelf-compliance、validate-nested、explain、report/download、script/download），含工具13 query_approval_status
- [ ] 后端端点与 2.6 节工具→代码节点/后端端点映射逐条核对通过
- [ ] 裸报文契约自测通过（2.4 节自测项 #8；本地 mock 回显断言，V2.7 替代原 tcpCont 拼装自测）
- [ ] 实时稽核接口完成联通测试（工具2 realtime_spec_audit）
- [ ] save_product_config 幂等与合法性校验验证通过（#6/#7；V2.2 确认门禁已移除）
- [ ] submit_release_approval 审批发起校验（四环节硬校验拒绝）验证通过（工具9）
- [ ] 节点结果存储读写一致（#9；服务重启后可查询）
- [ ] 每个必填入参 PARAM_MISSING 提示验证（#10；插件/结束节点判定）
- [ ] NodeResultService 持久化联通（pd_ai_node_results 表，H2/MySQL DDL 已执行；硬校验 5002/四环节门禁/幂等生效）

### B. 工作流配置（JSON/代码节点）阶段（对应主方案阶段4）
- [ ] `gen_workflows_v2.py` 重建后 11 个子工作流 JSON 全量校验（边/节点编号正确，含 wf_sub_01 节点106~113、wf_sub_04 节点315、wf_sub_06/07/09/10 关键代码节点），全部 ALL_OK
- [ ] wf_sub_00/wf_sub_01 链路验证：需求要素提取 → 相似产品查询（节点105）→ CODE_GET_TEMPLATE（节点106）→ 要素提取 → CODE_VALIDATE_ELEMENTS（节点108 质量闸）→ CODE_MERGE_NESTED（节点109）→ CODE_OP_VALIDATE_NESTED（节点110 本体校验闸 /validate-nested + /explain）→ CODE_RENDER_TABLE（节点111）→ 待补充判定 → 保存 requirement → 双出口；**有待补充项时不产出 req_id、确认无效**；需求工单审批 requirement 轨已生效
- [ ] 执行主干 wf_sub_02→03→05→04 链路验证：每环节"自查(query_node_result + CODE_EXTRACT_RECORD) → 调用插件端点 → 判定 → 存储 → 打印"通用模式逐条验证；plan_json 原样透传；环节2/3/4 自查 config 取参无断链
- [ ] CODE_POLL_PROGRESS 验证：间隔 5s、最多 360 次、连续 5 次失败终止（E12）、超时（E13）、fail_reason 出参非空
- [ ] CODE_MAP_FIXED_CASES（节点315）验证：31 条固定用例（ACC-001~012/BILL-001~010/CUST-001~009）确定性构建；九章节正式版报告结构完整；受理验证独立成节；E26 被测一致性核对生效
- [ ] wf_sub_06 串行自查验证：config→spec→fee→test→requirement 顺序执行（非并行）；CODE_SUMMARY_APPROVAL 合成；报告 7 章节完整；node=report 落库；CODE_APPROVAL_POLL 审批轮询与自动上线、CODE_DOWNLOAD_LAUNCH_SCRIPT（/script/download）生效
- [ ] wf_sub_07/wf_sub_08/wf_sub_09/wf_sub_10 单独调试通过（监控根因+工单 CODE_OP_ROOT_CAUSE/CODE_OP_CREATE_WO、审批进度、存量查询 CODE_OP_QUERY_OFFER、存量合规 CODE_OP_SHELF_COMPLIANCE）
- [ ] 智能体语义识别直调联调：按 3.2 提示词【意图→工作流映射表】命中意图、确认语义识别（V2.2：无需写 CONFIRMED 标记）、串行执行、每环节结果打印、异常中断引导、续跑映射
- [ ] 工具层校验联调（V2.2 修订）：save_product_config 确认门禁已移除（confirmed 任意值可落地+幂等）；四环节不全调 submit_release_approval 被拒绝
- [ ] 知识库按需读取验证：K4 仅读单文件；knowledge/ 迁移后引用路径正确（原 references/、skills/ 已废弃）
- [ ] 3.5 节子工作流级用例 #1~#22 全部通过

### C. 知识库阶段（对应主方案阶段3）
- [ ] knowledge/ 下 5 个目录（K1规范/K2资费/K3测试/K4存量/K5FAQ）+ K5存量报文 + ontology-fields + seed_offer_groups + 存量产品目录_清洗后 就位
- [ ] 《产品信息.txt》18 个销售品按 4.2.3 拆分为单文件入库（knowledge/K4存量/）
- [ ] 文件名与 4.2.2 命名规范一致（K4 按销售品 ID 可精确定位）
- [ ] 固定问答回归 3 组通过

### D. 集成与验收（对应主方案阶段5~6）
- [ ] 第 5 章部署清单 12 项逐项勾选（重点：BASE_URL=http://10.86.13.201:31281 指向正确、11 个子工作流 JSON 齐全、knowledge/ 迁移完成、后端 13 工具 + 7 新适配端点联通）
- [ ] 主方案 8.2 验收指标全量核验（重点：确认语义识别 0% 违例、执行主干串行连续性、异常处置完整性、续跑正确性、查询可用性、待补充规则符合率 100%、受理验证结论完整性、固定用例报告完整性）
- [ ] 端到端演示剧本彩排（含反向分支与异常引导分支）

### E. 模拟数据 18 销售品兼容性核对（V1.6 新增，对应 2.5 节与 3.5 用例 #19~#21）
《产品信息.txt》全部 18 个销售品逐一通过正向全流程验证（勾选对应销售品）：

| # | 销售品 ID | 销售品名称 | 系列 | 模拟接口兼容 | 端到端通过 |
| --- | --- | --- | --- | --- | --- |
| 1 | 900102308 | 5G-A套餐199元 | 5G-A | [ ] | [ ] |
| 2 | 900113043 | 5G-A单品239元 | 5G-A | [ ] | [ ] |
| 3 | 900113046 | 5G-A融合199元 | 5G-A | [ ] | [ ] |
| 4 | 900102307 | 5G-A融合299元 | 5G-A | [ ] | [ ] |
| 5 | 900102313 | 5G-A融合399元 | 5G-A | [ ] | [ ] |
| 6 | 900113044 | 5G-A融合239元 | 5G-A | [ ] | [ ] |
| 7 | 900102310 | 5G-A单品299元 | 5G-A | [ ] | [ ] |
| 8 | 900102312 | 5G-A单品399元 | 5G-A | [ ] | [ ] |
| 9 | 900113045 | 5G-A单品199元 | 5G-A | [ ] | [ ] |
| 10 | 900102306 | 5G-A融合199元 | 5G-A | [ ] | [ ] |
| 11 | 900117022 | 权益随心选·娱乐版19.9元 | 权益随心选 | [ ] | [ ] |
| 12 | 900117020 | 权益随心选·娱乐版29.9元 | 权益随心选 | [ ] | [ ] |
| 13 | 900117027 | 权益随心选·生活版19.9元 | 权益随心选 | [ ] | [ ] |
| 14 | 900117026 | 权益随心选·生活版29.9元 | 权益随心选 | [ ] | [ ] |
| 15 | 900117023 | 权益随心选·出行版19.9元 | 权益随心选 | [ ] | [ ] |
| 16 | 900117024 | 权益随心选·出行版29.9元 | 权益随心选 | [ ] | [ ] |
| 17 | 900117025 | 权益随心选·商超版19.9元 | 权益随心选 | [ ] | [ ] |
| 18 | 900117021 | 权益随心选·商超版29.9元 | 权益随心选 | [ ] | [ ] |

核对要点：① 工具1 相似度分析能命中对应销售品；② 工具6 presetValue 与该销售品《产品信息.txt》规则值一致；③ 工具2/8 稽核与资费校验按该销售品规则判定；④ 权益随心选系列重点核对权益类字段 A            