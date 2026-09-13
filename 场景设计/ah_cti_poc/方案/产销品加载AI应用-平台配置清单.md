# 产销品加载 AI 应用 · 平台配置清单

> 平台：AI应用开发（九思大模型 · 低代码智能体平台）
> 版本：V1.7　日期：2026-09-13
> 依据：《产销品加载AI应用开发方案.md》V1.7、《产销品加载AI应用-细化设计方案.md》V1.3
> 用途：平台界面逐项操作与核对（与开发工作清单区分：本清单只含界面配置，不含代码开发）

---

## 1. 插件市场录入（自研插件集 13 个 + 平台复用 2 个）

插件集名称：`产销品加载插件集`；接口协议 http/https；服务地址 `http://10.86.13.201:31281`。
导入文件：`插件\自研插件集V1.6\` 目录下各 `_export.json`（可直接导入，或按下表手工录入）。

| # | 工具名 | 工具代码 | 接口路径 | 方法 | 出参归纳 | 导出文件 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 相似度分析 | `query_similar_offer` | /api/v1/appstore/similar/offer/query | POST | 否 | 工具1_相似度分析_export.json |
| 2 | 实时规格稽核 | `realtime_spec_audit` | /api/v1/appstore/audit/realtime | POST | 是 | 工具2_实时规格稽核_export.json |
| 3 | 销售品测试发起 | `offer_test` | /api/v1/appstore/test/offer/start | POST | 否 | 工具3_销售品测试发起_export.json |
| 4 | 查询测试场景 | `get_test_scenes` | /api/v1/appstore/test/offer/scenes | POST | 是 | 工具4_查询测试场景_export.json |
| 5 | 查询测试进度 | `get_test_progress` | /api/v1/appstore/test/offer/progress | POST | 否 | 工具5_查询测试进度_export.json |
| 6 | 查询测试结果 | `get_test_result` | /api/v1/appstore/test/offer/result | POST | 是 | 工具6_查询测试结果_export.json |
| 7 | 配置落地 | `save_product_config` | /api/v1/appstore/product/config/save | POST | 否 | 工具7_配置落地_export.json |
| 8 | 计费规则校验 | `check_billing_rule` | /api/v1/appstore/billing/rules/verify | POST | 是 | 工具8_计费规则校验_export.json |
| 9 | 上线审批推送 | `submit_release_approval` | /api/v1/appstore/approval/submit | POST | 否 | 工具9_上线审批推送_export.json |
| 10 | 监控查询 | `query_product_monitor` | /api/v1/appstore/product/monitor | GET | 是 | 工具10_监控查询_export.json |
| 11 | 异常告警 | `send_alert` | /api/v1/appstore/alert/send | POST | 否 | 工具11_异常告警_export.json |
| 13 | 审批进度查询 | `query_approval_status` | /api/v1/appstore/approval/status | GET | 是 | 工具13_审批进度查询_export.json |
| 复用 | 节点结果存储 | `save_node_result` | /api/v1/appstore/result/save | POST | 否 | 节点结果存储_export_V1.6.json |
| 复用 | 节点结果查询 | `query_node_result` | /api/v1/appstore/result/query | GET | 是 | 节点结果查询_export_V1.6.json |

**录入核对要点：**
- [ ] 每个必填入参"为空提示"已填写（格式：`缺少{参数中文名}，请{获取方式}`）
- [ ] "是否提参"：上下文提取类选"是"；`config_json`/`plan_json`/`report_url` 等大报文选"否"（工作流变量引用）
- [ ] 工具2/7/9 关键校验点（V1.7 后端硬校验）：工具2 同步返回无文件上传；工具7 校验存储中 plan_id 的 **CONFIRMED 确认标记**（无标记返回 NOT_CONFIRMED）；工具9 校验 **execution_id 四环节（config/spec/fee/test）结果齐全**（缺失即拒绝推送）
- [ ] V1.6 口径：13 个工具全部为**自研模拟实现**（无外部 ApiID），模拟数据兼容《产品信息.txt》18 个销售品；《产销品场景部分能力接口清单.xlsx》仅作契约参考
- [ ] 每个工具在"预览与调试"中自测通过（对应细化设计 2.4 节自测项 #1~#12）

---

## 2. 工作流导入与发布（V1.7：仅 8 个子工作流，主流程弃用归档）

导入文件：`工作流配置\V1.6\` 目录。导入后先替换占位符再发布。
**V1.7 变更：智能体 LLM 按意图映射表直调子工作流，`wf_cpcp_main` 主流程不再导入/发布（JSON 保留归档，作 V1.6 固定编排模式参考）。**

| 工作流 | flowId | 导入文件 | 发布前操作 |
| --- | --- | --- | --- |
| 需求分析 | `wf_sub_01` | wf_sub_01_需求分析.json（9节点/8边） | 检查 LLM 节点提示词（V1.7 18字段+来源判定版）与知识库检索挂载（K1+K4，top_k=3）；LLM 节点4 单出参 plan_output + 代码节点 004a 拆分；结束前存储 node_name=requirement |
| 智能配置 | `wf_sub_02` | wf_sub_02_智能配置.json（7节点/6边） | req_id 单入参自查执行方案（query_node_result，submit_way=get）；确认"读取执行方案→配置落地"之间无大模型节点；**103 落地节点三入参 plan_id/plan_json/confirmed，plan_id 独立传（后端强校验）**；结束前存储 node_name=config |
| 规格稽核 | `wf_sub_03` | wf_sub_03_规格稽核.json（8节点/7边） | req_id 单入参自查 offer_id/config_json；确认 realtime_spec_audit 为同步插件节点；结束前存储 node_name=spec |
| 自动测试 | `wf_sub_04` | wf_sub_04_自动测试.json（11节点/10边） | req_id 单入参自查 offer_id；**0304 轮询为 type=6 代码节点（非循环节点）：inputs 平铺 list、asyncio.sleep(5)×360 次**；结束前存储 node_name=test |
| 资费校准 | `wf_sub_05` | wf_sub_05_资费校准.json（8节点/7边） | req_id 单入参自查 config_json；挂载 K2 资费规则库；结束前存储 node_name=fee |
| 上线审批 | `wf_sub_06` | wf_sub_06_上线审批.json（11节点/10边） | **req_id 单入参；串行自查 5 类环节结果（config/spec/fee/test/需求摘要）→ LLM 生成 7 章节报告 → 存储 node_name=report → 审批推送**；审批推送由后端硬校验 execution_id 四环节结果 |
| 监控运维 | `wf_sub_07` | wf_sub_07_监控运维.json | 配置定时触发（每日，平台定时任务）；异常判定阈值 error_count>0 或 fee_error_rate>0.1；LLM 消息查询默认直调工具10/11，此子流兜底 |
| 审批进度查询 | `wf_sub_08` | wf_sub_08_审批进度查询.json | 轻量子工作流；LLM 消息查询默认直调工具13，此子流兜底 |
| （归档）主工作流 | `wf_cpcp_main` | wf_cpcp_main_产销品加载主流程.json | **不导入、不发布**（V1.7 弃用归档） |

**子工作流关键链路核对（V1.7）：**
- [ ] 各子工作流 req_id 单入参自查链路：开始(req_id) → query_node_result(GET，submit_way=get) → 代码节点提取 → 工具节点
- [ ] 各子工作流结束前"代码节点合成 result_json + save_node_result"存储链路（node_name=config/spec/fee/test/report）
- [ ] wf_sub_04 0304 轮询节点：type=6、inputs 平铺 list、fail_reason 出参非空
- [ ] wf_sub_06 串行自查链式连接（501q1→q2→q3→q4→q5→501s，非星型并行）
- [ ] 智能体直调子工作流出参可被 LLM 读取（status/pass/test_passed 等判定字段完整）

---

## 3. 知识库配置（5 个分类）

| 分类 | 名称 | 文档 | 召回挂载点 |
| --- | --- | --- | --- |
| K1 | 产销品业务规范库 | 产销品管理办法/配置规范/命名规则 | wf_sub_01 节点2/4；智能体对话 |
| K2 | 资费规则库 | 资费模板手册/叠加优惠约束说明 | wf_sub_05 节点3 |
| K3 | 测试规范库 | 测试用例设计规范/测试报告模板 | wf_sub_04 节点6 |
| K4 | 存量销售品资料库 | 《产品信息.txt》**18 个销售品**逐条切片（5G-A 系列 10 个 + 权益随心选系列 8 个） | wf_sub_01 节点4（核心） |
| K5 | FAQ | 产销品加载FAQ | 智能体问答 |

**切片与召回参数：** 切片 512 token（K4 按章节自然边界，单片 ≤800）、重叠 50、混合检索语义权重 0.7、top_k=3（K4）/2（其他）、score 阈值 0.75、引用展示开启。
**K4 切片命名：** 首行元信息 `[销售品ID] [销售品名称] [章节名]`；命名规范 `K4存量_产品信息900102308_V1.0`。
- [ ] K4 确认 18 个销售品文档切片完成（对应主方案 5.1 清单）
- [ ] 固定问答回归 3 组通过（例：查 900102308 套外资费 → 命中阶梯计费描述）

---

## 4. 智能体装配（`cpcp_product_worker` 产销品数字员工）

| # | 装配项 | 配置值 |
| --- | --- | --- |
| 1 | 助手代码/名称 | `cpcp_product_worker` / 产销品数字员工 |
| 2 | 功能介绍 | 主方案 3.1 文案 |
| 3 | 提示词 | 主方案 3.2 全文（角色+技能6条+**意图→子工作流智能调度映射表**+限制；含 V1.4 待补充规则、V1.7 确认标记写入/串行直调子流/异常引导/消息查询技能；**"确认执行"→ 先写 CONFIRMED 确认标记再串行直调 wf_sub_02→03→05→04**） |
| 4 | 插件挂载 | 第 1 节 13 个自研插件工具（复用插件由工作流节点引用，不必挂智能体；**save_node_result/query_node_result 须挂载智能体**，供 LLM 写确认标记与存储环节结果） |
| 5 | 工作流挂载 | **仅挂载 `wf_sub_01~08` 八个子工作流（V1.7）**；**不挂载主流程 `wf_cpcp_main`（弃用归档）** |
| 6 | 知识库挂载 | K1~K5 五个分类 |
| 7 | 模型配置 | 温度 0.2 / 多轮对话 20 轮 / top_p 0.5 |
| 8 | FAQ 直接返回 | 否（需大模型归纳） |
| 9 | 开场白 | 主方案 3.3 文案 |
| 10 | 引导问题 | ①我要上新一个5G流量套餐，请帮我分析需求并生成执行方案 ②确认执行刚才的产销品加载方案 ③查询一下刚才那个销售品的审批进度 ④查询销售品900102308的运行监控结果 ⑤重新执行失败的环节 |
| 11 | 答案为空提示 | "抱歉，我仅支持产销品加载相关业务，请更换问题或联系管理员。" |
| 12 | 发布范围 | 先草稿调试，验收后"所有人可见" |

---

## 5. 配置完成 Checklist（按顺序勾选）

**阶段A 插件（对应主方案阶段1~2）**
- [ ] 13 个自研插件工具导入/录入并发布（模拟服务 10.86.13.201:31281 连通）
- [ ] 复用插件（节点结果存储/查询）可用性确认；读写一致自测（保存→查询逐字节一致）
- [ ] **后端工具层硬校验就绪（V1.7）**：工具7 CONFIRMED 标记校验（NOT_CONFIRMED）与幂等验证；工具9 四环节结果校验验证
- [ ] 每个必填入参"为空提示"逐项验证

**阶段B 知识库（对应主方案阶段3）**
- [ ] 5 个分类创建；K1~K3/K5 文档上传
- [ ] 《产品信息.txt》18 个销售品按 K4 切片方案入库；固定问答回归 3 组通过

**阶段C 工作流（对应主方案阶段4）**
- [ ] 8 个子工作流导入→替换占位符→单独调试通过（req_id 自查链路 + 内部存储链路逐条验证）
- [ ] **V1.7 LLM 智能调度联调：确认执行→写 CONFIRMED 标记→串行直调 wf_sub_02→03→05→04→每环节打印结果；异常中断引导；续跑映射**
- [ ] **不挂载主流程 wf_cpcp_main（弃用归档，JSON 保留）**

**阶段D 智能体与验收（对应主方案阶段5~6）**
- [ ] 智能体装配 12 项逐项勾选（重点：工作流仅挂 8 个子流、提示词含意图映射表）
- [ ] **确认续跑联调专项：需求分析→输出方案→回复"确认执行"→验证 LLM 写入 CONFIRMED 标记（node_name=CONFIRMED，req_id=plan_id）后串行直调子流，进入智能配置而非重复需求分析**
- [ ] **硬校验联调专项：跳过确认直调 save_product_config 返回 NOT_CONFIRMED；跳过环节直接发起审批被四环节校验拒绝**
- [ ] 细化设计 3.5 节工作流级用例 #1~#21 全部通过（含 #17/#18 硬校验、#19~#21 十八套餐兼容）
- [ ] 按《端到端演示剧本》完成全流程彩排（含反向分支速查表 10 项）
- [ ] 发布上线（草稿→所有人可见），工作流/插件保留可回退版本
