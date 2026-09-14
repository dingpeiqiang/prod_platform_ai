# 产销品加载 AI 应用 · Skills 技能包部署清单

> 平台：Skills 技能包运行时（任意支持 Skills 协议的 Agent 运行时）+ 后端模拟服务
> 版本：V2.0　日期：2026-09-14
> 依据：《产销品加载AI应用开发方案.md》V2.6（1.4 节实现方式说明）、《产销品加载AI应用-细化设计方案.md》V2.0、《skills/Skills技能包实现方案.md》V1.3
> 用途：技能包部署与后端就绪逐项核对（原 V1.8 平台界面配置清单随工作流方式废止，重写为 Skills 部署口径；部署项与细化设计第 5 章部署清单对齐）

---

## 1. 后端服务就绪（零改动，先于技能包部署）

后端模拟服务统一部署于 `http://10.86.13.201:31281`（可用环境变量 `CPCP_BASE_URL` 覆盖）。

| # | 就绪项 | 核对要点 |
| --- | --- | --- |
| 1 | 14 工具模拟路由（自研模拟实现，V1.6 口径） | similar/offer/query、audit/realtime、test/offer/start|scenes|progress|result、product/config/save、rules/verify、approval/submit|status、product/monitor、alert/send、ontology/fields 共 14 条路由连通；模拟种子数据兼容《产品信息.txt》18 个销售品 |
| 2 | 节点结果存储查询（后端通用 API） | POST `/api/v1/appstore/result/save`、GET `/api/v1/appstore/result/query`；NodeResultService（MyBatis-Plus）落库 `pd_ai_node_results` 表（H2/MySQL 双 DDL 已执行），服务重启结果不丢失 |
| 3 | 后端硬校验（V2.2 修订） | req_id 格式校验（PLAN\d{17}，非法返回 5002）；requirement 环节同键不同内容拦截（5006）；工具7 确认门禁**已移除**（confirmed 任意值可落地，保留幂等与 plan_json 合法性校验，NOT_CONFIRMED 不再出现）；工具9 校验 req_id 四环节（config/spec/fee/test）结果齐全，缺失拒绝推送 |
| 4 | 模拟数据工程 | 种子数据集 seed_offers.json（18 销售品全量规则）、preset_map.json（18×10 测点预期值）、演示场景开关（稽核驳回/资费冲突/测点不一致/监控异常可注入）、18 套餐一致性自测脚本全绿 |

**后端连通自测（需模拟服务在线）：**
```bash
cd skills/cpcp-product-worker
python scripts/cpcp_api.py similar_offer --desc "5G-A 单品套餐 月费199元 30G流量"
python scripts/cpcp_api.py query_monitor --product-id 900102308
python scripts/cpcp_api.py query_node_result --req-id PLAN20260913143025087
```
- [ ] 14 个工具对应子命令逐一连通后端（含 PARAM_MISSING / 5002 / 5006 错误码验证）

---

## 2. 技能包安装（`skills/cpcp-product-worker/`）

| # | 部署项 | 配置值 | 核对要点 |
| --- | --- | --- | --- |
| 1 | 技能包注册 | 将 `cpcp-product-worker/` 目录注册到支持 Skills 协议的 Agent 运行时（SKILL.md 为入口） | 目录结构完整：SKILL.md + scripts/（3 份）+ references/（flow-A~D 4 份 + 3 份机制文档 + K1~K5 五目录 26 份） |
| 2 | SKILL.md | 常驻总调度（约 49 行）：角色/目录导航/意图路由表/核心纪律5条/脚本调用约定/开场白 | 确认含确认语义纪律（V2.2：无需写 CONFIRMED 标记）、串行纪律、超范围拒答话术 |
| 3 | 流程文档 | flow-A-requirement / flow-B-execution / flow-C-approval / flow-D-query-ops | 每份 flow 文档"步骤=原节点"与细化设计 3.1 映射索引一致 |
| 4 | 机制文档 | tools-contract.md（14 工具契约）/ exception-matrix.md（E1~E23）/ ontology-fields.md（本体注册表+枚举命名+模板三合一） | 三份文档按需加载，SKILL.md 目录导航可达 |
| 5 | 知识库目录 | K1规范(3)/K2资费(2)/K3测试(2)/K4存量(18)/K5FAQ(1) 共 26 份 | K4 确认 18 个销售品单文件齐全（按销售品 ID 精确定位，禁止全量读取） |
| 6 | 脚本可运行 | cpcp_api.py（17 子命令）/ poll_test_progress.py / test_cpcp_api_local.py | Python 3.8+；`python scripts/cpcp_api.py` 无报错 |
| 7 | 环境变量 | `CPCP_BASE_URL`（默认 `http://10.86.13.201:31281`） | 指向后端模拟服务；替换真实实现仅改此值，技能包零改动 |
| 8 | 模型纪律 | 温度 0.2（严谨输出） | 出参逐字引用不加工；仅依据出参字段（status/pass/test_passed）判成败 |

