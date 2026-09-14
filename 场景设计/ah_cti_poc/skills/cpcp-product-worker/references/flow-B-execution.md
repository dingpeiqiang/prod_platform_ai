# 执行主干（智能配置→稽核→资费→自动测试含受理验证）—— 程序 B

> 对应原子工作流 wf_sub_02→03→05→04。用户确认配置后**一次跑完四个环节（智能配置→配置规格稽核→资费校准→自动测试），中途不停顿**；仅环节失败时中断。**受理验证是自动测试的子集**（测试平台自动执行受理类场景即完成受理验证，数据源=环节4 测试结果，不新增接口调用、不设独立环节）。各环节共用 req_id；每环节成功后存储（node_name=config/spec/fee/test）并立即打印结果。

## 触发条件
- 用户对执行方案回复确认类语句（**确认配置**/确认执行/同意/可以/执行吧等）；
- 或【重新执行】：按 fail_node 从失败环节续跑（STAGE1_CONFIG→环节1、STAGE2_AUDIT→环节2、STAGE3_FEE→环节3、STAGE4_TEST→环节4），已成功环节凭存储记录回放、**不重复调用写接口**。

## 前置检查
- `req_id`（必填）：执行方案存储键，以会话最近一次值为准，禁止重新生成；新会话未知时先运行 `python scripts/cpcp_api.py query_node_result` 检索，检索不到提示用户重新提报需求；
- 未识别到用户确认语义时**不得进入本程序**（防跳步由 SKILL.md 纪律保证，后端不重复校验）。

## 通用步骤模式（四环节共用，先读一遍）
1. **自查上游**：`python scripts/cpcp_api.py query_node_result --req-id "<req_id>" --node <上游node>`，取 `list[0].result_json` 原文（可先用 `extract_record` 子命令提取）；total==0 → E5 中断；
2. **调用本环节接口**（见下）；
3. **判定**：仅依据出参字段（各环节判定字段见下），失败/异常 → 按 `references/exception-matrix.md` 中断并引导【重新执行】/【修改执行方案】；
4. **存储**：`python scripts/cpcp_api.py save_node_result --req-id "<req_id>" --node <本环节名> --result-json "<结果JSON>"`（失败/超时场景也存储，供报告定位与续跑判定）；
5. **打印**（按各环节模板输出，数据逐字引用出参，不加工）。

## 环节1：销售品智能配置（唯一生产写入步骤）
```bash
# 自查 requirement → 提取执行方案原文
python scripts/cpcp_api.py query_node_result --req-id "<req_id>" --node requirement
# 落地（plan_json=原文原样透传，禁止任何加工/改写）
python scripts/cpcp_api.py save_product_config --req-id "<req_id>" --plan-json "<原文>" --operator "<操作人，可空>"
# 下载上线脚本文件（product_id 取上一步出参；保存路径默认 ./launch_<product_id>.sql）
python scripts/cpcp_api.py download_launch_script --product-id "<product_id>"
```
- 判定：status==SUCCESS 或 PARTIAL → 通过；FAIL → E6 中断（打印失败分类明细）；
- 存储 node=config；**失败不自动重试**（写操作防重复写入）；
- **输出模板（逐字引用出参）**：
```
已根据《{{套餐名称}}加载方案》完成销售品智能配置。

**配置状态：成功**
- 配置单号：{{config_id，取环节出参 product_id/offer_id 或后端配置单号}}
- 配置字段：{{fields 写入项数}}项
- [📄 配置脚本下载]({{script_url}})（点击图标下载，出参缺失时省略本行）
- 脚本文件已下载：{{download_launch_script 出参 saved_path}}（{{出参 file_size}} 字节）

**建议处理：可输入"执行稽核"进入【配置规格稽核】。**
```
- script_url 引用纪律：链接逐字引用出参 `script_url` 原文（后端已返回绝对 URL，直接引用即可；若为相对路径则拼接 BASE_URL 前缀），禁止自行构造或改写参数；**对话输出禁止裸露 URL，统一渲染为 Markdown 下载图标 `[📄 配置脚本下载](script_url)`**；脚本内容为后端生成的 CRM/billing 落库 SQL（模拟），仅在用户要求查看时按下载文件内容原文回显，禁止转述加工；
- 脚本文件下载纪律：download_launch_script 出参 `resultCode=="0"` 时按模板输出"脚本文件已下载"行；非 0（未落地 404/网络异常）时**省略该行、不中断主干**（下载图标行仍在，用户可手动下载）；禁止虚构 saved_path/file_size；
- PARTIAL 时改为：`**配置状态：部分成功**`，附失败分类明细，结尾改为"请根据失败明细说明修改意见，或回复【重新执行】。"（不显示下一环节引导）。

