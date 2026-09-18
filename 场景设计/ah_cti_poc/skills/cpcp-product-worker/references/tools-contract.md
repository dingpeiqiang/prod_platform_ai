# 工具契约参考（自原细化设计 2.1/2.2/2.3 迁移）
> 工具契约完整清单。脚本 `skills/cpcp-product-worker/scripts/cpcp_api.py` 子命令与本契约一一对应（含本地代码节点类子命令）；
> 接口契约基线：《产销品场景部分能力接口清单.xlsx》；模拟服务基址：`http://10.86.13.201:31281`（环境变量 `CPCP_BASE_URL` 可覆盖）。
> 请求体统一为**裸报文**（业务参数 JSON 直接置于顶层，V2.7 起不再使用 contractRoot/tcpCont 包裹）；出参若为 contractRoot/resultObject 包裹格式，脚本 `_unwrap` 自动解包。

## 公共约定
- 同步类工具单次超时 60s；异步轮询类 30s；**网络异常重试由脚本内置：共尝试 3 次（RETRY=2），耗尽后报 NET_ERROR/TIMEOUT/HTTP_5xx，按 E29 终止并询问用户；业务失败（resultCode≠0）不重试，即按对应异常码中断询问**；
- 错误码：`PARAM_MISSING`（必填缺失）/ `HTTP_<状态码>` / `NET_ERROR` / `TIMEOUT` / `PARSE_ERROR`；
- 模拟结果兼容性：存量种子数据=18 个销售品（5G-A 系列 10 个 + 权益随心选系列 8 个），存量品任一套餐输入均返回与该销售品资费规则一致的结构化结果；**新增产品链路（方案A）：落地时生成全新 offer_id 并按 plan_json 构造独立销售品档案，后续稽核/测试/校准按该档案返回，不再复用存量销售品编码或被种子数据覆盖**；测试预期值 presetValue：存量品取 preset_map，新增品按落地档案自动生成；未收录销售品不返回伪造数据；
- **种子数据匹配边界（E26 依据）**：出参 offerName/offerId 与被测配置不一致仍须在环节4 按被测一致性预校验核对（见 flow-B 环节4 / exception-matrix E26）；
- **V4.0 融合组兼容约定（可缺席回退）**：融合组扩展字段（`offer_group`/`group`/`member_role`/`offer_group_check`/`group_violations`）在单商品链路**全部缺席**，缺席时行为与历史版本一致；融合组成员构成唯一数据源=`similar_offer` 出参 `offer_group`（seed_offer_groups.json 为 POC 抽取来源），模型禁止自行推理成员关系（SKILL.md 纪律9）；
- **大报文传参约定**：>1KB 的 JSON 一律走 `--xxx-file` 会话工件（SKILL.md 纪律8 / flow-B 术语速查），禁止命令行内联。

## 工具1 相似度分析 `similar_offer`
- POST `/api/v1/appstore/similar/offer/query`
- 入参：`businessDesc`(string,必填,≤5000字符，缺少时提示"缺少业务需求描述，请提供需求原文或需求文档摘要")
- 出参：`resultCode`(0/1)、`resultMsg`、`similarOffer`(object，仅相似度最高 1 个，未命中为空对象)
  - `similarOfferId` / `similarOfferName` / `similarityScore`(0~1) / `similarityDesc`
  - `offerInfo`：完整产品配置信息（与需求要素同构：fields 3 模块/9 分类 24 字段数组 + similarOfferId/similarOfferName/series/sub_type 溯源键），后端 toFields24 转换
  - **`offer_group`（V4.0 新增，命中融合品时）**：组结构下发（group_id/main_offer_id/members[]{role,offer_id,required,dependency?,preset?}/group_rules{共享规则/互斥/依赖/退订联动}），成员构成唯一数据源（模型禁止增删成员）；单品命中时无本键
  - **`offerTemplate`（V7.0 新增，评审结论#3，后端 POC 改造项）**：按 6 模板嵌套的相似品实例化报文（结构与 `scripts/templates/<templateId>.schema.json` 同构），模板轨第②步远端兜底源直接使用；**本键缺席时（后端改造未上线）禁止模型手工把 offerInfo 24 字段逆投影为嵌套报文**，按 flow-A 步骤② 降级口径处理
- 错误处理：resultCode=1 时走"无相似产品"分支（E1：**中断询问用户**，回复【继续】降级补全/【修改需求】重提，禁止自动降级）

## 工具2 实时规格稽核 `spec_audit`
- POST `/api/v1/appstore/audit/realtime`
- 入参：`offer_id`(必填,"缺少销售品ID，请先完成配置落地")、`config_json`(必填,"缺少落地配置JSON，请先完成配置落地"；**=环节1 配置原文 plan_json，经 --config-json-file 引用会话工件传入**)、`audit_scene`(选填,spec/fee/all,默认all)
- 出参：`pass`(1/0)、`error_list[]`(item/level=error|warning/desc/suggest；**V4.0 融合组：组级检查项 item 形如 `group:<role>`**)、`audit_summary`、`resultCode`(0/1/NET_ERROR/TIMEOUT)
- 超时 60s；传输层重试脚本内置（共 3 次尝试）；pass=0 → E8 中断；超时/网络耗尽 → E7/E29（保留请求报文供人工重放）

