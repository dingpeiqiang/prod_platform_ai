# 执行主干（智能配置→稽核→资费→测试）—— 程序 B

> 对应原子工作流 wf_sub_02→03→05→04。用户确认执行后**一次跑完四环节，中途不停顿**；仅环节失败时中断。四个环节共用 req_id；每环节成功后存储（node_name=config/spec/fee/test）并立即打印结果。

## 触发条件
- 用户对执行方案回复确认类语句（确认执行/同意/可以/执行吧等）；
- 或【重新执行】：按 fail_node 从失败环节续跑（STAGE1_CONFIG→环节1、STAGE2_AUDIT→环节2、STAGE3_FEE→环节3、STAGE4_TEST→环节4），已成功环节凭存储记录回放、**不重复调用写接口**。

## 前置检查
- `req_id`（必填）：执行方案存储键，以会话最近一次值为准，禁止重新生成；新会话未知时先运行 `python scripts/cpcp_api.py query_node_result` 检索，检索不到提示用户重新提报需求；
- 未识别到用户确认语义时**不得进入本程序**（防跳步由 SKILL.md 纪律保证，后端不重复校验）。

## 通用步骤模式（四环节共用，先读一遍）
1. **自查上游**：`python scripts/cpcp_api.py query_node_result --req-id "<req_id>" --node <上游node>`，取 `list[0].result_json` 原文（可先用 `extract_record` 子命令提取）；total==0 → E5 中断；
2. **调用本环节接口**（见下）；
3. **判定**：仅依据出参字段（各环节判定字段见下），失败/异常 → 按 `references/exception-matrix.md` 中断并引导【重新执行】/【修改执行方案】；
4. **存储**：`python scripts/cpcp_api.py save_node_result --req-id "<req_id>" --node <本环节名> --result-json "<结果JSON>"`（失败/超时场景也存储，供报告定位与续跑判定）；
5. **打印**（逐字引用出参，不加工）：
```
【环节N/{环节名称}】✅ 执行成功
- 关键数据：{该环节关键输出}
- 已自动进入下一环节……（环节4 时改为"- 执行主干全部完成"）
```

## 环节1：智能配置（唯一生产写入步骤）
```bash
# 自查 requirement → 提取执行方案原文
python scripts/cpcp_api.py query_node_result --req-id "<req_id>" --node requirement
# 落地（plan_json=原文原样透传，禁止任何加工/改写）
python scripts/cpcp_api.py save_product_config --req-id "<req_id>" --plan-json "<原文>" --operator "<操作人，可空>"
```
- 判定：status==SUCCESS 或 PARTIAL → 通过；FAIL → E6 中断（打印失败分类明细）；
- 关键数据：product_id / offer_id / save_result 摘要（PARTIAL 附失败分类明细）；
- 存储 node=config；**失败不自动重试**（写操作防重复写入）。

## 环节2：配置规格稽核（实时，无轮询）
```bash
python scripts/cpcp_api.py spec_audit --offer-id "<环节1 offer_id>" --config-json "<环节1 配置原文>" --audit-scene all
```
- 判定：pass==1 → 通过；pass==0 → E8 中断（打印 error_list 明细 + 整改建议，可联动 send_alert(high)）；
- resultCode 非 0（NET_ERROR/TIMEOUT）→ 重试 1 次仍异常 → E7 中断；
- 关键数据：稽核通过，{audit_summary}；存储 node=spec。

## 环节3：资费校准
```bash
python scripts/cpcp_api.py billing_verify --config-json "<环节1 配置原文>" --check-scene all
```
- 判定：pass==1 → 通过（risk_list 为空说明）；pass==0 → E9 中断（打印风险清单 + 引导修改执行方案）；
- 风险解读（回放/查询场景）：先读 `references/K2资费/K2资费_叠加优惠约束说明_V1.0.md` 作为解释依据，**不得新增风险结论或修改风险等级**；
- 关键数据：资费校准通过，未发现叠加/互斥冲突；存储 node=fee。