---

## 3. 部署后自测

### 3.1 本地功能自测（不依赖后端）
```bash
cd skills/cpcp-product-worker
python scripts/test_cpcp_api_local.py
```
- [ ] 8 项断言全部通过（裸报文契约 mock 回显断言、出参解包归一、错误码归一、build_plan 唯一 req_id、extract_record E5 分支、枚举/缺参/64KB 前置校验）

### 3.2 按需加载验证
- [ ] SKILL.md 常驻不膨胀（约 49 行）；命中意图仅加载对应单份 flow 文档；
- [ ] K4 仅按 similarOfferId 读取单文件（禁止全量读取 18 份）；
- [ ] 大报文（plan_json/config_json/fields/report）一律走脚本 `--xxx-file` 文件传参，不经模型上下文中转。

### 3.3 意图路由联调（对齐 SKILL.md 路由表 5 类）
- [ ] 提报/修改需求 → 加载 flow-A（程序A 步骤1~8 链路，含出口A 不保存不产出 req_id）；
- [ ] 确认执行 / 重新执行 → 加载 flow-B（四环节串行环节1→2→3→4，每环节打印结果；V2.2：无需写 CONFIRMED 标记，确认语义由 SKILL.md 核心纪律3 识别）；
- [ ] 发起审批 → 加载 flow-C（仅四环节全成且用户明确确认后）；
- [ ] 查询审批进度 / 运行监控 → 加载 flow-D（轻量支线二选一，缺失参数先追问不编造）；
- [ ] 业务问答 → 按类别直读 K1~K5；超范围 → 拒答话术。

### 3.4 关键链路联调（对齐细化设计 3.5 程序级用例）
- [ ] 正向全流程：需求分析→确认→程序B 四环节串行（不停顿）→成功详情+审批提示→审批单生成；
- [ ] 续跑专项：稽核驳回中断→【重新执行】从失败环节续跑（已成功环节凭存储回放，不重复调用写接口）；
- [ ] 硬校验专项：四环节不全调 `submit_approval` 被后端拒绝；
- [ ] poll_test_progress.py：间隔 5s、最多 360 次、连续 5 次失败终止（退出码2）、超时（退出码3）；
- [ ] 细化设计 3.5 节程序级用例 #1~#21 全部通过（含 #17 确认语义识别、#18 四环节硬校验、#19~#21 十八套餐兼容）。

---

## 4. 部署完成 Checklist（按顺序勾选）

**阶段A 后端就绪（对应原阶段1~2，代码开发见《开发工作清单》）**
- [ ] 14 条路由 + 节点结果存储（save/query）连通自测通过
- [ ] 后端硬校验就绪（5002/5006/四环节门禁/幂等；工具7 确认门禁已按 V2.2 移除并验证）
- [ ] 18 套餐一致性自测脚本全绿；presetValue 抽查（900102308/900117022）与《产品信息.txt》一致

**阶段B 技能包安装（替代原阶段3~5 界面配置）**
- [ ] 技能包目录注册完成，结构完整（第 2 节 8 项逐项核对）
- [ ] `test_cpcp_api_local.py` 7 项自测通过
- [ ] K4 18 个销售品单文件齐全；固定问答回归 3 组通过（例：查 900102308 套外资费 → 命中阶梯计费描述）

**阶段C 联调（对应原阶段4 工作流调试，改为程序级验证）**
- [ ] 意图路由 5 类联调通过（3.3 节逐项勾选）
- [ ] 程序A/B/C/D 链路验证通过（对应原 8 个子工作流导入调试，映射见细化设计 3.1 节）
- [ ] 续跑/硬校验专项通过（3.4 节）

**阶段D 验收（对应原阶段5~6）**
- [ ] 细化设计 3.5 节用例 #1~#21 全部通过
- [ ] 按《端到端演示剧本》完成全流程彩排（含反向分支速查表 10 项）
- [ ] 上线：技能包纳入 git 版本管理；后续替换真实实现仅改 `CPCP_BASE_URL`，SKILL.md/flow 文档/契约文档零改动
