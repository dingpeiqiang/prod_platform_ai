# 工具契约参考（自原细化设计 2.1/2.2/2.3 迁移）
> 工具契约完整清单。脚本 `skills/cpcp-product-worker/scripts/cpcp_api.py` 子命令与本契约一一对应（含本地代码节点类子命令）；
> 接口契约基线：《产销品场景部分能力接口清单.xlsx》；模拟服务基址：`http://10.86.13.201:31281`（环境变量 `CPCP_BASE_URL` 可覆盖）。
> 请求体统一为**裸报文**（业务参数 JSON 直接置于顶层，V2.7 起不再使用 contractRoot/tcpCont 包裹）；出参若为 contractRoot/resultObject 包裹格式，脚本 `_unwrap` 自动解包。

## 公共约定
- 同步类工具单次超时 60s；异步轮询类 30s；**网络异常重试由脚本内置：共尝试 3 次（RETRY=2），耗尽后报 NET_ERROR/TIMEOUT/HTTP_5xx，按 E29 终止并询问用户；业务失败（resultCode≠0）不重试，即按对应异常码中断询问**；
- 错误码：`PARAM_MISSING`（必填缺失）/ `HTTP_<状态码>` / `NET_ERROR` / `TIMEOUT` / `PARSE_ERROR`；
- 模拟结果兼容性：存量种子数据=18 个销售品（5G-A 系列 10 个 + 权益随心选系列 8 个），存量品任一套餐输入均返回与该销售品资费规则一致的结构化结果；**新增产品链路（方案A）：落地时生成全新 offer_id 并按 plan_json 构造独立销售品档案，后续稽核/测试/校准按该档案返回，不再复用存量销售品编码或被种子数据覆盖**；测试预期值 presetValue：存量品取 preset_map，新增品按落地档案自动生成；未收录销售品不返回伪造数据；
- **种子数据匹配边界（E26 依据）**：出参 offerName/offerId 与被测配置不一致仍须在环节4 按被测一致性预校验核对（见 flow-B 环节4 / exception-matrix E26）；
- **大报文传参约定**：>1KB 的 JSON 一律走 `--xxx-file` 会话工件（SKILL.md 纪律8 / flow-B 术语速查），禁止命令行内联。

## 工具1 相似度分析 `similar_offer`
- POST `/api/v1/appstore/similar/offer/query`
- 入参：`businessDesc`(string,必填,≤5000字符，缺少时提示"缺少业务需求描述，请提供需求原文或需求文档摘要")
- 出参：`resultCode`(0/1)、`resultMsg`、`similarOffer`(object，仅相似度最高 1 个，未命中为空对象)
  - `similarOfferId` / `similarOfferName` / `similarityScore`(0~1) / `similarityDesc`
  - `offerInfo`：完整产品配置信息（同构 fields 四类18字段数组 + similarOfferId/similarOfferName/series/sub_type 溯源键），后端 toFields18 转换
- 错误处理：resultCode=1 时走"无相似产品"分支（E1：**中断询问用户**，回复【继续】降级补全/【修改需求】重提，禁止自动降级）

## 工具2 实时规格稽核 `spec_audit`
- POST `/api/v1/appstore/audit/realtime`
- 入参：`offer_id`(必填,"缺少销售品ID，请先完成配置落地")、`config_json`(必填,"缺少落地配置JSON，请先完成配置落地"；**=环节1 配置原文 plan_json，经 --config-json-file 引用会话工件传入**)、`audit_scene`(选填,spec/fee/all,默认all)
- 出参：`pass`(1/0)、`error_list[]`(item/level=error|warning/desc/suggest)、`audit_summary`、`resultCode`(0/1/NET_ERROR/TIMEOUT)
- 超时 60s；传输层重试脚本内置（共 3 次尝试）；pass=0 → E8 中断；超时/网络耗尽 → E7/E29（保留请求报文供人工重放）

## 工具3 测试发起 `offer_test`
- POST `/api/v1/appstore/test/offer/start`
- 入参：`offerId`(必填，camelCase 特例保持；"缺少销售品ID，请提供被测销售品ID")
- 出参：`resultCode`(0/1)、`resultMsg`、`globalId`（格式 `50+yyyyMMddHHmmss+10位随机数`）
- 30s；resultCode=1 或 globalId 空 → E10 终止（首次失败即中断，业务失败不重试）