## 环节4：自动测试（含受理验证）
```bash
# ① 发起（offerId=环节1 offer_id，camelCase 保持）
python scripts/cpcp_api.py offer_test --offer-id "<offerId>"          # resultCode==0 且 globalId 非空→继续；否则 E10
# ② 场景（记录受理验证覆盖范围 S_O_TC/S_ADD_CARD/S_U_TC；空 → E11 中断）
python scripts/cpcp_api.py test_scenes --global-id "<globalId>"
# ③ 轮询（前台运行，禁止后台执行；模拟服务测试时长约 20s，前台 60s 超时足够）
# 若运行环境禁止长驻前台命令，改用单次查询循环：手动重复执行下方第 2 条命令直至 done=true
python scripts/poll_test_progress.py --global-id "<globalId>"          # done=true→④；failed=true→仍取结果定位原因；连续失败→E12；30分钟超时→E13（保留 globalId）
python scripts/cpcp_api.py test_progress --global-id "<globalId>"     # 备用：单次查询，done 字段为字符串 "true"/"false"
# ④ 结果（须 done=true 后查询）
python scripts/cpcp_api.py test_result --global-id "<globalId>"
```
- 生成《销售品自动测试报告》（只基于输入数据，不虚构测点或结论）：
  1. 测试概要：offerName、globalId、场景/测点/通过/失败统计；
  2. **受理验证结论（强制章节）**：受理凭证 orderId={{orderId}}，offerInstId={{offerInstId}}（为空写"未获取到受理凭证，需人工核实"，E14 不中断）；逐受理场景（S_O_TC/S_ADD_CARD/S_U_TC）通过/失败 + 关键测点（P_EFF_DATE/P_EXP_DATE/P_STATUS/P_ORD_CNT 等）比对结论；
  3. 逐场景明细：仅展开 resultCode=1（不一致）测点（presetValue/testValue/结论），一致测点按场景汇总条数；
  4. AI 总结与建议：引用 objTestSceneRel 的 resultMsg/summaryDesc/suggestion；
  5. 总体结论 test_passed：全部场景全部测点一致（resultCode==0）且受理凭证非空 → 通过；
- 关键数据：场景 N 个、测点 M 个全部一致；受理验证 orderId/offerInstId；存储 node=test。

## 四环节全部成功后：打印汇总并中断等待审批确认
```
【执行主干全部完成】✅ 共4个环节执行成功：
1. 智能配置：product_id={...}，offer_id={...}，四类字段全部写入成功；
2. 配置规格稽核：通过，{audit_summary}；
3. 资费校准：通过，未发现叠加/互斥冲突；
4. 自动测试（含受理验证）：场景 N 个、测点 M 个全部一致；
   受理验证：orderId={...}，offerInstId={...}，各受理场景均通过。
是否发起上线审批？回复【发起审批】将汇总以上结果提交审批流；回复【暂不】可稍后发送"发起审批"继续。
```

## 异常中断输出（任一环节失败时，统一模板）
```
【异常】环节：{异常环节名称}
原因：{引用接口返回原文 resultCode/resultMsg，不得臆测}
明细：{稽核问题清单/资费风险清单/失败测点/超时信息}
建议：{整改建议}
请选择下一步：
① 回复【重新执行】：将自动从失败环节继续（已成功环节不重复执行）
② 回复【修改执行方案】：请说明修改意见，将重新生成执行方案并再次确认
```

## 禁止事项
- 环节1 落地前禁止用大模型重写/优化 plan_json（"分析结果 = 配置结果"）；禁止自动重试写操作；
- 各环节禁止凭语义猜测结论（仅依据 status/pass/test_passed/测点 resultCode 字段）；
- 禁止跳过自查直接编造 offer_id/config_json；续跑时已存在成功存储记录（本环节 node_name）→ 直接回放结果，禁止重复调用写接口；
- 环节4 禁止在 done!=true 时查询结果，禁止省略受理验证结论章节。