## 工具3 测试发起 `offer_test`
- POST `/api/v1/appstore/test/offer/start`
- 入参：`offerId`(必填，camelCase 特例保持；"缺少销售品ID，请提供被测销售品ID")
- 出参：`resultCode`(0/1)、`resultMsg`、`globalId`（格式 `50+yyyyMMddHHmmss+10位随机数`）
- 30s；resultCode=1 或 globalId 空 → E10 终止（首次失败即中断，业务失败不重试）

## 工具4 查询测试场景 `test_scenes`
- POST `/api/v1/appstore/test/offer/scenes`
- 入参：`globalId`(必填,"缺少测试流水号，请先发起测试")
- 出参：`resultCode`、`testScenes[]`(testSceneId/testSceneName/testSceneNbr=S_O_TC|S_ADD_CARD|S_U_TC|**S_GROUP_BIND|S_ADDON_SUB**(V4.0 融合组)/testSceneDesc/sort)
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
  - **`offer_group_check`（V4.0 新增，融合品时）**：组一致性结果（main_offer_id/members[]{role,offer_id,inst_id?,状态}），环节4"成员组合验证"小节与 E26 组核对唯一数据源；单品时无本键
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
- 出参：`product_id`、`offer_id`（**本次配置落地生成/分配的销售品 ID，V2.8 起必须在环节1 输出中显性回显**（flow-B 环节1"offer_id 显性回显纪律"），并作为环节2/环节4 `--offer-id` 与程序C 汇总的唯一入参来源，禁止省略回显或语义转述）、`save_result`(基础信息/资源配置/营销资源/销售规则 各分类 success/fail 及原因)、`status`=SUCCESS|PARTIAL|FAIL、`script_url`(V2.6 起为**绝对 URL**，后端按 X-Forwarded-Proto/Host 头解析网关前置地址后拼装，可直接点击下载；头缺失时退化为相对路径 `/api/v1/appstore/product/config/script?product_id=Pxxx`，此时脚本层拼接 BASE_URL 前缀)、**`group`（V4.0 新增，组结构 plan_json 时）**：主 offer_id + members[]{role,offer_id,product_id}，环节1"融合成员"回显行唯一数据源；单商品时无本键
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
- 出参：`pass`(1/0)、`risk_list[]`(risk_type/risk_desc/suggest)、`compare_list[]`(V2.6 新增，8 项资费比对明细：project_name=套餐月租/流量赠送量/语音赠送量/短信赠送量/流量超出资费/语音超出资费/短信超出资费/商品有效期、requirement_desc=需求侧值（取落地配置 plan_json 字段原文）、billing_desc=系统侧值（含折算括注如"29元（首月按天折算）"、"长期有效（自动续展）"）、result=一致/不一致；**V4.0 融合组：逐成员生成，每项新增 `member_role` 键**（主卡套餐/宽带/天翼高清/副卡功能费/权益包/其他），单商品时缺省）
- 60s；传输层重试脚本内置（共 3 次尝试）；pass=0 → E9 资费驳回分支；环节3 比对表逐行引用 compare_list（禁止模板自行拼装）；desc 系统性为空 → E27（见 exception-matrix，V4.0 融合组阈值逐成员内计算）

## 工具9 上线审批推送 `submit_approval`（V9.1 审批双轨：需求工单审批 / 上线审批）
- POST `/api/v1/appstore/approval/submit`
- 入参：`req_id`(必填,"缺少执行方案key")、`product_id`(必填,"审批对象ID")、`report_url`(必填,报告全文或链接)、`approval_flow`(选填,standard|urgent,默认standard)、`approval_type`(选填,requirement|launch,默认launch)、`approve_confirmed`(脚本内置固定 true——用户已明确回复"发起审批"后才进入对应流程，此字段为后端硬门禁依据)
- **approval_type 语义（V9.1）**：`requirement`=需求工单审批（flow-A0 需求提报后、需求分析前；POC 阶段审批对象 product_id 传 req_id 需求单号）；`launch`=上线审批（flow-C，四环节全成功且用户明确发起；后端硬校验 req_id 四环节结果齐全）
- 出参：`approval_id`、`approval_type`、`status`(审批中/通过/驳回)、`approval_matrix[]`(审批节点矩阵)；`status=NOT_CONFIRMED` 且无 approval_id → approve_confirmed 未置 true（脚本已内置，正常不应出现；出现即报缺陷）
- `approval_matrix[]` 每项：`node_seq`、`node_name`、`approver`(审批角色)、`status`、`opinion`、`update_time`
- 30s；传输层重试脚本内置（共 3 次尝试，业务失败不重试即 E16/E29 中断）；幂等（同 req_id+approval_type 返回原 approval_id）；**后端硬校验：approve_confirmed=true；launch 另须 req_id 四环节（config/spec/fee/test）结果齐全，缺失拒绝推送**

## 工具10 监控查询 `query_monitor`
- GET `/api/v1/appstore/product/monitor`
- 入参：`product_id`(必填,"缺少产品ID，请提供要查询的销售品")、`date_range`(选填,默认最近1天)、`metric`(选填,order/error/fee/all,默认all)
- 出参：`order_count`(int)、`error_count`(int)、`fee_error_rate`(float)、`alarm_list[]`
- **单品运营可视化 iframe 出口（V8.0；V9.3 强制 iframe 标签+绝对地址，供外部 AI 应用平台嵌入）**：D-2 文本摘要后**必须输出完整 iframe 标签** `<iframe src="http://10.86.13.201:31280/ops-web/product-detail.html?product_id={{product_id}}&name={{offer_name}}" width="800" height="400" title="单品运营可视化"></iframe>`（`src` 用**绝对地址**：部署基址 `http://10.86.13.201:31280` = 前端云部署网关（nginx 容器内 listen 6173）+ 固定路径 `/ops-web/product-detail.html`；**禁止仅输出相对路径或纯 URL**，否则缺失 http/ip/端口 无法 iframe 加载；产物为 `frontend/public/ops-web/` 纯静态页，第3层组装固定拼接，非平台接口；页面兼容 product_id/productId/offer_id/offerId 任一参数名，name/type 可选用于未收录商品回退画像，`name` 取 `dispatcher.py` 出参 `entities.offer_name`、缺失省略；页面渲染由 detail.js 纯代码完成，LLM 仅输出固定 iframe 标签、禁止手写页面 JSON/HTML 载荷）

## 工具11 异常告警 `send_alert`
- POST `/api/v1/appstore/alert/send`
- 入参：`product_id`(必填)、`alarm_level`(必填,high/middle/low,"缺少告警级别")、`content`(必填,"缺少告警内容"，含环节、问题描述、建议)
- 出参：`alert_id`、`status`

## 工具13 审批进度查询 `approval_status`
- GET `/api/v1/appstore/approval/status`
- 入参：`approval_id` 与 `product_id` 至少一个（approval_id 优先；"请提供审批单号或销售品ID，以便查询审批进度"）
- 出参：`approval_id`、`approval_type`(requirement|launch，D-1 据此区分衔接)、`status`(审批中/通过/驳回)、`current_node`、`approver`、`opinion`、`submit_time`、`update_time`、`approval_matrix[]`(审批节点矩阵，结构同工具9；D-1 展示审批矩阵源)
- 模拟行为：审批提交 **10s** 后任一次查询自动流转为 `status=通过`、`opinion=审核通过，同意上架/同意进入需求分析`，`approval_matrix` 全部节点置"已通过"（惰性推进，查询/幂等读取时触发）
- 查无审批单 → E21："未找到该销售品的审批单，请确认是否已发起审批"

## 工具14 字段本体推理 `ontology_reason`
- POST `/api/v1/appstore/ontology/fields`（action=reason 一体推理；**V4.0 新增 action=group_check 组级校验**）
- 入参：`action`=reason、`fields`(24 项字段数组 JSON（3 模块/9 分类，字段名逐字对照 ontology-fields.md 注册表）；**V2.9 起字段项可附 `remark` 键**=用户语义澄清原话，如"11月1日生效=商品发布时间，非生效方式字段")
  - **V4.0 融合组入参**：`fields` 传组结构 `{offer_type, main_offer:{role,fields}, member_offers:[{role,fields}], group_rules}` 时脚本自动逐成员 reason + 组级 group_check，出参新增 `group.group_violations[]`（item/level/desc/suggest，处置规则同 violations）；单商品扁平入参行为不变
- 出参：推理后 `fields_json`（含修正回写与默认值补全，source 改标"本体推理"，build_plan 归一显示为【AI补全】）、`fixed[]`(修正明细，defaulted=0 时为 fixed 动作结构)、`violations[]`；**V2.9 新增**：字段带排他性备注（含"非/不属于/不是…时间/字段/口径/方式/语义/范畴"）时引擎跳过该字段校验（fixed 记 `action=remark_excluded`，不生成 violation），且脚本自动从 fields_json 剔除该字段并在出参 `remark_excluded_fields` 列出字段名——被剔除值不进入执行方案
- 修正能力（按 V3.0 24 字段注册表）：枚举归一（月付/包月→后付费）、渠道同义词映射（营业厅/门店/实体→实体渠道；APP/网厅/线上/电子→电子渠道；直销/客户经理/政企→直销渠道）、套餐档位金额归一（"312元/月"→"312元"）、资源缺单位补全（60G→60GB）、套餐名称去首尾空白（口语名保留，不强制 K1 模板）
- 默认值补全（按注册表）：套餐属性→主资费、计费周期→自然月、套餐有效期→长期有效、到期处理方式→自动续订、适用用户→新老用户均可订购、销售渠道→三者全选、三类资源→无、是否允许办理副卡→允许、套外资费三项→无、新入网生效方式→立即生效、老用户生效方式→次月1日生效、过渡期资费规则→按日（当月实际天数）计扣、套餐变更范围→可变更至中国电信其他在售套餐、变更生效方式→次月1号生效、退订规则→允许退订次月生效、付费方式→后付费、支付方式→账单支付、流量结转规则→结转、断网授权→套外流量使用至600元时暂停上网、套餐编码→系统待生成；**仅套餐档位维持"待补充"**
- **字段名校验**：入参含未注册字段名（如旧口径"产品名称/套餐固定费/渠道类型/销售品状态/优惠条件"）时引擎透传不校验——上游必须按 24 字段模板提取，禁止使用旧字段名

## 工具15/16 节点结果存储查询（平台复用插件直连）
### `save_node_result`
- POST `/api/v1/appstore/result/save`
- 入参：`req_id`(必填,须匹配 `PLAN\d{17}`，非法返回 5002)、`node_name`(必填,requirement/config/spec/fee/test/report，空返回 5003)、`result_json`(≤64KB,超限 5004)、`status`(默认 ok)
- **node_name 语义对齐（九环节）**：节点名与九环节并非一一对应——**需求分析（环节2）的存储节点名固定为 `requirement`**（与需求提报共用该键；同一 req_id 下环节2 首次写入，不触发 5006 覆盖拦截）；config/spec/fee/test 分别对应智能配置/规格稽核/资费校准/自动测试。**模型不得因"环节叫需求分析"就改用 stage2/demand_analysis 等非注册名**（非注册 node_name 会被后端按非法处理）
- **req_id 唯一权威贯穿（V9.4）**：req_id 唯一权威口径=`PLAN + yyyyMMddHHmmss + 3 位随机`（须满足 `PLAN\d{17}`）；自环节1 需求提报生成后贯穿后续全部环节，保存一律沿用同号，**禁止换号 / 拼接非数字后缀**（换号将割裂各环节关联、续跑回放失效）
- 同键（req_id+node_name）覆盖写；requirement 环节同键不同内容拦截（5006）

### `query_node_result`
- GET `/api/v1/appstore/result/query`
- 入参：`req_id`(必填)、`node_name`(选填)、`latest_only`(默认 1)
- 出参：`code`/`msg`/`total`/`list[]`（取 `list[0].result_json` 为结果原文）
- total=0 → 按 E5 处理

## 工具17 执行主干状态机 `run_pipeline`（V5.0 新增，本地脚本）
- 本地代码节点（非平台接口）：`scripts/run_pipeline.py`，内部串行调用 save_product_config → spec_audit → billing_verify → offer_test/test_scenes/poll_test_progress/test_result → download_test_report，并完成工件落盘与 save_node_result 存储
- 入参：`--req-id`(必填)、`--workdir`(必填，会话可写目录)、`--confirmed`(确认门禁，未传即 E3 拒绝)、`--resume`(续跑)、`--fail-node`(STAGE1_CONFIG~STAGE4_TEST)、`--operator`(选填)
- 出参：`resultCode`(0/VALIDATE_FAIL/PARAM_MISSING/NOT_CONFIRMED)、`req_id`、`fail_node`、`e_code`(E5/E6/E8/E9/E10/E11/E12/E13/E20/E26/E29)、`next_action`(APPROVAL_GATE/RESUME/FIX_PLAN/CHECK_PLATFORM)、`nodes[]`（node/status=SUCCESS|FAIL|REPLAYED/result_file/summary）、`messages[]`
- 行为保证：串行顺序、失败即停不重试、已成功环节续跑回放不重复写接口、同 req_id 幂等回放、plan_json 待补充字段门禁（出口A 口径）、E26 被测一致性预校验（offerName 与 plan_json 套餐名称核对）
- 判定字段：config=status(SUCCESS/PARTIAL)、spec/fee=pass、test=全场景 successTestCaseCount==testCaseCount 且受理凭证（orderId/offerInstId）非空

## 工具18 入口调度器 `dispatcher`（V6.0 新增，本地脚本，四层架构第0/1层）
- 本地代码节点（非平台接口）：`scripts/dispatcher.py`，规则优先做意图归类/确认门禁/实体抽取；LLM 仅在 `needs_llm` 时做封闭枚举兜底（禁止自由文本）。
- 入参：`--message`(必填) / `--message-file`(>1KB 消息走文件)、`--session-file`(选填，会话上下文 JSON：req_id/offer_id/product_id/approval_id/offer_name 等)
- 出参：`resultCode`(0/PARAM_MISSING/FILE_ERROR)、`intent`（封闭枚举：ASK_INTENT/REQ_REPORT/CONFIRM_EXEC/RESUME_EXEC/APPROVAL/QUERY_APPROVAL/QUERY_MONITOR/QUERY_OFFER/ACCEPTANCE_PLAYBACK/QNA/REJECT/OUT_OF_SCOPE）、`route`(ASK/A/B/C/D1/D2/D4/QNA/NONE)、`confirmed`(bool，确认门禁)、`resume`(bool)、`needs_llm`(bool，规则未命中时 true)、`llm_prompt`(仅 needs_llm 时的最小兜底 schema)、`entities`(req_id/offer_id/product_id/approval_id/offer_name)、`kb_target`(K1~K5)、`matched_rule`
- **ASK_INTENT（V9.1 新增）**：产品描述（产品/套餐/资费规格）但未表明意图（非问句、无查询/配置/确认词）时返回；route=ASK，SKILL 先澄清"查询 or 配置"，明确【配置】后再次运行 dispatcher 转 REQ_REPORT（触发需求提报），【查询】则走查询类意图；完整产品规格文档同理（product-doc-ask）
- 行为保证：意图封闭枚举（任何输入必归类）；确认语义词表 + 否定词表（REJECT）；实体抽取消息优先、会话兜底；规则未命中 → needs_llm=true 交 LLM 单选归类
- 判定规则：确认门禁由本脚本 `confirmed` 决定（对应 run_pipeline `--confirmed` 与 SKILL.md 纪律3），模型禁止自行判断确认语义

## 工具19 同构键值合并 `merge_fields`（V6.1 新增，本地脚本，**@deprecated V7.0 起退役**）
- 本地代码节点（非平台接口）：`scripts/cpcp_api.py merge_fields`，把「需求要素提取 elements」与「相似产品出参」按 field 名确定性合并，模型不再手工合并（四层架构：确定性合并逻辑代码化）。
- **@deprecated**：flow-A 已整体切换模板轨（V7.0），本命令仅为过渡期向后兼容保留（一个迭代周期后随清理纪律删除），**新需求分析一律走工具22 `merge_nested`**；禁止新增依赖。
- 入参：`--fields-json`(步骤1 产出，扁平 fields 数组或融合组结构) / `--fields-json-file`、`--offer-json`(similar_offer 出参) / `--offer-json-file`
- `--offer-json` 兼容形态：扁平 fields 数组 / `{fields:[...]}` / 已归一组结构 / 原始 `offer_group`(members[].preset 可为字段数组或 {字段:值} 映射)
- 合并规则（逐字段对位）：需求有值→需求值(原始需求)；需求无值且非价格→取 offer 同名字段(AI推理)；皆缺失→留空(步骤4 引擎补全)；**价格类字段(套餐档位/各成员月功能费)禁止从相似产品/跨成员照搬**→需求未提供时留空交引擎维持"待补充"
- 出参：`resultCode`、`fields`（单商品数组）/ `group`（融合组结构：main_offer/member_offers 各自 fields，保留 category）
- 行为保证：出参保留 category（供 build_plan 模块归并，避免 plan_md 模块列变空串）；价格纪律延伸到组级（逐成员独立判定，禁止跨成员照搬）。下游 `build_plan` 内置「字段→分类」注册表（`FIELD_CATEGORY`），即使 `ontology_reason` 剥离开 category 也会按 24 字段名确定性回填，模块/分类列始终正常——无需手工补 category。

## 工具20 产品列表识别校验 `identify_products`（V7.0 新增，本地脚本，flow-A 步骤①后置闸）
- 本地代码节点（非平台接口）：`scripts/cpcp_api.py identify_products`，对 LLM 产品识别出参做**确定性校验**（四层架构：LLM 只做翻译，校验闸代码化）。
- 入参：`--products-json`(LLM 步骤①输出：`{products:[{name, prodType, members[]}], need_summary}`) / `--products-json-file`
- 校验规则：products 必须为非空数组；每项须含非空 `name` 且 `prodType` ∈ 封闭枚举（个人主套餐/宽带主套餐/个人附加资费/宽带附加资费/家庭基础套餐/家庭附加资费）
- 出参：`resultCode`、`valid_products[]`（校验通过项）、`invalid_products[]`（形态/枚举违规项）、`total`
- 错误处理：products 缺失/为空/非数组 → `PARAM_MISSING`；`invalid_products` 非空 → flow-A 按 E30 中断询问（禁止默认路由）
- 行为保证：不产出 templateId（路由由第③步 `get_template` 按相似品 template 字段做，**LLM 不猜模板名**——防模板名幻觉）

## 工具21 模板获取 `get_template`（V7.0 新增，本地脚本，flow-A 步骤③）
- 本地代码节点（非平台接口）：`scripts/cpcp_api.py get_template`，templateId → 模板 schema 全文（纯路由，无 LLM）。
- 入参：`--template`(必填，templateId，如 familyBasePrc；来源=步骤② 相似品 K5 报文包裹层或存量目录 template 字段)
- 出参：`resultCode`、`template`(x-template)、`schema`（模板 schema 全文：properties/enum/x-label/x-show-when/x-required/x-template）
- 错误处理：模板文件不存在 → `PARAM_MISSING`（flow-A 按 E32 中断）
- 行为保证：模板库=`scripts/templates/`（6 schema：personMainPrc 85 叶/broadBandMainPrc 43/personAddPrc 99/broadBandOptSpeedPrc 45/familyBasePrc 93/familyAddPrc 75）；出参 schema 叶子清单供第④步提示词注入（约 1~3K token）

## 工具22 嵌套报文合并 `merge_nested`（V7.0 新增，本地脚本，flow-A 步骤⑤，**替代 @deprecated merge_fields**）
- 本地代码节点（非平台接口）：`scripts/cpcp_api.py merge_nested`（转发同目录 merge_nested.py），把「LLM 第④步提取要素」与「相似产品嵌套报文」按模板 schema JSONPath 对位合并，模型不再手工合并。
- 入参：`--schema-file`(必填，模板 schema 路径) / `--elements-json`(第④步校验后要素) / `--elements-json-file`、`--offer-json`(相似品嵌套报文) / `--offer-json-file`、`--template`(相似品报文 {templateId:{…}} 包裹层解包名)、`--mode`(normal=新需求链路 / legacy=存量实例化，默认 normal)
- 合并规则（逐路径确定性）：schema 为骨架递归 → 需求要素有值→需求值(source=原始需求；legacy 模式=存量提取) → 无值且非价格→取相似品同路径(source=AI补全) → schema default 兜底(source=默认值) → 仍缺留空、必填进 pending_required；占位标记（"待补充"/"系统待生成"）视为空值不参与合并
- **价格禁照搬（normal 模式强制）**：档位/月费/月租/固定费类字段（按 x-label 关键词判别）需求未提供时一律留空，禁止从相似品取值；legacy 模式价格豁免（存量价格是事实数据）
- **枚举全放开为自由文本（V9.2，替代原 enum_violation）**：schema 枚举仅作展示/参考，不做命中校验、不产 enum_violation，提取值任意原文原样入库（含 5G-A 阶梯计费 3元/1GB 等非模板枚举的合法值）
- **必填字段说明覆盖兜底（V9.2）**：必填字段为空时，若需求已在同体系"计费说明型"字段（chargeStandard 超套收费标准等）写入计费自由文本，判为已覆盖、不进 `pending_required`；否则照旧必填进 `pending_required`
- 出参：`resultCode`、`template`、`payload`（嵌套实例化报文）、`_meta`（逐叶子 source 溯源）、`pending_required[]`
- 行为保证：同输入逐字节稳定（幂等）；`payload` 本体是第⑥步 render_table 唯一合法入参（传 merge 全出参会渲染为空）；`pending_required` 非空 → flow-A 出口A（不保存）

## 工具23 分节表格渲染 `render_table`（V7.0 新增，本地脚本，flow-A 步骤⑥）
- 本地代码节点（非平台接口）：`scripts/cpcp_api.py render_table`（转发同目录 render_table.py V3.0），把嵌套报文渲染为**业务人员可读的分节多表**（第3层结果组装，纯模板无 LLM）。
- 入参：`--schema-file`(必填)、`--json-file`(必填，**=工具22 出参 payload 本体（嵌套报文），非 merge 全出参**)、`--meta-file`(选填，工具22 出参 `_meta` 溯源，path→{source})、`--title`(选填，表格标题=套餐名称)
- 渲染形态（V3.0）：概览卡片置顶（资费名称/套餐月费/包含资源）→ 顶层容器=独立小节（"1. 基础信息/2. 发布信息/…"，加粗节标题）→ 小节内二级容器=加粗分组子标题行 → **四列表格（字段名称|字段值|取值来源|备注）**；**纯 x-label 中文，无技术键名、无层级标记列**；仅渲染有值段；必填缺失进文末【待补充字段】
- **取值来源列（V3.0）**：依 `--meta-file` `_meta` 逐叶子 source 确定性映射——`原始需求`→**原始需求提取**、`AI补全`→**复用相似产品**、`本体推理`→**本体推理**、`默认值`→**默认值**；`本体推理`仅标注来源不写值（value 仍以 payload 为准）；无 `_meta` 时该列留空
- 出参：markdown 分节多表文本（stdout；备注列含"满足条件时展示"标注，取值来源独立成列）
- 行为保证：同输入输出逐字节稳定（幂等可回归）；技术字段（templateId/prodId/prodPrcId/pricingId/opType）不出现在业务表格；渲染失败/空输出 → flow-A 按 E32 中断（先核对入参是否误传 merge 全出参）
- **输出形态（消除疑虑）**：本工具出参即 **markdown 表格正文（非 JSON）**，作为环节2 出口正文直接展示即可——**无需再落盘为 JSON 再读**；"文件为空/不是 JSON"属正常（stdout 本来就不是 JSON 文件），只需确认渲染有业务分节即可。

## 工具24 flat24 派生 `derive_flat24`（V7.0 新增，本地脚本，下游过渡兼容层，评审结论#1/#5）
- 本地代码节点（非平台接口）：`scripts/cpcp_api.py derive_flat24`，模板轨嵌套报文 → V3.0 flat24 字段数组**单向投影**（方向仅 v2→flat，flat→v2 有损禁止），供环节2/3 后端按 24 字段校验继续可用（后端零改动）。
- 入参：`--template`(必填，templateId)、`--payload-json`(merge_nested 出参 payload) / `--payload-json-file`、`--mapping-file`(选填，默认 `references/ontology-fields.json` 的 path_to_field 映射表)
- 派生规则（确定性）：嵌套报文扁平 walk → 按 path_to_field 映射表对位 flat24 字段名 → 无对应路径的自动丢弃（如 roleMax）；field/value/source=模板轨派生
- 出参：`resultCode`、`template`、`fields[]`（flat24 字段数组）、`note`
- 行为保证：仅作环节2/3 过渡兼容，模板轨唯一事实源=嵌套报文；派生结果随 plan_json_v2 一并入库（`flat_fields` 键，评审结论#5 双份入库）

## 工具25 嵌套本体校验闸 `validate_nested`（V7.0+ 新增，本地脚本转发 Java，flow-A 步骤⑤.5）
- 本地代码节点（非平台接口）→ 转发 backend-app Java `POST /api/v1/product-ontology/config/validate-nested`；**复用现有 CPCP 本体推理平台（backend-app 端口 6174，TTL + OWLAPI/Openllet/RDF4J/SWRL/SHACL），禁止新造本体或自拍 TTL**。
- 入参：`--template`(必填，templateId)、`--payload-json-file`(必填，=merge_nested 出参 payload 本体工件)、`--similar-offer-file`(选填，相似品嵌套报文，供冲突比对)
- 技术链路（Java 侧已实现，`ProductOntologyService.validateNested`）：normalizeNested 归一层 → `ConfigMessageProjector.fromMessage` 反投影为扁平 lowerCamelCase draft → `TemplateDeriveEngine.derive` 补全 → `TemplateComplianceService.checkCompliance`（含 `ShaclValidationDelegate` R-C06/R-C03/R-C05，引擎优先+Lite 兜底）
- 出参：`resultCode`、`pass`（bool，门禁判定）、`template`、`violations[]`（item 含 ruleId/issueType/issueLevel=HIGH|WARN/field/message/engine）、`defaulted[]`（field/value/fillSource/rule）、`rule_ids[]`、`trace_id`、`can_submit`（==pass）、`explain_hint`
- 处置：`pass=false`（存在 issueLevel=HIGH 或 ruleId=R-C06）→ E33 中断（附违规项与建议）；pass=true（仅 WARN/通过）→ 不阻断，WARN 随输出附【风险提示】；后端未启动/连接失败 → E34 提示型不阻断
- 行为保证：结果逐字节引用不加工；价格字段禁推纪律由上游 merge_nested 保证，本闸不另补价

## 工具25a 存量批量合规扫描 `shelf_compliance`（V9.2 新增，本地脚本转发 Java，存量产品本体规则合规）
- 本地代码节点（非平台接口）→ 转发 backend-app Java `POST /api/v1/product-ontology/config/shelf-compliance`；**存量产品（在架 shelfOfferings）同样必须满足本体规则（R-C*，含 R-C04 附加资费依赖缺失）**，逐一映射为合规草稿并校验，输出违规清单供批量整改。
- 入参：`--offering-ids`(选填，逗号分隔商品编码；**不传=扫描全部存量**，传=仅扫指定编码)
- 出参：`success`、`total`、`passedCount`、`failedCount`、`items[]`（每条含 offeringId/offeringName/offeringType/pass/violations[]（含 ruleId/issueLevel/field/message）/ruleIds[]/resultCode）
- 处置：扫描结果逐字节引用；failedCount>0 或存在存量品 R-C04 违规 → 提示运营按违规清单整改（补 dependOn/sourceOfferRef 或确认独立订购口径），禁止静默忽略存量违规；后端不可用 → E34 提示型（不阻断）
- 行为保证：新增 @ 后端 `ProductOntologyService.auditShelfCompliance`，复用既存 `shelfOfferingToDraft` + `checkCompliance`，不新造规则；纯只读扫描，不改写存量数据

## 工具26 推理可见性 `explain_nested`（V7.0+ 新增，本地脚本转发 Java，flow-A 步骤⑤.5）
- 本地代码节点（非平台接口）→ 转发 backend-app Java，**复用既有 `/config/explain` 与 `/config/provenance/{field}`（PROV-O），Python 侧重造 reason_trace**。
- 入参：`--trace-id`(必填，validate_nested 出参)、`--audience`(选填，engineer/sales)、`--field`(选填，字段路径；传值→GET `/config/provenance/{field}`，缺省→POST `/config/explain`)
- 出参：`resultCode`、`explanation`/`provenance`（如何得出 + 依据规则/字段来源）、`used_rules[]`
- **调用时机（V9.2 自动显示）**：flow-A 步骤⑤.5 `validate_nested` 无阻断项后**自动调用**一次（`--audience sales`，POST /config/explain），渲染【推理依据】小节随环节2 输出——**无需用户追问**；用户追问"字段来源/为什么这么判"时再叠加 `--field <字段路径>`（GET /config/provenance/{field}）补字段级溯源
- 错误处理：取数失败/服务不可用 → E34 提示型（不中断主干，输出"推理依据暂不可用"）

## 工具27 异动根因推理 `ops_root_cause`（V8.1 新增，本地脚本转发 Java，flow-D 支线D-2 闭环）
- 本地代码节点（非平台接口）→ 转发 backend-app Java `POST /api/v1/product-ontology/ops/root-cause`；**复用 CPCP 本体推理平台（`product-ops.ttl` 产商品运营归因与风险本体 + `ops_rules.json` R-A01~A06 + Openllet SWRL），禁止新造本体/自拍 TTL**。
- 入参：`--product-id`/`--offering-id`（二选一，商品 ID）、`--text`（选填，异动描述，供文本归因；至少一个）
- 出参（推理链完整字段，D-2 第6步② 逐字引用）：`success`、`offeringId`、`offeringName`、`anomalies[]`（R-A01 异动确认：metricCode/metricValue/metricDelta/message/ruleId）、`candidates[]`、`paths[]`（R-A02~A05 归因排名，topN：rank/rootCauseType/name/weight/ruleId/evidence[]/path[]/isPrimary）、`evidenceTriples[]`（本体 {s,p,o} 证据三元组）、`entityNames{}`、`reportEvidence{}`、`market{}`、`graphScope{}`、`appliedRules[]`、`opsRulesVersion`、`reasonEngine`（openllet-swrl | java-rules | openllet-swrl+java-fallback）、`swrlFiredRules[]`（命中 SWRL 规则，幂等）、`swrlMessage`、`actionList[]`（优化建议，规则/图数据驱动）、`workOrder.draft`、`metricFacts{}`（可选）、`snapshotAt`；`paths` 为空 → 出参 `message`"已确认异动但未命中归因规则"
- 处置：接口失败/后端不可用 → E34 提示型（不阻断，输出"根因推理暂不可用"）；出参逐字引用不加工，`swrlFiredRules`/`paths`/`evidenceTriples` 即**推理链**本体

## 工具28 创建处置工单 `create_work_order`（V8.1 新增，本地脚本转发 Java，flow-D 支线D-2 闭环）
- 本地代码节点（非平台接口）→ 转发 backend-app Java `POST /api/v1/product-ontology/ops/work-orders`
- 入参：`--product-id`/`--offering-id`（必填）、`--source`（选填，默认 ops_assistant）、`--session-id`（选填，会话）、`--title`（选填，缺省=商品名+优化工单）、`--summary`（选填，异动+根因摘要）、`--actions`（选填，JSON 数组=actionList 优化动作）、`--root-causes`（选填，JSON 数组=paths 根因）、`--offering-name`（选填）
- 出参：`success`、`message`、`workOrder`（含 `workOrderId` WO 开头/`title`/`offeringId`/`offeringName`/`summary`/`actions[]`/`status=open`/`source`/`sessionId`）、`persisted`；后端初始化中 → `success=false` 服务稍后重试
- 行为保证：rule/data 驱动，工单即闭环落点；失败 → E34 提示型（不阻断根因结论）

## 工具29 查询处置工单 `query_work_order`（V8.1 新增，本地脚本转发 Java，flow-D 支线D-2 回检）
- 本地代码节点（非平台接口）→ 转发 backend-app Java `GET /api/v1/product-ontology/ops/work-orders`
- 入参：`--status`（选填，open/in_progress/done/cancelled）、`--session-id`、`--q`（关键词：工单号/标题/商品名/商品编码）、`--page`、`--size`（分页，page 从 1 起，size 缺省 20）
- 出参：`success`、`items[]`（workOrder 字段）+ 分页 `total/page/size`（有分页参数时）
- 错误处理：取数失败 → E34 提示型

## 工具30 工单状态流转 `update_work_order`（V8.1 新增，本地脚本转发 Java，flow-D 支线D-2 回检）
- 本地代码节点（非平台接口）→ 转发 backend-app Java `PUT /api/v1/product-ontology/ops/work-orders/{workOrderId}`
- 入参：`--work-order-id`(必填)、`--status`(必填，枚举 open/in_progress/done/cancelled)、`--remark`(选填，回检备注)
- 出参：`success`、`workOrder`（含更新后 status）
- 行为保证：闭环状态机 open → in_progress → done/cancelled；失败 → E34 提示型

## 工具31 存量产品信息查询 `query_offer`（flow-D 支线D-4 与 flow-A 步骤②，本地只读）
- 本地代码节点（非平台接口）：`scripts/cpcp_api.py query_offer`，读取 skill 包内置存量目录 `references/存量产品目录_清洗后.json`（**随 skill 打包内置在 `references/` 下，脚本已按 skill 相对路径正确解析，模型禁止自行重寻该 JSON**，CTRL 见 flow-A 步骤②）+ `references/K4存量/`（K4 存量销售品资料库，按产品 ID 单文件）做确定性检索，纯只读不改写；LLM 不参与检索判定。
- 入参：`--product-id`（9 位编码）/ `--name`（产品名称）/ `--keyword`（描述关键词），三者至少一个（缺少 → PARAM_MISSING："缺少查询入参，请提供 --product-id 或 --name/--keyword"）。
- 出参：`resultCode`(0)、`matched[]`（逐条含 `offer_id`/`name`/`product_type`/`biz_series`/`tier`/`template`/`members[]` + `k4_text`（K4 档案原文）+ `k4_path`）；`status=dup` 的目录条目按 `duplicate_of` 自动透传到 active 品；目录未收录但 K4 档案存在时按 ID 直接读档；未命中 → `matched=[]`。
- **用途（两处，均只读）**：
  - **flow-A 步骤② 相似产品本地主源**：`--keyword` 命中后由该脚本确定性返回相似品候选，步骤② 再按 product_type/tier 最接近/members 取 top1 并挂接 `references/K5存量报文/<offer_id>.json` 为相似品报文（不做任何写入）；
  - **flow-D 支线D-4 存量产品信息查询**。
- 处置：命中 → 按出参结构化回显存量产品信息（逐字引用，禁止编造字段）；`matched=[]` → 追问核对名称/ID或引导"存量合规扫描"查看全部；只读查询不生成 req_id、不推进需求分析/配置流水线状态（D-4 仅查询；flow-A 步骤② 仅作相似品检索参照，流程推进仍由后续步骤完成）。