## 工具4 查询测试场景 `test_scenes`
- POST `/api/v1/appstore/test/offer/scenes`
- 入参：`globalId`(必填,"缺少测试流水号，请先发起测试")
- 出参：`resultCode`、`testScenes[]`(testSceneId/testSceneName/testSceneNbr=S_O_TC|S_ADD_CARD|S_U_TC/testSceneDesc/sort)
- testScenes=[] → E11 终止："该销售品未匹配到测试场景，请检查销售品配置"

## 工具5 查询测试进度 `test_progress`
- POST `/api/v1/appstore/test/offer/progress`
- 入参：`globalId`(必填)
- 出参：`totalSteps`(场景数+2)、`activeIndex`、`done`(bool)、`failed`(bool)、`failIndex`(无失败=-1)、`totalSceneCount`、`finishedSceneCount`、`failedSceneCount`
- 30s / 不重试（轮询由 poll_test_progress.py 控制）；单次失败不终止，**连续 2 次失败**终止转人工（E12，flow-B 显式传 `--max-consecutive-fail 2`）

## 工具6 查询测试结果 `test_result`
- POST `/api/v1/appstore/test/offer/result`
- 入参：`globalId`(必填，须在 done=true 后查询)
- 出参：`resultCode`/`resultMsg`/`testRequestId`/`testRequestName`/`offerName`/**`orderId`**/**`offerInstId`**(受理验证依据)/**`report_url`**(V2.7 新增，正式版《销售品自动化测试报告》下载链接，绝对 URL 可直接点击下载；头缺失时退化为相对路径 `/api/v1/appstore/test/offer/report?global_id=xxx`，此时脚本层拼接 BASE_URL 前缀)/`testScenes[]`
  - `offerName` 同时用作**被测一致性预校验（E26）**依据：与环节1 出参 offer_id 对应的被测配置套餐名称核对，不一致 → 主干中断（见 flow-B 环节4）；
  - `testScenes[]`：testSceneNbr/Name/Desc、testCaseCount、successTestCaseCount、failTestCaseCount、testCasePointResults[](testPointNbr/presetValue/testValue/resultCode=0一致|1不一致/resultMsg)、objTestSceneRel(resultMsg/summaryDesc/suggestion)
  - **`testCases[]`（V2.9 新增）**：31 条固定用例逐条结论（caseId/caseName/level/result），后端按 K3 规范第4章判定依据确定性生成，是 `map_fixed_cases` 的首选数据源（脚本存在该出参时原样透出，不再本地映射）；result 取值 ✅/❌/本销售品未覆盖
- orderId/offerInstId 为空 → 报告标注"未获取到受理凭证，需人工核实"（E14，不中断）
- **报告归档（V2.7）**：测试完成查询结果时后端按出参原文归档正式版报告 Markdown（完整 9 章节结构，对齐 K3测试_销售品自动化测试报告模板_V2.0.md：12 项基础信息 + 三大验证 31 条固定用例 ACC-001~012/BILL-001~010/CUST-001~009 + P0/P1/P2 分级 + 缺陷清单/风险汇总/整改建议 + 三选一整体上线结论），同 globalId 覆盖刷新；对话输出须附 report_url 下载图标行

## 工具7B 测试报告下载 `download_test_report`
- GET `/api/v1/appstore/test/offer/report`（本地代码节点，非平台插件）
- 入参：`--global-id`(必填,"缺少测试流水号，请先完成自动测试（环节4）并取出参 globalId")、`--save-path`(选填,默认 `./test_report_<globalId>.md`；**默认目录可能只读（E28），建议显式指定会话可写目录绝对路径**)
- 行为：下载报告响应体原样写入本地文件（二进制安全，不按 JSON 解析）
- 出参：`resultCode`(0=成功)、`resultMsg`、`saved_path`(绝对路径)、`file_size`(字节数)
- 错误处理：HTTP 404（报告未归档）→ `HTTP_404`，提示确认测试已完成；网络异常重试脚本内置（共 3 次尝试，耗尽即 E29）；**失败不中断执行主干**（环节4 report_url 链接行仍在，用户可手动下载）；本地路径不可写 → E28（不中断主干）

## 工具7 配置落地 `save_product_config`
- POST `/api/v1/appstore/product/config/save`
- 入参：`req_id`(必填,"缺少执行方案key，请先完成需求分析并确认执行方案")、`plan_json`(必填,存储取回的 JSON 原文原样透传)、`operator`(选填)、`confirmed`(兼容字段，后端仅记录不校验)
- 出参：`product_id`、`offer_id`（**本次配置落地生成/分配的销售品 ID，V2.8 起必须在环节1 输出中显性回显**（flow-B 环节1"offer_id 显性回显纪律"），并作为环节2/环节4 `--offer-id` 与程序C 汇总的唯一入参来源，禁止省略回显或语义转述）、`save_result`(基础信息/资源配置/营销资源/销售规则 各分类 success/fail 及原因)、`status`=SUCCESS|PARTIAL|FAIL、`script_url`(V2.6 起为**绝对 URL**，后端按 X-Forwarded-Proto/Host 头解析网关前置地址后拼装，可直接点击下载；头缺失时退化为相对路径 `/api/v1/appstore/product/config/script?product_id=Pxxx`，此时脚本层拼接 BASE_URL 前缀)
- 60s / **不自动重试**（写操作防重复写入）；后端保留 plan_json 合法性校验（5001）与同 plan_json 幂等（重放时按本次请求头重写 script_url，保证链接始终可用）；确认门禁已移除（V2.2）；落地成功时同步生成 CRM/billing 落库 SQL 脚本（模拟）
- **附带下载路由**：GET `/api/v1/appstore/product/config/script?product_id=Pxxx` → text/plain（附件名 launch_Pxxx.sql），返回后端生成的两段式 SQL（/*run@crm*/ 定价信息段 + /*run@billing*/ 优惠/累计段）；product_id 未落地返回 404