## 环节2：配置规格稽核（实时，无轮询）
```bash
python scripts/cpcp_api.py spec_audit --offer-id "<环节1 offer_id>" --config-json "<环节1 配置原文>" --audit-scene all
```
- 判定：pass==1 → 通过；pass==0 → E8 中断（打印 error_list 明细 + 整改建议，可联动 send_alert(high)）；
- resultCode 非 0（NET_ERROR/TIMEOUT）→ 重试 1 次仍异常 → E7 中断；
- 存储 node=spec；
- **输出模板（✅ 清单七项为固定检查项，通过时逐项输出 ✅；禁止虚构 ✅）**：
```
> **稽核结果：通过**
> - 基础信息完整性：✅
> - 资源配置完整性：✅
> - 套外资费完整性：✅
> - 字段格式规范性：✅
> - 字段枚举合法性：✅
> - 配置项关联一致性：✅
> - 业务规则完整性：✅
>
> 未发现任何异常。

**建议处理：可输入"资费校准"进入【资费校准】。**
```
- 七项检查项与出参 error_list 分类一一对应：仅当出参 error_list 为空（或该分类无告警项）时输出 ✅；某分类存在告警 → 该项改标 ❌ 并在该行下附 error_list 对应明细（item/desc/suggest 逐字引用）；
- pass==0 时整体结论改为"**稽核结果：不通过**"，仅列出 ❌ 项及明细，未告警项不输出，结尾按 E8 引导【重新执行】/【修改执行方案】（不显示下一环节引导）；
- 禁止补 ✅ 凑数：七项清单与出参告警分类核对后方可输出，出参结构不含分类信息时以 error_list 为空=全通过处理。

## 环节3：资费校准（8 项比对）
```bash
python scripts/cpcp_api.py billing_verify --config-json "<环节1 配置原文>" --check-scene all
```
- 判定：pass==1 → 通过（risk_list 为空说明）；pass==0 → E9 中断（打印风险清单 + 引导修改执行方案）；
- 风险解读（回放/查询场景）：先读 `references/K2资费/K2资费_叠加优惠约束说明_V1.0.md` 作为解释依据，**不得新增风险结论或修改风险等级**；
- 存储 node=fee；
- **比对表固定 8 项，逐行引用出参 `compare_list[]`（project_name/requirement_desc/billing_desc/result 四键一一对应）**：套餐月租/流量赠送量/语音赠送量/短信赠送量/流量超出资费/语音超出资费/短信超出资费/商品有效期；出参无对应比对项时该行整体省略，禁止编造系统侧值、禁止在模板里自行拼装折算括注；
- **输出模板**：
```
已完成资费校准及计费规则验证。

**比对信息：**

| 项目 | 套餐描述（需求） | 计费配置描述（系统） | 比对结果 |
| :--- | :--- | :--- | :--- |
| {{project_name}} | {{requirement_desc}} | {{billing_desc（含折算括注，如 29元（首月按天折算））}} | {{result}} |

**校准结论：** {{全部 result==一致→"资费配置与计费规则完全一致"；存在不一致→逐条列出并中断}}

**建议处理：可输入"自动测试"进入【销售品自动测试】。**
```
- 表行数=出参 compare_list 长度（正常 8 行），行序按出参顺序逐行输出，禁止合并行或增删行；
- 存在不一致（result≠一致）或 risk_list 非空 → 整体结论改"**校准结论：存在不一致/风险**"，仅列不一致项与 risk_list 明细，结尾按 E9 引导【修改执行方案】（不显示下一环节引导）。

