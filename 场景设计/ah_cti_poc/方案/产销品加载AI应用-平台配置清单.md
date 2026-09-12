# 产销品加载 AI 应用 · 平台配置清单

> 平台：AI应用开发（九思大模型 · 低代码智能体平台）
> 版本：V1.6　日期：2026-09-12
> 依据：《产销品加载AI应用开发方案.md》V1.6、《产销品加载AI应用-细化设计方案.md》V1.2
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
- [ ] 工具2/7/9 关键校验点：工具2 同步返回无文件上传；工具7 `confirmed` 二次校验（非 true 返回 NOT_CONFIRMED）；工具9 `approve_confirmed` 校验（未经确认返回 NOT_CONFIRMED）
- [ ] V1.6 口径：13 个工具全部为**自研模拟实现**（无外部 ApiID），模拟数据兼容《产品信息.txt》18 个销售品；《产销品场景部分能力接口清单.xlsx》仅作契约参考
- [ ] 每个工具在"预览与调试"中自测通过（对应细化设计 2.4 节自测项 #1~#12）

---

## 2. 工作流导入与发布（1 主 + 8 子）

导入文件：`工作流配置\V1.6\` 目录。导入后先替换占位符再发布。

| 工作流 | flowId | 导入文件 | 发布前操作 |
| --- | --- | --- | --- |
| 主工作流 | `wf_cpcp_main` | wf_cpcp_main_产销品加载主流程.json | 将 6 个子流节点的 `workFlowId`（REPLACE_WITH_SUB01/02/03/04/05/06_FLOWID）替换为各子工作流实际发布 ID |
| 需求分析 | `wf_sub_01` | wf_sub_01_需求分析.json | 检查 LLM 节点提示词与知识库检索挂载（K1+K4，top_k=3） |
| 智能配置 | `wf_sub_02` | wf_sub_02_智能配置.json | 确认"读取执行方案→配置落地"之间无大模型节点 |
| 规格稽核 | `wf_sub_03` | wf_sub_03_规格稽核.json | 确认 realtime_spec_audit 为同步插件节点 |
| 自动测试 | `wf_sub_04` | wf_sub_04_自动测试.json | 配置循环节点：间隔 5s、超时 30 分钟、连续 5 次查询失败终止 |
| 资费校准 | `wf_sub_05` | wf_sub_05_资费校准.json | 挂载 K2 资费规则库 |
| 上线审批 | `wf_sub_06` | wf_sub_06_上线审批.json | 确认 approve_confirmed 插件层校验 |
| 监控运维 | `wf_sub_07` | wf_sub_07_监控运维.json | 配置定时触发（每日，平台定时任务）；异常判定阈值 error_count>0 或 fee_error_rate>0.1 |
| 审批进度查询 | `wf_sub_08` | wf_sub_08_审批进度查询.json | 轻量子工作流；若智能体直调工具13 可省略挂载 |

**主工作流关键节点核对（V1.5/V1.6）：**
- [ ] 开始节点 8 个入参：requirement_text / requirement_file / plan_id / confirmed / resume_action / fail_node / execution_id / approve_confirmed
- [ ] **两次中断**：① 结束节点A（执行方案确认，输出方案表格+plan_id 后中断）；② 节点14 成功结果详情汇总（提示"是否发起上线审批"后中断）
- [ ] 执行主干串行段（环节1 智能配置→环节2 实时稽核→环节3 资费校准→环节4 自动测试）中途无人工等待点；每环节后接"判定与打印"节点（模板见细化设计 3.1.2）
- [ ] 环节结果存储：各环节后置 `save_node_result` 节点，key=`EXEC{execution_id}_STAGE{n}`（n=1~4）
- [ ] 异常处置节点（异常A）：统一异常出口，按 fail_node（STAGE1_CONFIG/STAGE2_AUDIT/STAGE3_FEE/STAGE4_TEST）打印异常环节+原因+明细+建议，并引导【重新执行】/【修改执行方案】
- [ ] 续跑判定选择器优先级：approve_confirmed==true → 审批分支；revise_plan → 需求分析；retry_from_fail+fail_node → 对应环节（回放已成功环节，不重复调用写接口）
- [ ] 各子工作流单独调试通过后，再串联主工作流调试

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
| 3 | 提示词 | 主方案 3.2 全文（角色+技能6条+限制7条；含 V1.4 待补充规则、V1.5 串行执行/异常引导/消息查询技能） |
| 4 | 插件挂载 | 第 1 节 13 个自研插件工具（复用插件由工作流节点引用，不必挂智能体） |
| 5 | 工作流挂载 | `wf_cpcp_main`（默认入口）+ `wf_sub_01~08` |
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
- [ ] 工具7 二次校验（NOT_CONFIRMED）与幂等验证；工具9 审批校验验证
- [ ] 每个必填入参"为空提示"逐项验证

**阶段B 知识库（对应主方案阶段3）**
- [ ] 5 个分类创建；K1~K3/K5 文档上传
- [ ] 《产品信息.txt》18 个销售品按 K4 切片方案入库；固定问答回归 3 组通过

**阶段C 工作流（对应主方案阶段4）**
- [ ] 8 个子工作流导入→替换占位符→单独调试通过
- [ ] 主工作流串联调试通过（两次中断点、四环节串行连续性、每环节结果打印、异常A、续跑回放）

**阶段D 智能体与验收（对应主方案阶段5~6）**
- [ ] 智能体装配 12 项逐项勾选
- [ ] 细化设计 3.5 节工作流级用例 #1~#19 全部通过（含 #17~#19 十八套餐兼容）
- [ ] 按《端到端演示剧本》完成全流程彩排（含反向分支速查表 10 项）
- [ ] 发布上线（草稿→所有人可见），工作流/插件保留可回退版本
