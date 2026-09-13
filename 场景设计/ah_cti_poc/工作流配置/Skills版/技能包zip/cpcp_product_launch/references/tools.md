# 自研插件工具契约（模拟结果输出，种子数据=《产品信息.txt》18个销售品）

> 基地址：http://10.86.13.201:31281 ；全部工具契约与《自研插件集V1.6》一致（V1.7 增加工具层硬校验），替换真实实现时契约不变。
> 能力1~4 需存储类工具：req_id 规范——执行方案=plan_id（PLAN+yyyyMMdd+3位序号）；执行主干=execution_id（EXE+yyyyMMddHHmmss+2位序号）；node_name 取值：requirement(执行方案)/CONFIRMED(确认标记)/config(智能配置)/spec(稽核)/fee(资费)/test(测试)/report(上线报告)。
> V1.7 硬校验约定：写接口（save_product_config / submit_release_approval）不信任 LLM 传参，以节点结果存储为准做门禁校验——配置落地须先有 CONFIRMED 标记，审批推送须先有四环节结果。

## query_similar_offer
- 接口：POST http://10.86.13.201:31281/api/v1/appstore/similar/offer/query
- 说明：相似度分析：businessDesc(必填,≤5000字符) → resultCode/resultMsg/similarOfferList[similarOfferId,similarOfferName,similarityScore,similarityDesc]

## realtime_spec_audit
- 接口：POST http://10.86.13.201:31281/api/v1/appstore/audit/realtime
- 说明：实时规格稽核：offer_id/config_json(必填),audit_scene(默认all) → pass(1/0)/error_list[item,level,desc,suggest]/audit_summary/resultCode；同步返回，无轮询

## offer_test
- 接口：POST http://10.86.13.201:31281/api/v1/appstore/test/offer/start
- 说明：测试发起：offerId(必填) → resultCode/resultMsg/globalId；异步动作，发起后轮询

## get_test_scenes
- 接口：POST http://10.86.13.201:31281/api/v1/appstore/test/offer/scenes
- 说明：查询测试场景：globalId(必填) → resultCode/testScenes[testSceneId,testSceneName,testSceneNbr(S_O_TC/S_ADD_CARD/S_U_TC),testSceneDesc,sort]

## get_test_progress
- 接口：POST http://10.86.13.201:31281/api/v1/appstore/test/offer/progress
- 说明：查询测试进度：globalId(必填) → totalSteps/activeIndex/done/failed/failIndex/totalSceneCount/finishedSceneCount/failedSceneCount；轮询间隔5s、超时30分钟

## get_test_result
- 接口：POST http://10.86.13.201:31281/api/v1/appstore/test/offer/result
- 说明：查询测试结果：globalId(必填) → resultCode/resultMsg/testRequestId/testRequestName/offerName/orderId/offerInstId/testScenes[testSceneNbr,testCaseCount,successTestCaseCount,failTestCaseCount,testCasePointResults,objTestSceneRel]；orderId/offerInstId为受理验证依据

## save_product_config
- 接口：POST http://10.86.13.201:31281/api/v1/appstore/product/config/save
- 说明：配置落地：plan_id/plan_json(必填,存储JSON原文原样透传)/confirmed(必填,true)/operator → product_id/offer_id/save_result/status(SUCCESS/PARTIAL/FAIL/NOT_CONFIRMED)；双重门禁：confirmed=true 且存储中须存在 plan_id 的 CONFIRMED 确认标记（node_name=CONFIRMED），缺标记返回 NOT_CONFIRMED；同 plan_json 幂等

## check_billing_rule
- 接口：POST http://10.86.13.201:31281/api/v1/appstore/billing/rules/verify
- 说明：计费规则校验：config_json(必填),check_scene(fee/overlay/superposition/all,默认all) → pass(1/0)/risk_list[risk_type,risk_desc,suggest]

## submit_release_approval
- 接口：POST http://10.86.13.201:31281/api/v1/appstore/approval/submit
- 说明：上线审批推送：product_id/report_url/execution_id(必填),approve_confirmed(必填,true),approval_flow(standard/urgent) → approval_id/status；双重门禁：approve_confirmed=true 且存储中须存在 execution_id 的四环节结果（config/spec/fee/test 全部 status=ok），缺任一返回 NOT_CONFIRMED（附缺失环节 reason）；同 product_id 幂等

## query_product_monitor
- 接口：GET http://10.86.13.201:31281/api/v1/appstore/product/monitor
- 说明：监控查询：product_id(必填),date_range(选填),metric(order/error/fee/all) → order_count/error_count/fee_error_rate/alarm_list[alarm_id,alarm_level,content,alarm_time]

## send_alert
- 接口：POST http://10.86.13.201:31281/api/v1/appstore/alert/send
- 说明：异常告警：product_id/alarm_level(high/middle/low)/content(必填) → alert_id/status

## query_approval_status
- 接口：GET http://10.86.13.201:31281/api/v1/appstore/approval/status
- 说明：审批进度查询：approval_id/product_id(至少一个) → approval_id/status(审批中/通过/驳回)/current_node/approver/opinion/submit_time/update_time

## save_node_result
- 接口：POST http://10.86.13.201:31281/api/v1/appstore/result/save
- 说明：节点结果存储：req_id(必填,plan_id或execution_id)/node_name(必填)/result_json(必填)/status → code/msg/record_id；同键覆盖

## query_node_result
- 接口：POST http://10.86.13.201:31281/api/v1/appstore/result/query
- 说明：节点结果查询：req_id(必填),node_name(选填),latest_only(选填) → code/msg/total/list[list[i].result_json]