## 工具7A 脚本文件下载 `download_launch_script`
- GET `/api/v1/appstore/product/config/script`（本地代码节点，非平台插件）
- 入参：`--product-id`(必填,"缺少产品ID，请先完成配置落地并取出参 product_id")、`--save-path`(选填,默认 `./launch_<product_id>.sql`；**默认目录可能只读（E28），建议显式指定会话可写目录绝对路径**)
- 行为：下载脚本响应体原样写入本地文件（二进制安全，不按 JSON 解析）
- 出参：`resultCode`(0=成功)、`resultMsg`、`saved_path`(绝对路径)、`file_size`(字节数)
- 错误处理：HTTP 404（product_id 未落地）→ `HTTP_404`，提示确认已落地；网络异常重试脚本内置（共 3 次尝试，耗尽即 E29）；**失败不中断执行主干**（环节1 链接行仍在，用户可手动下载）；本地路径不可写 → E28（不中断主干）

## 工具8 计费规则校验 `billing_verify`
- POST `/api/v1/appstore/billing/rules/verify`
- 入参：`config_json`(必填，**=环节1 配置原文 plan_json，经 --config-json-file 引用会话工件传入**)、`check_scene`(选填,fee/overlay/superposition/all,默认all)
- 出参：`pass`(1/0)、`risk_list[]`(risk_type/risk_desc/suggest)、`compare_list[]`(V2.6 新增，8 项资费比对明细：project_name=套餐月租/流量赠送量/语音赠送量/短信赠送量/流量超出资费/语音超出资费/短信超出资费/商品有效期、requirement_desc=需求侧值（取落地配置 plan_json 字段原文）、billing_desc=系统侧值（含折算括注如"29元（首月按天折算）"、"长期有效（自动续展）"）、result=一致/不一致)
- 60s；传输层重试脚本内置（共 3 次尝试）；pass=0 → E9 资费驳回分支；环节3 比对表逐行引用 compare_list（禁止模板自行拼装）；desc 系统性为空 → E27（见 exception-matrix）

## 工具9 上线审批推送 `submit_approval`
- POST `/api/v1/appstore/approval/submit`
- 入参：`req_id`(必填,"缺少执行方案key，请先完成执行主干")、`product_id`(必填,"缺少产品ID，请先完成配置落地")、`report_url`(必填,报告全文或链接)、`approval_flow`(选填,standard|urgent,默认standard)、`approve_confirmed`(脚本内置固定 true——用户已明确回复"发起审批"后才会进入程序C，此字段为后端硬门禁依据)
- 出参：`approval_id`、`status`(审批中/通过/驳回)；`status=NOT_CONFIRMED` 且无 approval_id → approve_confirmed 未置 true（脚本已内置，正常不应出现；出现即报缺陷）
- 30s；传输层重试脚本内置（共 3 次尝试，业务失败不重试即 E16/E29 中断）；幂等（同 product_id 返回原 approval_id）；**后端硬校验：approve_confirmed=true 且 req_id 四环节（config/spec/fee/test）结果齐全，缺失拒绝推送**