## 环节4：销售品自动测试（含受理验证）
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
- 判定：test_passed==通过（全部场景全部测点 resultCode==0 且受理凭证非空）→ 通过；否则 E14/E15 中断；
- 存储 node=test；
- **报告生成前置**：按 K3 读取指令生成正式版《销售品自动化测试报告》（9 章节，模板见 `references/K3测试/K3测试_销售品自动化测试报告模板_V2.0.md`，用例判定依据见 `references/K3测试/K3测试_销售品自动化测试用例设计规范_V2.0.md`）；
- **输出模板（对话输出=报告第三章总体结论 + 受理验证摘要；完整 9 章节报告随环节结果输出/供程序C 归档；测试用例清单逐行引用出参场景统计：测试类型=场景名、用例数=该场景 testCaseCount、结果=该场景 successTestCaseCount/failTestCaseCount 判定 ✅/❌；行数=出参场景数，禁止虚构场景行或合并行；受理验证小节逐字引用出参 orderId/offerInstId 与逐受理场景结论，禁止虚构受理凭证）**：
```
已根据销售品配置自动生成并执行测试用例 **{{用例总数（各场景 testCaseCount 合计，逐字引用出参统计）}}条**。

**测试结果：**

| 测试类型 | 用例数 | 结果 |
| :--- | :--- | :--- |
| {{场景名（逐字引用出参 testScenes，下同）}} | {{用例数}} | ✅/❌ |

**三大验证结论（对齐正式版报告模板）：**
- 受理验证（ACC）：{{P0 用例全部 ✅ → "通过"；否则列 ❌ 用例明细}}；
- 计费验证（BILL）：{{compare_list 全部一致且 risk_list 为空 → "通过"；否则列 ❌/风险明细}}；
- 客服验证（CUST）：{{出参可核验项全部 ✅ → "通过"；未覆盖项标注"本销售品未覆盖"}}。

**整体上线结论：** ✅ 建议上线 / ⚠️ 评估风险后上线 / ❌ 禁止上线（判定规则=K3 用例设计规范第6章：P0 全过→建议上线；P0 全过但 P1 ❌ 或 risk_list 非空→评估风险后上线；任一 P0 ❌→禁止上线）。

**受理验证：**
- 受理凭证：orderId：{{orderId}}，offerInstId：{{offerInstId}}（出参为空时写"未获取到受理凭证，需人工核实"）；
- 新用户订购（S_O_TC）：✅/❌
- 副卡加装（S_ADD_CARD）：✅/❌（出参无该场景时省略本行）
- 套餐退订（S_U_TC）：✅/❌
- 关键测点：P_EFF_DATE 生效时间 / P_EXP_DATE 失效时间 / P_STATUS 实例状态 / P_ORD_CNT 订购数量 {{逐场景比对结论， resultCode==0 为 ✅，存在不一致列明细}}

《销售品自动化测试报告》（正式版 9 章节）已生成并归档（缺陷明细/业务风险/整改建议见报告第四~七章）。
- [📄 测试报告下载]({{test_result 出参 report_url}})（点击图标下载，出参缺失时省略本行）
- 报告文件已下载：{{download_test_report 出参 saved_path}}（{{出参 file_size}} 字节）

**建议处理：可输入"上线审批"进入【上线审批】。**
```
- 上表场景行以 `test_result` 出参 `testScenes` 返回的场景清单为准逐行输出（场景名逐字引用，出参未返回的场景行不输出、出参多出的场景行照列，禁止按模板凑行）；
- **报告下载纪律（V2.7）**：test_result 出参 `report_url` 引用纪律与环节1 script_url 一致——链接逐字引用出参原文（后端已返回绝对 URL，直接引用即可；若为相对路径则拼接 BASE_URL 前缀），禁止自行构造或改写参数；**对话输出禁止裸露 URL，统一渲染为 Markdown 下载图标 `[📄 测试报告下载](report_url)`**；报告文件下载：`python scripts/cpcp_api.py download_test_report --global-id "<globalId>"`，出参 `resultCode=="0"` 时按模板输出"报告文件已下载"行；非 0（报告未归档 404/网络异常）时**省略该行、不中断主干**（下载图标行仍在，用户可手动下载）；禁止虚构 saved_path/file_size；
- **三大验证结论必须与 K3 用例设计规范 V2.0 的 31 条固定用例判定映射一致**（ACC-001~012 / BILL-001~010 / CUST-001~009，等级 P0/P1/P2），禁止凭语义猜测、禁止补 ✅ 凑数；无对应出参项的用例标"本销售品未覆盖"（不判 ❌、不计入阻断）；
- **整体上线结论三选一**，判定唯一依据=出参（测点 resultCode / 场景 failTestCaseCount / risk_list / 受理凭证），禁止在"任一 P0 ❌"时输出"建议上线"；
- **正式版 9 章节报告**：章节结构、基础信息 12 项、缺陷清单（第五章）、风险汇总（第六章）、整改建议（第七章）均按 `references/K3测试/K3测试_销售品自动化测试报告模板_V2.0.md` 模板生成，数据逐字引用出参，出参不存在的数据省略或标"本销售品未覆盖"，禁止虚构；
- **受理验证小节=自动测试的子集输出**（非独立环节）：数据源=出参 orderId/offerInstId 与逐受理场景（S_O_TC/S_ADD_CARD/S_U_TC）结论及关键测点比对，逐字引用禁止加工；受理凭证为空时按 E14 固定文案标注，不中断；
- 禁止在此环节后输出"可输入受理验证"引导（受理验证不设触发词、不设独立环节，结果随环节4 输出；用户追问受理验证时直接回放环节4 输出中的受理验证小节）。

## 四环节全部成功后：打印汇总并中断等待审批确认
```
【执行主干全部完成】✅ 共4个环节执行成功：
1. 智能配置：product_id={...}，offer_id={...}，{{fields 写入项数}}项字段全部写入成功；
2. 配置规格稽核：通过（七项检查全部 ✅，未发现任何异常）；
3. 资费校准：通过，未发现叠加/互斥冲突；
4. 自动测试：{{用例总数}}条用例通过率{{通过率}}，三大验证（受理/计费/客服）{{全部通过/存在 ❌ 项}}，整体结论{{建议上线/评估风险后上线/禁止上线}}；受理验证（测试子集）：orderId={...}，offerInstId={...}，各受理场景均通过；测试报告：[📄 测试报告下载]({{report_url}})。
是否发起上线审批？可输入"上线审批"提交审批流；回复【暂不】可稍后发送"上线审批"继续。
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
- 输出模板中的 ✅/用例数/比对结果必须逐字对应出参，禁止补 ✅ 凑数、禁止虚构"配置耗时"等出参不存在的数据（无则省略该行）；
- 禁止跳过自查直接编造 offer_id/config_json；续跑时已存在成功存储记录（本环节 node_name）→ 直接回放结果，禁止重复调用写接口；
- 环节4 禁止在 done!=true 时查询结果，禁止省略受理验证结论章节；禁止在存在 P0 ❌ 时输出"建议上线"结论（判定规则见 K3 用例设计规范 V2.0 第6章）。