## 工具10 监控查询 `query_monitor`
- GET `/api/v1/appstore/product/monitor`
- 入参：`product_id`(必填,"缺少产品ID，请提供要查询的销售品")、`date_range`(选填,默认最近1天)、`metric`(选填,order/error/fee/all,默认all)
- 出参：`order_count`(int)、`error_count`(int)、`fee_error_rate`(float)、`alarm_list[]`

## 工具11 异常告警 `send_alert`
- POST `/api/v1/appstore/alert/send`
- 入参：`product_id`(必填)、`alarm_level`(必填,high/middle/low,"缺少告警级别")、`content`(必填,"缺少告警内容"，含环节、问题描述、建议)
- 出参：`alert_id`、`status`

## 工具13 审批进度查询 `approval_status`
- GET `/api/v1/appstore/approval/status`
- 入参：`approval_id` 与 `product_id` 至少一个（approval_id 优先；"请提供审批单号或销售品ID，以便查询审批进度"）
- 出参：`approval_id`、`status`(审批中/通过/驳回)、`current_node`、`approver`、`opinion`、`submit_time`、`update_time`
- 模拟行为：审批提交 **10s** 后任一次查询自动流转为 `status=通过`、`current_node=流程结束（上架完成）`、`opinion=审核通过，同意上架`（惰性推进，查询/幂等读取时触发）
- 查无审批单 → E21："未找到该销售品的审批单，请确认是否已发起审批"

## 工具14 字段本体推理 `ontology_reason`
- POST `/api/v1/appstore/ontology/fields`（action=reason 一体推理）
- 入参：`action`=reason、`fields`(18 项字段数组 JSON；**V2.9 起字段项可附 `remark` 键**=用户语义澄清原话，如"11月1日生效=商品发布时间，非生效方式字段")
- 出参：推理后 `fields_json`（含修正回写与默认值补全，source 改标"本体推理"）、`fixed[]`(修正明细，defaulted=0 时为 fixed 动作结构)、`violations[]`；**V2.9 新增**：字段带排他性备注（含"非/不属于/不是…时间/字段/口径/方式/语义/范畴"）时引擎跳过该字段校验（fixed 记 `action=remark_excluded`，不生成 violation），且脚本自动从 fields_json 剔除该字段并在出参 `remark_excluded_fields` 列出字段名——被剔除值不进入执行方案
- 修正能力：枚举归一（月付/包月→按月）、渠道同义词映射（营业厅/门店/实体→实体渠道；APP/网厅/线上/电子→电子渠道；直销/客户经理/政企→直销渠道）、产品名称 K1 模板归一（"5G-A 套餐"→"5G-A单品套餐待定档位元"）、生效日期 yyyyMMdd→yyyy-MM-dd、资源缺单位补全（60G→60GB）、套餐固定费缺周期补全（199元→199元/月）
- 默认值补全：生效日期→立即生效、三类资源→无、适用地区→全国、计费周期→自然月、副卡规则→不允许办理副卡、退订规则→默认口径、销售品状态→待上线；**仅套餐固定费维持"待补充"**

## 工具15/16 节点结果存储查询（平台复用插件直连）
### `save_node_result`
- POST `/api/v1/appstore/result/save`
- 入参：`req_id`(必填,须匹配 `PLAN\d{17}`，非法返回 5002)、`node_name`(必填,requirement/config/spec/fee/test/report，空返回 5003)、`result_json`(≤64KB,超限 5004)、`status`(默认 ok)
- 同键（req_id+node_name）覆盖写；requirement 环节同键不同内容拦截（5006）

### `query_node_result`
- GET `/api/v1/appstore/result/query`
- 入参：`req_id`(必填)、`node_name`(选填)、`latest_only`(默认 1)
- 出参：`code`/`msg`/`total`/`list[]`（取 `list[0].result_json` 为结果原文）
- total=0 → 按 E5 处理
