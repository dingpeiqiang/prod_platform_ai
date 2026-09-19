# 产销品加载 AI 应用开发方案
> 平台：AI应用开发（九思大模型 · 低代码智能体平台）
> 场景：安徽电信 CPCP 产销品域 · 数字员工（必选场景）
> 版本：V3.0　日期：2026-09-14
> 实现基线（V2.0 重塑起）：**12 个工作流 JSON（`wf_main_intent_意图调度` + `wf_sub_00`~`wf_sub_10`，位于 `场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/`，由 `gen_workflows_v2.py` 生成）+ `knowledge/` 知识库 + 后端 `/api/v1/appstore/*` 适配端点**；第 2~6 章平台工作流描述为业务口径与契约权威定义（见 1.4 节实现方式说明）

> V2.0 重塑履历 | 2026-09-18 | **工作流重塑为 12 工作流 JSON + 知识库 + 后端适配端点（实现载体迁移，业务口径零改动）**：① 废弃 `skills/cpcp-product-worker/` 技能包与 `references/flow-A~D`/`scripts/cpcp_api.py` 脚本子命令，改为 12 个工作流 JSON（`wf_main_intent_意图调度` 主调度 + `wf_sub_00`~`wf_sub_10`），由 `gen_workflows_v2.py` 确定性生成；主调度通过 **CODE_DISPATCHER 确定性意图解析**路由到各子流；② 环节覆盖扩为 11 子流——需求提报（wf_sub_00，含需求工单审批 approval-type=requirement）、需求分析（wf_sub_01 模板轨：要素提取→validate_elements 质量门禁→merge_nested→validate_nested 本体校验闸→render_table→保存 requirement）、智能配置（wf_sub_02 融合组成员回显）、规格稽核（wf_sub_03 组维度）、资费校准（wf_sub_05 成员分组）、自动测试（wf_sub_04 重写：31 条固定用例 ACC/BILL/CUST + P0/P1 + 九章节正式版报告 + CODE_MAP_FIXED_CASES）、上线审批（wf_sub_06 双轨 requirement/launch + 审批通过自动上线+监控运维方案）、监控运维（wf_sub_07 异常分支：ops_root_cause 根因推理 + create_work_order 建工单闭环）、审批进度查询（wf_sub_08 双轨）、存量产品查询（wf_sub_09 只读）、存量合规扫描（wf_sub_10 shelf_compliance）；③ 后端新增 7 个适配端点（AppStoreV16Controller，`/api/v1/appstore/*`）：`/ops/root-cause`、`/ops/work-orders`、`/shelf-compliance`、`/validate-nested`、`/explain`、`/report/download`、`/script/download`，网关 BASE_URL=http://10.86.13.201:31281；④ 确定性逻辑（merge_nested/render_table/validate_elements/get_template/render_requirement_report/map_fixed_cases/extract_record/dispatcher/poll_progress 等）全部内嵌为工作流 type=6 代码节点，取代技能包脚本子命令；⑤ 知识资产（K1~K5 知识库、templates 注册表、ontology-fields.json、seed_offer_groups.json 等）迁至 `knowledge/` 目录（原 `references/`、`skills/` 废弃）；req_id 唯一批次标识全链路贯穿（PLAN+yyyyMMddHHmmss+3 位随机） |
> V3.1 履历 | 2026-09-15 | **similar_offer 异常分支显式覆盖 + 输出结构铁律强化（脚本/后端零改动，裸报文契约实测已对齐）**：① flow-A 步骤2 判定扩充——resultCode 非 0/1（PARAM_MISSING/HTTP_xxx/NET_ERROR/TIMEOUT 等）一律按 E1 降级不中断，resultMsg 原样引用禁止臆造原因（实测空白 desc 时后端返回 PARAM_MISSING 而脚本 exit 0，旧判定只认 0/1 会误入执行分支）；② SKILL.md 纪律1 判定字段补全（环节1=status、环节2/3=pass、环节4=test_passed/测点 resultCode/场景 failTestCaseCount）并追加"禁止自造名词"兜底、纪律5 重写为输出结构铁律（四要素/汇总块必须输出）、纪律6 追加"禁止非模板表述"；③ flow-B 环节1/2/3 输出模板补"建议处理"引导行，文末【执行主干全部完成】汇总块补注"必须输出"；④ K5FAQ 新增 Q13（相似产品查询失败降级说明）；⑤ 细化设计方案同步 V2.7 版本行 |
> V3.0 履历 | 2026-09-14 | **受理验证归并为自动测试子集 + 配置上线脚本下载链接**：① 执行主干由"5 环节"改回"四环节"（智能配置→配置规格稽核→资费校准→自动测试），**受理验证是自动测试的子集**（测试平台自动执行受理类场景即完成受理验证，数据源=环节4 测试结果 orderId/offerInstId+逐受理场景，不新增接口调用、不设触发词、不单列环节）；② 工具7 save_product_config 落地成功时后端生成 CRM/billing 落库 SQL 上线脚本（模拟，两段式 /*run@crm*/+/*run@billing*/），出参新增 `script_url` 下载链接，新增附带下载路由 GET /api/v1/appstore/product/config/script（text/plain，未落地 404）；③ SKILL.md/flow-B/flow-C/细化设计/平台配置/工作清单同步；④ K5FAQ Q12 口径同步 |
> V2.9 履历 | 2026-09-14 | **字段体系全量重构为 3 模块/9 分类/24 字段 + 8 环节输出样例对齐（含后端引擎重写与 flow-A~D 全量改版）**：① 《加载方案》需求分析输出对齐样例五列模块表格（模块/分类/字段名称/字段值/备注），同模块/同分类合并展示，模块列加粗，备注列收敛为两态【原始需求】/【AI补全】（原"AI推理/本体推理"统一改称"AI补全"）；② 字段注册表重构——四类18字段 → 3 模块（基础信息/资源配置/业务规则）×9 分类×24 字段（新增套餐档位/套餐属性/套餐有效期/到期处理方式/适用用户/套外资费标准三项/新入网生效方式/老用户生效方式/过渡期资费规则/套餐变更范围/变更生效方式/付费方式/支付方式/流量结转规则/断网授权；删除优惠条件/优惠期/适用地区/订购限制；产品名称不再强制 K1 模板归一、产品编码默认"系统待生成"、仅套餐档位维持"待补充"）；③ 后端 FieldOntologyService/OfferSeedService 按新注册表重写（correctValue 修正逻辑同步重构），编译通过；④ 触发词对齐样例——"确认执行"→"确认配置"、"发起审批"→"上线审批"、新增"确认上线"（审批通过后触发监控运维方案生成，flow-D 新增支线D-3）；⑤ flow-B 执行主干扩为 5 环节（智能配置→配置规格稽核→资费校准→自动测试→受理验证，受理验证复用测试结果不新增接口）；⑥ flow-C 输出改为上线校验看板（5 项 ✅ 表 + 风险检查 + AI审批建议）；⑦ 各环节结尾固定"建议处理：可输入'××'进入【××】。"引导话术；输出模板纪律新增"✅/统计值必须与出参一一对应，禁止补 ✅ 凑数、禁止虚构出参不存在的数据（如配置耗时）"；⑧ SKILL.md/ontology-fields.md/K5FAQ(V1.1)/平台配置清单(V2.2) 同步；脚本 cpcp_api.py 新增 CATEGORY_MODULE/SOURCE_LABEL 常量并重写 build_plan 五列输出，本地自测 8 项全绿 |
| V2.8 | 2026-09-14 | **基于 599 元需求分析实测复盘的 flow-A/文档级优化（脚本/契约零改动）**：① 产品名称口语化名称违规前置规避——实测中 LLM 把口语名"5G套餐599元"直接合并进 fields，引擎修正正则（仅覆盖"5G-A套餐{N}元/套餐{N}元"形态）无法修正完全脱离模板的口语名，记入 violations 后方案带病入库；ontology-fields.md 产品名称行新增**合并规则**（步骤3 禁止口语名直接合并，须先归一为"5G-A套餐{档位}元"再交引擎修正，口语原文仅保留在 need_summary）；② flow-A 步骤4 新增 **violations 处置分支**——仅产品名称格式类 violation 不中断但步骤8 必须逐字引用并提示用户确认命名，其余字段 violation 一律异常中断；步骤8 出口B 追加【命名规范提示】小节模板与输出纪律（禁止额外生成文档/下载链接、禁止输出内部推理过程，实测中曾自行生成下载文档偏离标准出口）；③ 步骤1 新增**提取自检清单**（月费防漏提/资源值不带修饰语/渠道枚举拆分/日期格式/未提及字段填空串禁止提前填"无"屏蔽引擎补全）；④ 步骤3 显式约束销售品状态一律"待上线"禁止取相似产品"在售"、禁止修饰语混入字段值；⑤ 禁止事项同步扩充 4 条；后端引擎/脚本未改动 |
| V2.7 | 2026-09-14 | **基于 599 元 5G 套餐实测反馈的脚本级修复与文档对齐（业务口径/契约零改动）**：① similar_offer 修复 contractRoot 包裹误判——后端 mock 只解析顶层 businessDesc，包裹后返回 PARAM_MISSING，`contract_root_needed()` 收窄为仅 audit/realtime 包裹；② **ontology_reason 空返回防护**——后端字段本体推理引擎空返回（fields_json 为空数组/空串）时脚本报 **ONTOLOGY_EMPTY**（exit 2），禁止跳过该步骤直接组装方案，`build_plan` 同步拒绝空 fields 组装；异常矩阵新增 **E2b**，6.5.4 节脚本层通用行为同步；③ 产品名称命名模板歧义消除——ontology-fields.md 产品名称行扩写为 5G-A 单品/融合/权益随心选三套模板 + 档位占位规则 + 口语化名称归一约束（实测中"5G-A套餐单品599元"与 K1 模板"待定档位元"写法存在歧义）；④ flow-A-requirement.md 步骤2~5 同步：相似产品未命中判定细化（resultCode=0 但 similarOffer 空也走 E1 不中断分支）、ontology 空返回防护（重试 1 次→仍为空中断）、build_plan 入参防护；⑤ 实测复盘发现的"重复运行需求分析冒充执行"隐患由 SKILL.md 核心纪律3 禁止，未新增脚本级防护；⑥ **全部接口去除 contractRoot 包裹**（同日追加，用户确认）：请求侧统一裸报文（业务参数 JSON 置于顶层），删除脚本 `_tcp_cont`/`_transaction_id`/`contract_root_needed` 及包裹分支，出参侧 `_unwrap` 兼容解包保留；spec_audit 修复 `--config-json-file` 文件传参失效缺陷；本地自测新增裸报文契约断言（7→8 项）；4.2 节通用要求、6.5.4 节、细化设计 2.0.2/2.1/2.3/2.4/2.6 节、技能包方案 4.1/4.2/4.3 节、ontology-fields.md 第 4 节、tools-contract.md 同步 |

## 版本记录
| 版本 | 日期 | 变更说明 |
| --- | --- | --- |
| V2.0（工作流重塑） | 2026-09-18 | **实现载体从「Skills 技能包」重塑为「12 个工作流 JSON + knowledge/ 知识库 + 后端适配端点」**：废弃 `skills/cpcp-product-worker/`（SKILL.md/scripts 子命令/flow-A~D），改为 12 个工作流 JSON（`wf_main_intent_意图调度` 主调度 + `wf_sub_00`~`wf_sub_10`，见 `工作流配置/智能体工作流集V1.6/`，`gen_workflows_v2.py` 生成）；CODE_DISPATCHER 确定性意图解析路由；新增需求提报（工单审批 approval-type=requirement）、存量产品查询（只读）、存量合规扫描（shelf_compliance）、监控运维根因闭环（ops_root_cause+create_work_order）、审批双轨、审批通过自动上线；确定性逻辑内嵌 type=6 代码节点（CODE_MERGE_NESTED/CODE_RENDER_TABLE/CODE_VALIDATE_ELEMENTS/CODE_GET_TEMPLATE/CODE_RENDER_REQ/CODE_MAP_FIXED_CASES/CODE_EXTRACT_RECORD/CODE_DISPATCHER/CODE_POLL_PROGRESS 等）；后端新增 7 个适配端点（/ops/root-cause、/ops/work-orders、/shelf-compliance、/validate-nested、/explain、/report/download、/script/download）；知识资产迁至 `knowledge/`；业务口径/字段体系/接口契约/门禁/异常矩阵零改动（详见顶部 V2.0 重塑履历） |
| V1.2 | 2026-09-12 | ① 需求分析与提报环节重构为「需求分析助手」工作流模式（业务要素→配置字段映射 + AI推理 + 执行方案 JSON 存储）；② 智能配置改为"用户确认执行方案后触发"，直接读取存储 JSON 调用配置落地接口；③ 按《产销品场景部分能力接口清单.xlsx》重构插件清单（相似度分析/智能稽核/智能测试 9 个真实 API 接口）；④ 新增测试验证数据《产品信息.txt》使用说明 |
| V1.3 | 2026-09-12 | ① 执行方案保存/查询复用平台已有「节点结果存储查询插件」，不再自研 save_plan_json/get_plan_json；② 智能稽核改为**实时接口**调用（不经过文件上传，直接按销售品实时发起稽核并同步取回结果）；③ 自动化测试定位为**发起动作**：发起后自动完成测试与受理验证，测试报告内容包含受理验证结论，取消独立"受理验证"环节 |
| V1.4 | 2026-09-12 | 明确待补充规则：仅**价格、资源**两类字段未提供时填"待补充"（禁止推理，由用户在确认环节补充）；**其余缺失字段一律由 LLM 基于相似产品（query_similar_offer 结果 + 存量销售品资料）推理补全**，来源标记"AI补全"（V2.9 前原文为"AI推理"，随两态归一改写） |
| V1.9 | 2026-09-13 | 补全规则修订：① **产品编码不做补全**——编码由智能配置环节落地后生成，需求未提供时字段值填"由智能配置生成"；② **待补充触发条件收紧**——仅当套餐固定费（月租费）未提取到，或流量/语音/短信三类资源一个都未提取到时才判"待补充"（任一类资源已提取到则其余资源字段可AI推理）；req_id 生成改为代码节点系统生成（每次分析重新生成） |
| V2.0 | 2026-09-13 | **字段补全稳定性增强（本体+相似产品组合补全）**：新增知识分类 **K6 字段本体库**（四类18字段的类型/枚举/格式/默认规则/兜底口径定义）；wf_sub_01 节点2/节点4 提示词升级为**两阶段补全**——先取 K6 本体约束字段形态，再按**逐级降级取值链**（原始需求 → 相似产品 → 本体默认值 → 待补充）取值，禁止跳级虚构；知识库分类由 5 个扩展为 6 个（挂载点：wf_sub_01 节点2/节点4） |
| V2.1 | 2026-09-13 | **字段补全改为本体推理引擎实现（K6 知识库文档方案整体替换）**：删除 K6 字段本体库知识分类（知识库恢复 K1~K5 五分类）；四类18字段的枚举/格式/默认值/兜底口径改由**后端字段本体推理引擎**（FieldOntologyService，工具14 field_ontology_reason，POST /api/v1/appstore/ontology/fields，action=validate/complete/ontology）以代码为单一事实源提供；wf_sub_01 节点4后新增「字段本体推理」节点（工具14 **action=reason 一体推理**：本体校验+非法值修正回写（月付/包月→按月等枚举归一）+缺失字段按本体默认值补全+兜底口径置待补充），并改为**串行闭环**——方案输出拆分节点以推理后 fields_json 为准组装 plan_json（引擎兜底真正生效，替代 V2.0 LLM 提示词自觉遵守模式）；节点4 提示词精简为只做取值链前两级（原始需求+相似产品，缺失留空）；插件集 13→14 个工具 |
| V2.2 | 2026-09-13 | **确认门禁移除 + 子工作流取值断链修复**：① 后端 `save_product_config` **完全移除确认门禁**（删除 confirmed==true 校验与存储 CONFIRMED 标记校验及 diagnose 诊断，NodeResultService.existingNodes 一并删除）——确认与否由外层智能体 LLM 识别判断（"确认执行"意图识别后才调度子工作流），后端不再重复校验；联调发现 LLM 跳步/漏写 CONFIRMED 标记导致 NOT_CONFIRMED 误拒，门禁防跳步收益低于误拒成本，防跳步职责回归智能体提示词约定；② wf_sub_02 新增「提取执行方案原文」代码节点（CODE_EXTRACT_RECORD）：query_node_result 出参 list 为记录数组，须提取 list[0].result_json 原文再传 save_product_config（原实现数组整体透传，靠后端下钻兼容，已根除）；③ wf_sub_03/04/05 **取值断链修复**：三子流原引用开始节点不存在的 offer_id/config_json 出参，改为自查链路——query_node_result 按 req_id+node_name=config 读取智能配置环节结果 + 代码节点提取原文，稽核/测试/资费的 offer_id/config_json 全部从 config 环节结果取值；④ **待补充项判定收归引擎单一事实源**：wf_sub_01 方案输出拆分节点 004a 的 pending_fields 改为从本体推理引擎返回的推理后字段数组反查（value=待补充），不再采信节点4 LLM 自判的 pending_fields（双重判定取值不一致时路由歧义）；节点4 提示词同步——pending_fields 固定输出空数组，待补充判定职责移交引擎；⑤ 字段本体推理引擎修正增强：渠道类型多选归一补同义词映射表（营业厅/门店/实体→实体渠道，APP/网厅/线上/电子→电子渠道，直销/客户经理/政企→直销渠道），真实报文"营业厅,APP"可修正为"实体渠道、电子渠道"；reason 修正项补 defaulted=0 统一 fixed 明细动作结构；⑥ **待补充项全部可推理 + 值不符合规则可自动修正**：本体注册表补齐 18 字段默认值（生效日期→立即生效、三类资源→无、适用地区→全国、计费周期→自然月等），推理引擎对 value=待补充 的字段一律按本体默认值推理补全（**仅套餐固定费价格维持待补充**交用户确认）；correctValue 新增格式类修正规则——产品名称 K1 模板归一（"5G-A 套餐"→"5G-A单品套餐待定档位元"，档位占位交确认补充）、生效日期 yyyyMMdd→yyyy-MM-dd、资源类缺单位补全（60G→60GB）；真实报文产品名称修正后 violations 清空；⑦ **来源标注新增"本体推理"**：引擎补全/修正后的字段 source 由"AI推理"改标"本体推理"（原始需求=LLM 需求提取、AI推理=相似产品取值、本体推理=引擎默认值补全/规则修正），字段来源三态扩展为四种；节点4 提示词注明"本体推理"来源由引擎自动标注、LLM 禁止自行标注，结束节点文案补来源说明；⑧ **方案输出拆分重构为推理后单入参**：004a 删除 plan_output 入参（LLM 输出非最新值，保留会造成表格/方案与引擎结果偏差）——唯一数据源=节点31推理后 fields_json，plan_json 改由代码直接组装（req_id/fields/pending_fields 三键），**plan_md 由代码基于推理后数组重新生成四列表格**（与 fields_json 严格一致，不再使用 LLM 自生成表格）；节点4 出参精简为 fields_output（仅 fields 键，plan_output/plan_md/pending_fields 出参全部废弃），LLM 职责收窄为纯取值；6.1 表与节点表同步 |
| V2.3 | 2026-09-13 | **需求分析工作流重构为 7 环节新链路 + 缺值来源口径统一「AI补全」**：① wf_sub_01 按 7 环节重构——环节1 需求理解与要素拆解（LLM 职责收窄为**仅提取要素信息**：18字段未提及项 value 填空字符串、不做完整性判断、不输出 pending_fields，elements_json 增补为固定18项）；环节2 相似产品查询（入参=节点2需求要素摘要 need_summary，整合四类要素关键信息）；环节3 **产品信息整合**（原「字段映射与补全」节点重写职责：将匹配产品（相似度第1）的全量配置信息与要素部分信息整合，输出最终产品信息=18字段完整取值，source 仅"原始需求/AI补全"两种）；环节4 字段本体推理（工具14 reason 不变）；方案输出拆分/待补充项判断/保存/双结束节点保留；② **缺值来源全局口径统一**："AI推理"全部改称"AI补全"（节点2/4 提示词、插件描述、K1/K5 知识库、方案文档、演示剧本），字段存储值不变、兼容存量数据；③ 节点4 新增整合规则三级取值（需求要素有值→原始需求；无值→相似产品取值标 AI补全；两者皆缺失→留空由引擎补全标 AI补全）；6.1 表与节点表同步 |
| V2.4 | 2026-09-13 | **相似产品返回单产品+完整配置信息 + 整合/稽核口径强化**：① 工具1 query_similar_offer 出参由 similarOfferList(array) 改为 **similarOffer(object，仅相似度最高的1个产品)**，并新增 **offerInfo 完整产品配置信息**字段（销售品全量规则：资费/资源/副卡/渠道/订购/退订等，取自《产品信息.txt》种子），后端 OfferSeedService.matchSimilar 元素附 offerInfo + 新增 matchBestSimilar（取 score 第1），OfferSimV16Service.similarOfferQuery 改单对象返回；② 环节3 产品信息整合提示词改写：**以相似产品完整产品配置信息（similarOffer.offerInfo）为基准，整合需求中提取的配置信息**，需求无值字段从 offerInfo 取值；③ 环节4 字段本体推理节点描述强化：**稽核产品配置参数，存在问题自动修正回写**（枚举归一/同义词映射/格式修正）；工具1 插件契约/2.x 接口表/开发工作清单/细化设计 2.1 出参表同步 |
| V2.5 | 2026-09-13 | **offerInfo 与需求要素统一配置结构模板规范（同构键值合并）**：① 后端 OfferSeedService 新增 toFields18——销售品种子（20键英文结构）→ 与需求要素解析同构的 fields 四类18字段数组（field/category/value，字段名与本体注册表一致：产品名称取 offer_name、套餐固定费=monthly_fee+元/月、流量/语音取 in_fee 中文键、副卡规则按 sub_card 允许办理+共享规则拼装、渠道类型顿号拼接 sale_channels 等），工具1 offerInfo 出参改为该同构结构（保留 similarOfferId/similarOfferName/series/sub_type 溯源键）；② 环节3 产品信息整合提示词改写为**同构键值合并**——两侧同一套配置结构模板规范，按 field 名逐字段对齐（需求有值→原始需求；无值→offerInfo 同名字段标 AI补全；皆缺失→留空由引擎补全），消除跨结构语义对齐；③ gen_plugins 工具1 出参 schema 补 offerInfo.fields 数组结构；节点3/4 提示词与 flowRemark 同步；双校验通过、后端编译通过 |
| V1.5 | 2026-09-12 | ① **执行主干自动化串行**：用户确认后，智能配置→实时稽核→资费校准→自动测试四环节自动串行执行，中途不中断，仅环节异常时中断；② 每环节执行后打印处理结果，异常时打印异常节点与异常原因，并引导用户选择"重新执行"或"修改执行方案"；③ 全部成功后打印各环节成功结果详情，并提示"是否发起上线审批"，用户确认后才发起；④ 新增**消息查询**能力：发送消息可查询审批进度（query_approval_status）、查询监控运维结果（query_product_monitor） |
| V1.6 | 2026-09-12 | ① **插件全部自研+模拟结果输出**：工具1~6 不再对接外部 ApiID，改为自研实现并采用模拟结果输出，模拟数据须兼容适配《产品信息.txt》全部 18 个销售品套餐（测试预期值取自套餐规则值），新增"模拟结果兼容性要求"；② 《产销品场景部分能力接口清单.xlsx》降级为接口契约参考；③ 实时稽核明确为普通接口（无 ApiID） |
| V1.7 | 2026-09-13 | ① **LLM 智能调度模式**：删除主流程 `wf_cpcp_main`（JSON 已删除，不再归档），智能体按【意图→子工作流智能调度映射表】**直调 `wf_sub_01`~`wf_sub_08` 八个子工作流**；确认执行→写确认标记（node_name=CONFIRMED）→串行直调 wf_sub_02→03→05→04；② **门禁由工具层硬校验保障**：后端 `save_product_config` 校验存储中 req_id 的 CONFIRMED 标记（无标记返回 NOT_CONFIRMED）、`submit_release_approval` 校验 req_id 四环节（config/spec/fee/test）结果齐全，`NodeResultService` 新增 `latestRecord(reqId,nodeName)` 公开方法支撑校验；③ wf_sub_01 需求分析 LLM 节点重写：单出参 `plan_output` + 代码节点 004a 拆分，输出 18 字段完整结构化方案，来源仅"原始需求/AI推理"两种；④ **环节结果存储下沉到子工作流内部**：wf_sub_02~05 各内置 save_node_result 节点（req_id=入参，node_name=config/spec/fee/test）；⑤ **统一存储键（本版定稿）**：原 plan_id/execution_id 双键合并为 **req_id 单键**（PLAN+yyyyMMddHHmmss+3位随机数，方案批次与执行主干共用，同键覆盖），wf_sub_01~06 全部单 req_id 入参，wf_sub_02 按 req_id+requirement 自查执行方案，方案key由后端从 plan_json 的 req_id 键提取 |

## 配套文档索引
| 文档 | 用途 |
| --- | --- |
| 《产销品加载AI应用-交付与运维手册.md》 | **交付与运维配套手册（V2.0 起整合原《平台配置清单》《开发工作清单》）**：第 1 章开发工作清单（接口/服务/工作流 JSON/代码节点），第 2 章平台配置清单（工作流 JSON 部署与自测/后端就绪/联调/验收） |
| 《产销品加载AI应用-专项设计方案汇编.md》 | **专项设计与子方案汇编（V2.0 起整合 5 篇专项方案）**：融合商品加载落地、需求分析模板驱动重构、本体推理接入模板轨、存量实例化报文生成、存量数据清洗规则 |
| 《产销品加载AI应用-知识文档来源与采编指南.md》 | 知识库文档来源渠道与采编 SOP |
| `场景设计/ah_cti_poc/knowledge\` 目录（见 `knowledge\README_知识库总索引.md`） | 知识库冷启动语料，K1~K5 五个分类：K1 业务规范 / K2 资费规则 / K3 测试规范 / K4 存量销售品资料 / K5 FAQ（V2.0 起承载于 `knowledge/`，同时含 `ontology-fields.json`、`seed_offer_groups.json` 等结构化资产） |
| 《产销品加载AI应用-本体应用方案.md》 | 本体（TTL）应用方案：结构化知识、关系推理、配置校验，V1.1 新增 |
| `cpcp_product_ontology.ttl` | 可导入平台的产销品域本体文件（10 类/20+ 数据属性/12 对象属性/枚举/实例），V1.1 新增 |
| 《产销品加载AI应用-端到端演示剧本.md》 | 全流程演示剧本（验收评审/宣传展示用），V1.1 新增 |
| 《工作流JSON开发规范.md》（`工作流配置/工作流JSON开发规范.md`） | **工作流 JSON 开发规范（V2.0 起为当前实现基线）**：12 工作流 JSON 的结构规范、type=6 代码节点内嵌规则、参数与统一键（req_id）约定、知识库挂载约定 |
| 《SitechAI开发平台配置规范.md》（`场景设计/SitechAI开发平台配置规范.md`） | **AI 开发平台配置规范（通用配置基准）**：平台四层架构、源码驱动纪律、节点类型/契约（入参两态三层一致、array item 树、snake_case）、条件分支 sourcePort、HTTP 插件/代码节点/LLM 节点规范、插件 Export OpenAPI、平台能力边界、最佳实践。**本方案 12 工作流 JSON 与插件/端点的配置均对齐此规范**（符合性见 6.5.6 节） |
| 《工作流配置重塑改造清单.md》（`工作流配置/工作流配置重塑改造清单.md`） | **工作流配置重塑改造清单（V2.0）**：从旧载体（技能包/平台工作流）重塑为 12 工作流 JSON + 知识库 + 后端适配端点的改造对照、逐子流差异、验收项 |
| ~~`需求分析工作流可参考提示词.txt`~~ | **@deprecated（V1.2 历史文件，已归档至 `方案/_deprecated/`）**：V1.2 时代的旧四分类字段体系（产品名称/套餐固定费/收费方式/渠道类型等），与现行 V3.0「3 模块/9 分类/24 字段」口径冲突，禁止再作为需求分析提示词来源；现行口径唯一权威=`knowledge/ontology-fields.md` + `knowledge/ontology-fields.json` + 后端 FieldOntologyService |
| `产销品场景部分能力接口清单.xlsx` | 需求方提供的 HTTP API 能力接口清单（9 个接口），V1.6 起降级为**接口契约参考**（路径/入参/出参定义依据），实际实现为自研模拟接口，V1.2 新增 |
| `产品信息.txt` | 需求方提供的存量销售品资料（5G-A 系列套餐），用于相似度匹配验证与端到端测试，V1.2 新增 |

> 注意（V2.0）：原 `skills/`、`references/` 相关条目（Skills 技能包实现方案、技能包 README、SKILL.md、flow-A~D）随技能包载体废弃而移除，实现基线以「12 工作流 JSON + knowledge/ 知识库 + 后端 `/api/v1/appstore/*` 适配端点」为准。

---

## 1. 方案总览

### 1.1 建设目标
以数字员工替代产销品上架全流程人工操作，实现：

**需求提报 → 需求分析（五列模块表格《加载方案》）→ 用户确认 → 【智能配置（配置落地）→ 配置规格稽核 → 资费校准 → 销售品自动测试（含受理验证子集）】自动化串行 → 上线审批（上线校验看板，用户二次确认后发起）→ 监控运维（确认上线后生成监控运维方案）** 的端到端闭环。

> V1.2 关键调整：需求分析产出《产销品加载执行方案》（Markdown 表格 + 执行 JSON），**用户确认执行方案后**才触发智能配置；智能配置不再由大模型重新生成配置，而是**直接读取已存储的执行方案 JSON 调用配置落地接口**，确保"分析结果 = 配置结果"。
> V1.3 关键调整：① 执行方案 JSON 的保存/查询**复用平台「节点结果存储查询插件」**（节点结果写入→按 key 查询，不新建自研存储服务）；② 智能稽核改为**实时接口**调用（不经过文件上传环节，按销售品直接实时发起稽核并取回结果）；③ 自动化测试定位为**发起动作**（offer_test 发起后由测试平台自动完成受理类场景的执行与验证），**测试报告内容包含受理验证结论**，不再单设"受理验证"环节。
> V1.5 关键调整：① **执行主干自动化串行**：用户确认后，智能配置→实时稽核→资费校准→自动测试**四环节自动串行执行、中途不停顿**，每个环节执行后打印该环节处理结果；任一环节异常即中断，打印**异常节点与异常原因**，并引导用户选择【重新执行】（从失败环节续跑）或【修改执行方案】（回到需求分析）；② 四环节全部成功后，打印**各环节成功结果详情**，并提示"是否发起上线审批"，**用户确认后才发起审批**（审批不再自动触发）；③ **消息查询**：流程结束后用户可随时发送消息查询**审批进度**（新增 `query_approval_status` 工具）与**监控运维结果**（`query_product_monitor`）。

### 1.2 总体架构（平台能力映射）

| 平台能力 | 在本场景中的角色 |
| --- | --- |
| 智能体（助手） | 产销品数字员工统一入口（对话式 + 流程式调度中枢）；V2.1 起由智能体（LLM）按 3.2 提示词【意图→工作流映射表】**语义识别直调** 11 个子工作流（串行控制、结果打印、异常中断引导由提示词【调度纪律】约束），后端硬校验兜底；流程结束后承接**消息查询**（审批进度/监控结果）与**异常处置引导**（重新执行/修改执行方案） |
| 工作流 | 承载 11 个业务环节的子工作流（wf_sub_00~10，req_id 单必填入参自查链路，无意图调度主流程）；由智能体语义识别后直调各子流，子流内部含环节结果存储节点（save_node_result） |
| 插件/工具 | 封装 HTTP API 能力接口（相似度分析/智能稽核/智能测试等 9 个）、平台「节点结果存储查询插件」及 CRM、计费、订单等系统原子能力 |
| 知识库 | 存储业务规范、资费规则、测试规范、存量产品资料（含《产品信息.txt》5G-A 系列销售品语料） |
| **本体库（TTL）** | 产销品域结构化知识：类/属性/关系建模，支撑需求校验、关系推理、配置生成约束、术语对齐（V1.1 新增） |
| 大模型节点 | 需求解析（执行方案生成）、稽核判定、测试报告生成（含受理验证结论汇总）、串行执行结果汇总输出 |
| 选择器节点 | 稽核通过/驳回、资费通过/驳回、测试通过/失败等分支控制（子工作流内部）；V1.7 起确认门禁改由后端工具层硬校验（CONFIRMED 标记/四环节结果） |

### 1.3 设计原则
1. **子工作流化 + 智能体语义调度（V1.7/V2.1）**：每个环节封装为子工作流（wf_sub_00~10，req_id 单必填入参自查链路）；V2.1 起撤销 `wf_main_intent` 意图调度主流程，由智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别后**直调**各子流，确定性纪律（确认门禁、串行不跳步、fail_node 续跑、req_id 沿用、仅依据出参判成败）固化在提示词【调度纪律】与【限制】并由后端硬校验兜底。
2. **插件先行，自研模拟**：所有系统交互必须封装为插件工具（HTTP 接口），大模型只做"理解与生成"，不直接触碰生产系统。V1.6 起 13 个插件工具**全部自研实现并采用模拟结果输出**（不再对接外部 ApiID），模拟数据须兼容适配《产品信息.txt》全部 18 个销售品套餐；执行方案存储等通用能力优先复用平台已有插件（如「节点结果存储查询插件」），不自研重复能力。
3. **需求分析结构化优先（V3.0 口径）**：需求分析环节按 `wf_sub_01` 需求分析子工作流（模板轨）执行——要素提取→validate_elements 质量门禁→merge_nested→validate_nested 本体校验闸→render_table，按 `knowledge/ontology-fields.md`/`ontology-fields.json` 同义映射表提取 **3 模块/9 分类/24 字段**（套餐名称/套餐编码/套餐档位/.../流量结转规则/断网授权），字段来源仅"原始需求/AI补全"两种。**补全策略（V3.0 口径）：仅价格类字段（套餐档位）未提取到时维持"待补充"（禁止推理、禁止从相似产品照搬）；套餐编码恒填"系统待生成"（不计入待补充）；其余缺失字段按取值链补全——相似产品（offerInfo 同构 24 字段）→ 本体推理引擎默认值（FieldOntologyService，标"AI补全"）**，产出可执行的《加载方案》（五列模块表格）。**@deprecated**：旧《需求分析工作流可参考提示词.txt》四分类字段体系（产品名称/套餐固定费/渠道类型等）已废弃归档，禁止参照。
4. **确认后再执行**：智能配置属于生产写入类操作，必须等用户对执行方案回复确认后才触发；配置数据直接取自执行方案 JSON，大模型不做二次加工，避免语义漂移。
5. **实时稽核**：配置落地后立即按销售品实时调用稽核接口，同步取回稽核结果，不经过文件中转。
6. **测试即验证**：自动化测试是一次"发起"动作，测试平台自动执行受理类场景（新装/加装/退订等）即完成受理验证；测试报告须包含受理验证结论。
7. **知识库兜底**：业务规范类判断（如资费叠加约束）以知识库检索增强 + 大模型节点共同完成，降低幻觉。
8. **自动化串行，异常即停**：用户确认后的执行主干（智能配置→实时稽核→资费校准→自动测试）**自动串行执行、中途不停顿**；仅当环节异常（落地失败/稽核驳回/资费不通过/测试失败/接口异常）时中断。每环节执行后打印处理结果；异常时打印异常节点+异常原因，并引导用户【重新执行】或【修改执行方案】；全部成功后打印各环节成功详情并提示"是否发起上线审批"。
9. **审批二次确认**：上线审批不自动触发，须在执行主干全部成功后由用户确认发起。
10. **人机协同**：用户确认执行方案、确认发起上线审批两个生产/发布决策点保留人工确认；流程结束后支持消息查询审批进度与监控运维结果。

### 1.4 实现方式说明（V2.0：Skills 技能包实现 → 12 工作流 JSON + 知识库 + 后端适配端点）

> 本方案第 2~6 章按原「九思平台（子工作流 + 插件 + 知识库切片）」实现方式编写，作为**业务口径与契约的权威定义**保留。**当前代码实现已重塑为 12 个工作流 JSON + `knowledge/` 知识库 + 后端 `/api/v1/appstore/*` 适配端点方式**，两者业务逻辑等价，对应关系如下：

| 原平台构件（第 2~6 章描述） | V2.0 工作流 JSON 实现（当前基线） |
| --- | --- |
| 智能体常驻提示词（3.2 角色+职责+意图映射表+调度纪律+限制） | 智能体（LLM）按 3.2 节提示词【意图→工作流映射表】（覆盖 需求提报/确认配置/失败续跑/上线审批/审批进度/监控运维/存量查询/存量合规/QNA 等）做**语义识别并直调各子流**；无独立意图调度主流程 |
| 子工作流 wf_sub_01~08（6.2 节节点表） | 11 个子工作流 JSON：`wf_sub_00`~`wf_sub_10`（环节覆盖扩展至需求提报工单审批/存量查询/存量合规/根因闭环等），`gen_workflows_v2.py` 确定性生成（无 `wf_main_intent` 意图调度主流程） |
| 插件工具 1~14（2.1/4.2/4.4 节契约） | 后端 AppStoreV16Controller `/api/v1/appstore/*` 适配端点（14 个既有契约端点 + 7 个 V2.0 新增：`/ops/root-cause`、`/ops/work-orders`、`/shelf-compliance`、`/validate-nested`、`/explain`、`/report/download`、`/script/download`，网关 BASE_URL=http://10.86.13.201:31281） |
| 代码节点（004a 拆分 / CODE_EXTRACT_RECORD / 0304 轮询） | 全部内嵌为工作流 **type=6 代码节点**：CODE_MERGE_NESTED / CODE_RENDER_TABLE / CODE_VALIDATE_ELEMENTS / CODE_GET_TEMPLATE / CODE_RENDER_REQ / CODE_MAP_FIXED_CASES / CODE_EXTRACT_RECORD / CODE_POLL_PROGRESS / CODE_FUSION_GROUP_ECHO / CODE_OP_ROOT_CAUSE / CODE_OP_CREATE_WO / CODE_OP_SHELF_COMPLIANCE / CODE_OP_VALIDATE_NESTED / CODE_SUMMARY_APPROVAL / CODE_DOWNLOAD_* 等 |
| 平台知识库 K1~K5（5.1 节） | `knowledge/` 目录（K1规范|K2资费|K3测试|K4存量|K5FAQ 知识库 + `ontology-fields.json`/`ontology-fields.md`/`seed_offer_groups.json`/templates 注册表等结构化资产），由工作流代码节点 / 后端服务按需读取 |
| 知识库向量召回/切片 | 确定性代码节点 / 后端服务按需读取（K4 按销售品 ID 单文件精确定位，禁止全量读取） |
| 平台门禁（确认标记/四环节校验） | 后端硬校验保留不变（5002/四环节门禁/幂等），确认门禁沿用 V2.2 口径由智能体提示词【调度纪律】保证（确认语义未命中不触发执行主干），防跳步由后端四环节硬校验兜底 |

**重塑核心原则**：业务逻辑零改动（3 模块/9 分类/24 字段、待补充判定、本体推理引擎、req_id 统一键、异常矩阵原样保留）；接口契约零改动（路径/入参/出参与 2.1 节一致，替换真实实现仅改网关 BASE_URL）；确定性逻辑（merge_nested/render_table/validate_elements/get_template/render_requirement_report/map_fixed_cases/extract_record/dispatcher/poll_progress 等）由技能包脚本子命令迁移为工作流 type=6 代码节点，同一份 Python 逻辑整体内嵌、行为可审计。

**执行入口变化**：用户对话 → 智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别用户意图（含实体提取 req_id/offer_id/approval_id 等）→ 直调对应子流 `wf_sub_00`~`wf_sub_10` → 子流内经代码节点 + 后端适配端点完成业务处理 → 结束节点输出结果。上下文成本控制：智能体常驻提示词精简，子流/知识库命中意图后才加载对应单份；大报文（plan_json/config_json/fields/report）一律经节点结果存储（req_id+node_name）或后端适配端点下载（report/download、script/download）传递，不经模型上下文中转。

---

## 2. 需求到平台能力映射

| 业务环节 | 业务目标 | 平台实现载体 |
| --- | --- | --- |
| 需求提报 | 收集业务需求文档/结构化需求，生成需求工单 | 子工作流 `wf_sub_00`（需求提报）：LLM 要素提取（snake_case 扁平 JSON）+ 需求工单审批（**approval-type=requirement**），生成需求工单号 req_id，经审批通过后流转 wf_sub_01 |
| 需求分析 | 业务要素→配置字段映射，生成《产销品加载执行方案》并存储执行 JSON | 子工作流 `wf_sub_01`（模板轨）：要素提取（CODE_EXTRACT_RECORD）→ CODE_VALIDATE_ELEMENTS 质量门禁 → CODE_MERGE_NESTED 合并嵌套 → CODE_OP_VALIDATE_NESTED 本体校验闸（validate-nested 端点）→ CODE_RENDER_TABLE 渲染表格 → 保存 requirement（node_name=requirement） |
| **用户确认** | 人工确认执行方案，确认后才进入配置落地 | 智能体提示词【调度纪律】识别确认语义（未命中确认不触发智能配置） |
| **执行主干自动化串行**（V1.5，V2.1 智能体语义调度） | 智能配置→稽核→资费→测试自动串行，环节异常才中断；每环节打印结果，异常打印节点与原因并引导重新执行/修改执行方案 | 智能体语义识别"确认配置"后**串行直调**：`wf_sub_02`→`wf_sub_03`→`wf_sub_05`→`wf_sub_04`；异常按 fail_node 续跑（门禁由后端硬校验） |
| 销售品智能配置 | 按 req_id 自查存储的执行方案 JSON，调用配置落地接口完成 CRM 配置 | 子工作流 `wf_sub_02`（req_id 单入参）：自查 requirement + 配置落地 `save_product_config` + **CODE_FUSION_GROUP_ECHO 融合组成员回显**（含可选 group） |
| 配置规格稽核 | 配置落地后**实时调用稽核接口**，同步取回稽核结果 | 子工作流 `wf_sub_03`（组维度）：实时稽核 `realtime_spec_audit`（组维度稽核，error_list 含 group 与 role 定位） |
| 资费校准 | 校验计费逻辑、优惠叠加冲突 | 子工作流 `wf_sub_05`（成员分组）：计费规则校验 `check_billing_rule`（check_scene=all，按 member_role 分组比对，E27 负值比对） |
| 销售品自动测试（含受理验证） | **发起**销售品自动化测试（发起动作）→轮询进度→取结果；测试平台自动执行受理类场景即完成受理验证 | 子工作流 `wf_sub_04`（重写）：`offer_test` / `get_test_scenes` / CODE_POLL_PROGRESS 轮询 / `get_test_result` + **CODE_MAP_FIXED_CASES（31 条固定用例 ACC/BILL/CUST + P0/P1）** + 九章节正式版报告 + 报告下载（report/download） |
| 上线审批 | 汇总测试报告（含受理验证）、推送审批；**审批通过自动上线 + 生成监控运维方案** | 子工作流 `wf_sub_06`（**双轨 requirement/launch**）：CODE_SUMMARY_APPROVAL 汇总报告 → `submit_release_approval` 推送审批，审批通过自动上线并生成监控运维方案 |
| **审批进度查询**（V1.5） | 用户发送消息查询审批单当前状态 | 子工作流 `wf_sub_08`（审批进度查询，**双轨**查询）+ `query_approval_status`（按 approval_id/offer_id） |
| **存量产品查询**（V2.0 新增） | 用户发送消息查询存量/在售产品销售品信息（只读） | 子工作流 `wf_sub_09`（存量产品查询，**只读**）+ CODE_OP_QUERY_OFFER + `query_similar_offer`/`query_node_result` 只读查询 |
| **存量合规扫描**（V2.0 新增） | 存量销售品合规扫描（上架合规） | 子工作流 `wf_sub_10`（存量合规扫描）：CODE_OP_SHELF_COMPLIANCE → 后端 `/shelf-compliance` 端点 |
| 监控运维 | 上线后持续监控异常，异常根因推理 + 建工单闭环 | 子工作流 `wf_sub_07`（异常分支）：`query_product_monitor` 监控查询 → 异常时 **CODE_OP_ROOT_CAUSE 根因推理**（/ops/root-cause）+ **CODE_OP_CREATE_WO 建工单**（/ops/work-orders）闭环 |

### 2.1 原子能力 → 插件清单

#### A. 自研能力接口（V1.6 定位调整：以下接口全部**自研实现并采用模拟结果输出**，不再对接外部 ApiID；模拟数据须兼容适配《产品信息.txt》全部 18 个销售品套餐）

| # | 验证场景 | 插件工具名 | 用途说明 |
| --- | --- | --- | --- |
| 1 | 相似度分析 | `query_similar_offer` | 通过业务需求描述文本查询相似销售品，支撑需求分析环节"匹配历史相似产品/配置"与 AI推理；模拟实现：以《产品信息.txt》18 个销售品为相似产品库 |
| 2 | 智能稽核 | `realtime_spec_audit` | **实时稽核**：按销售品/配置内容实时发起稽核并同步返回稽核结果，替代原"文件上传+异步报告"链路（V1.3 调整，原文件上传类接口不再使用）；模拟实现：按配置规范规则引擎输出稽核结论 |
| 3 | 智能测试 | `offer_test` | **发起动作**：按销售品 ID 发起自动化测试（含受理类场景执行），返回测试流水 `globalId`；模拟实现：异步模拟测试执行，覆盖受理类场景 |
| 4 | 智能测试 | `get_test_scenes` | 查询本次测试匹配的测试场景集合（如 套餐新装/副卡加装/套餐退订），即受理验证覆盖范围 |
| 5 | 智能测试 | `get_test_progress` | 轮询测试步骤、是否完成、是否失败（done/failed/failIndex） |
| 6 | 智能测试 | `get_test_result` | 测试全部完成后查询逐场景测试点统计与 AI 总结；返回的 `orderId`/`offerInstId` 即实际受理生成的订单号/实例 ID，作为受理验证依据 |

#### B. 平台已有插件（复用，不自研）

| # | 插件工具名 | 说明 |
| --- | --- | --- |
| 7 | `节点结果存储查询插件` | 平台通用能力：**执行方案 JSON 与各环节结果的保存与查询**（V1.6 后端已落库持久化，pd_ai_node_results 表）。需求分析产出的执行方案 JSON 以「节点结果」形式写入（req_id=方案批次号、node_name=requirement），执行主干各子工作流以同键 req_id、node_name=config/spec/fee/test/report 存取本环节结果（V1.7 统一键：原 plan_id/execution_id 双键合并为 req_id 单键）。V1.3 起替代原自研 save_plan_json/get_plan_json |

#### C. 需补充开发的能力接口（自研，模拟结果输出）

| # | 插件/工具名 | 说明 |
| --- | --- | --- |
| 8 | `save_product_config` | **配置落地接口**：读取执行方案 JSON（基础信息/资源配置/营销资源/销售规则四类字段），写入 CRM 销售品配置。V1.2 核心新增；模拟实现：写入内存产品档案（种子数据含《产品信息.txt》18 个销售品），并生成 offer_id |
| 9 | `check_billing_rule` | 计费规则校验：校验套餐计费逻辑、优惠叠加规则；模拟实现：规则引擎内置叠加/互斥/负资费规则，模拟数据适配 18 个销售品资费结构 |
| 10 | `submit_release_approval` | 上线审批推送：汇总报告并推送审批流（V1.5 起由用户确认后触发）；模拟实现：生成审批单号并记录状态流转 |
| 11 | `query_product_monitor` | 监控查询：查询上线后订单量、异常量、计费差错等（V1.5 起支持消息查询触发）；模拟实现：按产品 ID 确定性生成监控指标与告警回显 |
| 12 | `send_alert` | 异常告警：推送异常告警到运维群/工单；模拟实现：生成告警单号，供监控查询回显闭环 |
| 13 | `query_approval_status` | **审批进度查询**（V1.5 新增）：按 `approval_id`/`offer_id` 查询审批单当前状态（审批中/通过/驳回）与当前审批环节、审批意见，支撑用户消息查询审批进度；模拟实现：按审批单号回放状态与意见 |

#### D. V2.0 新增后端适配端点（AppStoreV16Controller，`/api/v1/appstore/*`，V2.0 重塑新增）

> 以下 7 个端点由后端 AppStoreV16Controller 以适配端点形式提供（网关 BASE_URL=http://10.86.13.201:31281），供各子工作流 type=6 代码节点调用，取代原技能包脚本子命令。

| # | 端点（POST） | 用途说明 | 承载环节 |
| --- | --- | --- | --- |
| 14 | `/ops/root-cause` | 监控运维异常根因推理（ops_root_cause）：对 query_product_monitor 上报的异常指标做根因分析 | wf_sub_07 异常分支（CODE_OP_ROOT_CAUSE） |
| 15 | `/ops/work-orders` | 运维建工单（create_work_order）：根因确认后创建运维工单，闭环异常处置 | wf_sub_07 异常分支（CODE_OP_CREATE_WO） |
| 16 | `/shelf-compliance` | 存量合规扫描（shelf_compliance）：存量销售品上架合规性核验 | wf_sub_10（CODE_OP_SHELF_COMPLIANCE） |
| 17 | `/validate-nested` | 嵌套本体校验闸（validate_nested）：需求分析本体校验（R-C04/C06） | wf_sub_01（CODE_OP_VALIDATE_NESTED） |
| 18 | `/explain` | 解释/说明端点：对校验结果、合规结论等提供可读解释 | 各子流解释类输出 |
| 19 | `/report/download` | 测试报告下载：九章节正式版测试报告下载链接 | wf_sub_04（CODE_DOWNLOAD_TEST_REPORT） |
| 20 | `/script/download` | 配置上线脚本下载：CRM/billing 落库 SQL 上线脚本下载 | wf_sub_06 自动上线（CODE_DOWNLOAD_LAUNCH_SCRIPT） |

> **代码节点化（V2.0）**：原技能包脚本子命令（build_plan/extract_record/poll/dispatcher/map_fixed_cases 等）全部重构为工作流 **type=6 代码节点**（CODE_MERGE_NESTED/CODE_RENDER_TABLE/CODE_VALIDATE_ELEMENTS/CODE_GET_TEMPLATE/CODE_RENDER_REQ/CODE_MAP_FIXED_CASES/CODE_EXTRACT_RECORD/CODE_DISPATCHER/CODE_POLL_PROGRESS/CODE_FUSION_GROUP_ECHO/CODE_OP_ROOT_CAUSE/CODE_OP_CREATE_WO/CODE_OP_SHELF_COMPLIANCE/CODE_OP_VALIDATE_NESTED/CODE_SUMMARY_APPROVAL/CODE_DOWNLOAD_TEST_REPORT/CODE_DOWNLOAD_LAUNCH_SCRIPT 等），确定性逻辑内嵌于 12 个工作流 JSON 中，同一份 Python 逻辑整体内嵌、行为可审计，不再依赖外部脚本子命令。

> **模拟结果兼容性要求（V1.6 新增，全部自研接口统一遵守）**：模拟输出不得写死单一套餐样例，须以《产品信息.txt》全部 **18 个销售品**（900102308/900113043/900113046/900102307/900102313/900113044/900102310/900102312/900113045 等 5G-A 系列套餐，及 900102306 融合199元、900117020~900117027 权益随心选系列）为种子数据，任一销售品作为输入时，相似度分析、稽核、资费校验、测试（场景/进度/结果/受理验证）、监控等接口均能返回与该销售品资费规则一致的结构化模拟结果（测试预期值 presetValue 取自该销售品在《产品信息.txt》中的规则值），保证端到端演示对任意套餐可复现。后续真实接口替换模拟实现时，接口契约（路径/入参/出参）保持不变。

> 插件调整要点（V1.3）：① 执行方案保存/查询**复用「节点结果存储查询插件」**，删除自研 `save_plan_json`/`get_plan_json`；② 智能稽核改为**实时接口**（删除 `audit_file_upload`/`audit_report_notify`/`audit_result_insert`/`query_audit_by_sql` 四个异步链路工具，改为实时稽核接口，实时稽核为**普通接口**（直接封装插件工具，无 ApiID））；③ 自动化测试为**发起动作**：测试平台自动执行受理类场景（套餐新装/副卡加装/套餐退订），即完成受理验证，取消独立 `verify_acceptance` 环节，测试报告必须包含受理验证结论；④ API 通用接口涉及 `tcpCont` 报文头（transactionId/reqTime/globalId/svcCode/appKey/dstSysId/sign 等固定字段）由插件封装层统一拼装，大模型节点与工作流只透传业务参数；⑤ 自动化测试仍为异步执行（发起→轮询→取结果），插件需支持工作流代码节点循环轮询（建议轮询间隔 5s，超时 30 分钟）。
>
> 插件调整要点（V1.5）：⑥ 新增自研工具 `query_approval_status`（审批进度查询，工具13），与 `query_product_monitor` 共同支撑流程结束后的消息查询能力；插件总数由 12 变为 **13**（6 个 HTTP API + 节点结果存储查询插件 + 6 个自研）。
>
> 插件调整要点（V1.6）：⑦ 原"已有 HTTP API 接口"（工具1~6）定位调整：**不再对接外部 ApiID，改为全部自研实现并采用模拟结果输出**，与工具7~13 统一为自研插件集；⑧ 模拟结果须以《产品信息.txt》全部 18 个销售品为种子数据，兼容适配任一套餐（见 2.1 节"模拟结果兼容性要求"）；《产销品场景部分能力接口清单.xlsx》降级为接口契约参考（路径/入参/出参定义仍按其设计，便于后续替换真实实现）。
>
> 插件调整要点（V2.0）：⑨ 在 14 个既有工具基础上补充 **7 个新后端适配端点**（D 节，工具14~20：/ops/root-cause、/ops/work-orders、/shelf-compliance、/validate-nested、/explain、/report/download、/script/download），插件总数扩为 **21 个**（既有 14 工具 + 7 新端点）；⑩ 确定性逻辑全面**代码节点化**——原技能包脚本子命令（build_plan/extract_record/poll/dispatcher 等）迁移为工作流 type=6 代码节点，由 `wf_main_intent` CODE_DISPATCHER 统一路由调度。

---

## 3. 智能体设计（产销品数字员工）

### 3.1 基本信息
| 项 | 值 |
| --- | --- |
| 助手代码 | `cpcp_product_worker` |
| 助手名称 | 产销品数字员工 |
| 功能介绍 | 面向产销品域的数字员工，支持从需求提报、需求分析（五列模块表格《加载方案》）、用户确认、智能配置（配置落地）、配置规格稽核、资费校准、自动测试（含受理验证子集）到上线审批（上线校验看板）、监控运维方案的全流程自动化操作。 |
| 发布范围 | 所有人可见 |
| 图标 | 默认/上传产销品图标 |

### 3.2 提示词（角色 + 技能 + 限制 模式）

> V2.1 实现说明：撤销 `wf_main_intent` 意图调度工作流（其 CODE_DISPATCHER 程序化路由已下放）；本节提示词为**智能体意图识别 + 子工作流直调**的权威定义——由智能体（LLM）按下方【意图→工作流映射表】做语义识别并**直接调度 11 个子工作流 `wf_sub_00`~`wf_sub_10`**（子流均自带开始节点/req_id 入参/自查链路/结束节点，是完整独立工作流）。确定性纪律（确认门禁、串行不跳步、fail_node 续跑、req_id 沿用、仅依据出参判成败、审批四环节后端硬校验兜底）固化为下方【限制】。**提示词按【角色+技能+限制】三段式精简（2026-09-18）：原【职责与能力】归并为【技能】，原【调度纪律】并入【限制】，映射表与其余纪律语义零改动。**

```
【角色】
你是安徽电信产销品域数字员工，精通 CPCP 产销品管理、CRM 配置、计费规则、订单受理
与测试验证，负责销售品从需求到上线的端到端自动化加载。你不直接操作 CRM、不修改
配置、不代替用户做最终业务决策。

【技能】
1. 需求提报与分析：理解需求→拆解要素（3 模块/9 分类/24 字段）→补全→生成《加载方案》
   （五列模块表格+执行方案 JSON，经节点结果存储保存，返回 req_id）。需求原文最高优先、
   不得虚构；补全只许"原始需求/AI补全"两态；仅套餐档位（价格）缺且无参照才"待补充"
   （禁推理价格）；套餐编码填"系统待生成"。有待补充则列出并提示补充。
2. 执行主干（确认后串行四环节）：识别确认语义（确认配置/确认执行/同意/可以/执行吧）
   →取最近 req_id→串行调度 `wf_sub_02`→`wf_sub_03`→`wf_sub_05`→`wf_sub_04`（受理验证
   为测试子集随结果输出）。每环节返回即打印结果（引用出参原文，结尾"建议处理：可输入
   ××进入【××】"）；环节结果由子流 save_node_result 落库（node_name=config/spec/fee/test）。
   异常（status≠SUCCESS / pass≠1 / test_passed≠通过）立即中断，打印异常环节+原因+关键明细，
   引导【重新执行】（按 fail_node 续调，已成功环节不重跑）或【修改执行方案】。
3. 审批与上线：四环节全成且用户确认后调度 `wf_sub_06`（自查四环节+报告推送，双轨）；
   审批通过自动上线并生成监控运维方案（通过前严禁生成）。
4. 查询与运维：按消息直调 `wf_sub_08` 审批进度（双轨）、`wf_sub_07` 监控运维（offer_id，
   异常分支根因推理+建工单）、`wf_sub_09` 存量查询（只读）、`wf_sub_10` 存量合规扫描。

【意图→工作流映射表（语义识别，直调子流）】
| 用户意图 | 调度子流/动作 | 关键入参 |
| --- | --- | --- |
| 提报/修改需求 | `wf_sub_00`→（工单审批通过）→`wf_sub_01` | requirement_text；req_id 沿用 |
| 确认配置 | 串行 `wf_sub_02`→`03`→`05`→`04` | req_id=最近值 |
| 重新执行失败环节 | 按 fail_node 续调（STAGE1→02/2→03/3→05/4→04） | req_id 沿用；自查回放 |
| 上线审批 | `wf_sub_06`（双轨） | req_id=原值 |
| 查询审批进度 | `wf_sub_08` | approval_id/offer_id |
| 查询监控运维 | `wf_sub_07`（异常根因+工单闭环） | offer_id |
| 查询存量产品 | `wf_sub_09`（只读） | 产品名/ID |
| 存量合规扫描 | `wf_sub_10` | 存量范围 |
| 业务问答/超范围 | K1~K5 知识库检索 / 答案为空提示 | 检索词 |

【限制】
1. 仅回答产销品加载相关业务，超范围按答案为空提示。
2. 智能配置必须用户确认《加载方案》后触发，配置以节点结果存储保存版本为准。
3. 确认语义未命中绝不调度执行主干；req_id 沿用最近值，禁止重新生成。
4. 执行主干必须串行，严禁并行、跳步、凭语义推断成败（仅依据出参 status/pass/valid/test_passed/backend_pending 判）。
5. 续跑已成功环节不重复调用（凭存储回放）；审批由后端四环节硬校验兜底。
6. 审批必须四环节全成且用户确认后才发起；审批通过前严禁生成监控运维方案。
7. 异常时明确给出环节/原因/建议并引导重执行或改方案，不得自行重试。
8. 输出结构化：环节名/结果/关键数据/下一步建议；✅ 与统计值须与出参一一对应，禁止凑数、虚构，不泄露资费配置明细。
```

### 3.3 配置项
| 配置项 | 取值 |
| --- | --- |
| 插件 | 挂载第 2.1 节全部插件工具与后端适配端点（既有 13 个工具 + V2.0 新增 7 个适配端点 + 节点结果存储查询插件，共 21 个；另确定性逻辑内嵌为工作流 type=6 代码节点） |
| 工作流 | 挂载 **11 个子工作流 JSON**（`wf_sub_00`~`wf_sub_10`，见 `工作流配置/智能体工作流集V1.6/`，`gen_workflows_v2.py` 生成；无意图调度主流程，由智能体按 3.2 节【意图→工作流映射表】语义识别后**直调**） |
| 知识库 | 产销品业务规范库、资费规则库、测试规范库、存量销售品资料库（含《产品信息.txt》5G-A 系列语料） |
| 模型配置 | 温度值 0.2（严谨场景）、多轮对话 20 轮、top_p 适度调小 |
| FAQ 直接返回 | 否（需大模型归纳） |
| 开场白 | "您好，我是产销品数字员工，可协助您完成销售品从需求提报、加载方案生成（五列模块表格）、确认后智能配置、稽核校准、自动测试（含受理验证）到上线审批、监控运维的全流程。请上传需求文档或直接描述需求，我将为您生成《加载方案》。" |
| 引导问题 | 1) 我要上新一个 5G 流量套餐，请帮我分析需求并生成加载方案 2) 确认配置刚才的产销品加载方案 3) 查询一下刚才那个销售品的审批进度 4) 查询销售品 900102308 的运行监控结果 5) 重新执行失败的环节 |
| 答案为空提示 | "抱歉，我仅支持产销品加载相关业务，请更换问题或联系管理员。" |

---

## 4. 插件设计（工具清单）

### 4.1 插件创建
- 插件名称：`产销品加载插件集`
- 每个工具统一走「工具代码/名称/描述/接口协议(http/https)/接口地址/出参归纳」配置流程。
- 工具1~6 为自研模拟实现（V1.6），接口路径/入参/出参以《产销品场景部分能力接口清单.xlsx》契约为准，模拟数据须适配《产品信息.txt》全部 18 个销售品套餐。
- 执行方案保存/查询、节点结果存取统一复用平台已有「节点结果存储查询插件」，不在本插件集中重复创建。

### 4.2 HTTP API 能力接口工具定义（6 个，自研+模拟结果输出）

> API 通用要求（V2.7 修订）：全部接口请求体统一为**裸报文**（业务参数 JSON 直接置于顶层）。原 tcpCont 报文头拼装（transactionId/reqTime/globalId/version/sign/svcCode/appKey/dstSysId）与 contractRoot 包裹结构已**整体移除**（实测后端只解析顶层字段，包裹后业务参数不可见返回 PARAM_MISSING）；出参侧若为 contractRoot/resultObject 包裹格式由脚本 `_unwrap` 自动解包。工作流与大模型节点只透传业务参数。

**工具1：相似度分析 `query_similar_offer`**（自研模拟实现：以《产品信息.txt》18 个销售品为相似产品库）
| 项 | 内容 |
| --- | --- |
| 接口 | POST（V2.7 起裸报文） |
| 描述 | 通过业务需求描述查询相似销售品（≤5000字符），**返回相似度最高的1个产品及其完整产品配置信息（offerInfo 与需求要素同构：fields 四类18字段数组，后端 toFields18 转换）**，用于需求分析环节匹配历史产品 |
| 入参 | `businessDesc`(string,必填,业务需求描述文本≤5000字符) |
| 出参 | `similarOffer`(object: similarOfferId, similarOfferName, similarityScore, similarityDesc, **offerInfo=完整产品配置信息（与需求要素同构的 fields 四类18字段数组）**)，未命中时为空对象 |
| 是否提参 | 是（businessDesc 必填） |

**工具2：实时规格稽核 `realtime_spec_audit`**（V1.3 调整：实时接口，普通接口无 ApiID）
| 项 | 内容 |
| --- | --- |
| 接口 | POST（V2.7 起裸报文） |
| 描述 | 配置落地后按销售品/配置内容**实时发起稽核**，同步返回稽核结果（通过/驳回 + 问题明细 + 整改建议），不经过文件上传与异步报告链路 |
| 入参 | `offer_id`(string,必填,配置落地返回的销售品ID) `config_json`(string,必填,落地配置JSON) `audit_scene`(string,选填,稽核场景: spec/fee/all，默认 all) |
| 出参 | `pass`(int: 1通过/0不通过) `error_list`(array: item, level, desc, suggest) `audit_summary`(string,稽核总结) `resultCode`(string) |
| 是否归纳 | 是（大模型节点提炼"通过/驳回 + 整改建议"） |

**工具3：销售品测试发起 `offer_test`**（自研模拟实现：异步模拟测试执行，返回模拟测试流水）
| 项 | 内容 |
| --- | --- |
| 接口 | POST（requestObject 报文） |
| 描述 | **发起动作**：根据销售品 ID 发起自动化测试（异步执行，测试平台自动完成受理类场景的执行与受理验证），返回测试流水 globalId |
| 入参 | `offerId`(string,必填,销售品ID，offer 表主键) |
| 出参 | `resultCode`(string: 0/1) `globalId`(string,格式 50+yyyyMMddHHmmss+10位随机数) `resultMsg`(string) |

**工具4：查询测试场景 `get_test_scenes`**（自研模拟实现：按产品返回受理类场景集合）
| 项 | 内容 |
| --- | --- |
| 接口 | POST（requestObject 报文） |
| 描述 | 测试发起后查询本次测试匹配的测试场景集合（即受理验证覆盖范围） |
| 入参 | `globalId`(string,必填,测试流水号) |
| 出参 | `testScenes`(array: testSceneId, testSceneName, testSceneNbr, testSceneDesc, sort) `resultCode`(string) |

**工具5：查询测试进度 `get_test_progress`**（自研模拟实现：按流水号推进模拟进度）
| 项 | 内容 |
| --- | --- |
| 接口 | POST（requestObject 报文） |
| 描述 | 轮询测试当前步骤、是否完成、是否失败 |
| 入参 | `globalId`(string,必填,测试流水号) |
| 出参 | `totalSteps`(int) `activeIndex`(int) `done`(bool) `failed`(bool) `failIndex`(int) `totalSceneCount`/`finishedSceneCount`/`failedSceneCount`(int) |

**工具6：查询测试结果 `get_test_result`**（自研模拟实现：测试点预期值取自《产品信息.txt》该销售品规则值）
| 项 | 内容 |
| --- | --- |
| 接口 | POST（requestObject 报文） |
| 描述 | 测试进度全部完成后查询逐场景测试点比对明细与 AI 场景总结；`orderId`/`offerInstId` 为实际受理生成的订单号/销售品实例 ID，作为**受理验证结论**依据 |
| 入参 | `globalId`(string,必填,测试流水号) |
| 出参 | `testRequestId`(string) `testScenes`(array: 场景统计 testCaseCount/successTestCaseCount/failTestCaseCount, 测点明细 testPointNbr/presetValue/testValue/resultCode/resultMsg, AI总结 objTestSceneRel) `orderId`/`offerInstId`(string) |
| 是否归纳 | 是（由工作流大模型节点汇总生成测试报告，报告必须包含受理验证结论） |

### 4.3 平台复用插件（不自研）

**节点结果存储查询插件：执行方案与各环节结果保存/查询**（V1.3 引入，V1.6 更新）
| 项 | 内容 |
| --- | --- |
| 保存（save_node_result） | POST `/api/v1/appstore/result/save`（入参 req_id/node_name/result_json/status）；wf_sub_01 写执行方案（req_id=入参、node_name=requirement）；wf_sub_02~05 子流内部环节结果存储节点写本环节结果（req_id=入参，node_name=config/spec/fee/test）；wf_sub_06 写上线报告（node_name=report） |
| 查询（query_node_result） | **GET** `/api/v1/appstore/result/query`（入参 req_id/node_name/latest_only，出参 total/list）；各子工作流开始后按 req_id 自查上游结果 |
| key（req_id）规范 | V1.7 统一键：执行方案与执行主干共用单键 `PLAN` + yyyyMMddHHmmss + 3位随机数；**由 wf_sub_01 拆分代码节点以系统时钟生成（datetime.now + 3位随机数，每次分析重新生成、保证唯一、LLM 不参与生成）**；后端硬校验：PLAN 格式校验（非法返回 5002）；requirement 环节同键重写一律删除旧记录后重新插入（同键覆盖写） |
| 说明 | 平台已有通用插件，直接挂载使用，**不再自研** save_plan_json/get_plan_json；后端已落库持久化（pd_ai_node_results 表，H2/MySQL 双 DDL），服务重启不丢失 |

### 4.4 自研能力接口工具定义（6 个）

**工具7：配置落地 `save_product_config`**（V1.2 核心新增）
| 项 | 内容 |
| --- | --- |
| 接口 | POST `https://{cpcp-gateway}/api/v1/appstore/product/config/save` |
| 描述 | 读取执行方案 JSON，将基础信息/资源配置/营销资源/销售规则四类字段写入 CRM 销售品配置 |
| 入参 | `req_id`(string,必填,执行方案存储key，V1.7 统一键) `plan_json`(string,必填,节点结果存储查询插件取回的执行方案JSON，原样透传) `operator`(string,选填) |
| 出参 | `offer_id`(string) `offer_id`(string) `save_result`(object: 各字段分类写入结果) `status`(string) |
| 是否提参 | 是（plan_json 必填且须为存储 JSON 原文） |

**工具8：计费规则校验 `check_billing_rule`**
| 项 | 内容 |
| --- | --- |
| 接口 | POST `https://{billing-check}/api/v1/appstore/billing/rules/verify` |
| 入参 | `config_json`(string,必填,落地配置JSON) `check_scene`(string,枚举: fee/overlay/superposition/all) |
| 出参 | `pass`(int: 1/0) `risk_list`(array: risk_type, risk_desc, suggest) |

**工具9：上线审批推送 `submit_release_approval`**
| 项 | 内容 |
| --- | --- |
| 接口 | POST `https://{oa-gateway}/api/v1/appstore/approval/submit` |
| 入参 | `req_id`(string,必填,V1.7 统一键,后端硬校验四环节结果) `report_url`(string,必填) `approval_flow`(string,枚举: standard/urgent) |
| 出参 | `approval_id`(string) `status`(string) |

**工具10：监控查询 `query_product_monitor`**
| 项 | 内容 |
| --- | --- |
| 接口 | GET `https://{monitor}/api/v1/appstore/product/monitor` |
| 入参 | `offer_id`(string,必填) `date_range`(string,选填) `metric`(string,枚举: order/error/fee/all) |
| 出参 | `order_count`(int) `error_count`(int) `fee_error_rate`(float) `alarm_list`(array) |

**工具11：异常告警 `send_alert`**
| 项 | 内容 |
| --- | --- |
| 接口 | POST `https://{monitor}/api/v1/appstore/alert/send` |
| 入参 | `offer_id`(string,必填) `alarm_level`(string,枚举: high/middle/low) `content`(string,必填) |
| 出参 | `alert_id`(string) `status`(string) |

**工具13：审批进度查询 `query_approval_status`**（V1.5 新增）
| 项 | 内容 |
| --- | --- |
| 接口 | GET `https://{oa-gateway}/api/v1/appstore/approval/status` |
| 描述 | 按 approval_id 或 offer_id 查询上线审批单当前状态，支撑用户发送消息查询审批进度 |
| 入参 | `approval_id`(string,选填,审批单号，与 offer_id 至少一个必填) `offer_id`(string,选填,产品ID，与 approval_id 至少一个必填) |
| 出参 | `approval_id`(string) `status`(string: 审批中/通过/驳回) `current_node`(string,当前审批环节) `approver`(string,当前审批人) `opinion`(string,审批意见) `submit_time`/`update_time`(string) |
| 是否归纳 | 是（大模型节点归纳为"审批单号+状态+当前环节+意见"摘要） |

> 通用要求：出参"是否归纳总结"按工具用途设置——查询类选"是/LLM"，执行类选"否"（由工作流大模型节点统一汇总）；入参均需填写"为空提示"与"是否提参"。

---

## 5. 知识库设计

### 5.1 知识分类与内容
| 知识分类 | 内容 | 用途 |
| --- | --- | --- |
| 产销品业务规范 | 产销品管理办法、配置规范、命名规则、上架流程 | 规格稽核判定依据、需求解析参照 |
| 资费规则库 | 资费模板、叠加优惠约束、计费口径说明 | 资费校准、大模型节点提示词增强 |
| 测试规范库 | 测试用例设计规范、受理/计费测试标准、报告模板 | 测试用例生成、报告生成 |
| 存量销售品资料库 | 《产品信息.txt》（需求方提供，共 18 个销售品：5G-A 系列 10 个——900102308 5G-A套餐199元、900113043 单品239元、900113046 融合199元、900102307 融合299元、900102313 融合399元、900113044 融合239元、900102310 单品299元、900102312 单品399元、900113045 单品199元、900102306 融合199元；权益随心选系列 8 个——900117022/900117020 娱乐版19.9/29.9元、900117027/900117026 生活版19.9/29.9元、900117023/900117024 出行版19.9/29.9元、900117025/900117021 商超版19.9/29.9元；各销售品的套内资费/套外资费/副卡/订购/退订全量规则） | 需求分析环节 AI推理的字段参照、相似度匹配结果解读、测试预期值参照 |
| FAQ | 常见问题（如：稽核不通过怎么办、如何查询存量产品） | 智能体直接问答 |

> 《产品信息.txt》使用说明（V1.2 新增）：该文件为需求方提供的存量销售品全量资料，仅用于测试验证与知识库语料，不得作为新需求的字段来源覆盖用户原始需求。用途：① 将其切片入库至"存量销售品资料库"，供需求分析 AI推理时检索参照；② 端到端测试时，可将其中任一销售品（如 900102308）的资费规则改写为"新需求"输入，验证需求分析解析与 AI推理的准确性；③ 智能测试结果比对时，以其规则值作为预期值（presetValue）的人工核对基准。

### 5.2 上传方式
- 文档模版：上传 word/pdf 规范文档（切片入库）。
- FAQ 模版：稽核失败处理、资费冲突处理等高频问答。
- 命名建议：`[分类]_[文档名]_版本号`，便于版本管理。

---

## 6. 工作流设计

### 6.1 智能体调度模式（V2.1：智能体语义识别直调；撤销 wf_main_intent 意图调度主流程）

> **V2.1 核心调整**：撤销 `wf_main_intent` 意图调度工作流，调度职责重新回归智能体（LLM）——按 3.2 节提示词【意图→工作流映射表】做语义识别后**直调** 11 个子工作流 `wf_sub_00~10`。常驻提示词精简（仅角色/职责/映射表/调度纪律/限制），串行控制、结果打印、异常中断引导由【调度纪律】约束；确定性纪律（确认门禁、串行不跳步、fail_node 续跑、req_id 沿用、仅依据出参判成败）固化为提示词硬性原则，并由后端硬校验（四环节门禁/req_id 校验）兜底。子工作流内部保留环节结果存储节点（save_node_result），续跑按存储记录回放。
>
> **演进背景（V1.7 起，供追溯）**：V1.7 删除主工作流 `wf_cpcp_main` 固定编排（JSON 已删除），改由智能体 LLM 按【意图→子工作流智能调度映射表】直调 `wf_sub_01`~`wf_sub_08` 八子流，由 LLM 承担环节顺序控制/入参注入/结果打印/异常中断引导；原主流程环节结果存储职责已下沉到 wf_sub_02~05 内部（save_node_result）。V2.0 曾将 LLM 调度程序化为 `wf_main_intent` + CODE_DISPATCHER 确定性路由；V2.1 在保留 V1.7 直调模式基础上，将 V2.0 沉淀的确定性纪律（确认门禁、串行不跳步、fail_node 续跑、req_id 沿用、仅依据出参判成败、审批四环节后端硬校验兜底）固化进智能体提示词【调度纪律】与【限制】，撤销独立的 `wf_main_intent` 调度工作流。
>
> 选择智能体语义直调的考量（承接 V1.7 决策并修订 V2.0）：① 平台配置规范要求"调度中枢用智能体"（意图映射表直调子流，见《SitechAI开发平台配置规范.md》），`wf_main_intent` 与规范职责重叠冗余；② 现行 `wf_main_intent` 存在 wf_merged_exec 悬挂引用、未路由 wf_sub_01~05/10 等真实缺陷，主流程无法触达主业务链路；③ 「确认执行」后携带 req_id 的注入问题由子流自查链路（query_node_result 按 req_id 自查）在子流内部解决，无需主调度注入；④ 串行/防跳步/续跑等确定性纪律固化为提示词【调度纪律】并由后端硬校验兜底，行为仍可审计、可测试。

**执行主干调度时序（智能体语义识别"确认配置"后串行直调，不等用户再发消息）：**

```
用户回复"确认执行"（携带上轮 req_id）
  ▼
① 智能体语义识别命中"确认配置"（req_id 沿用最近值；req_id 由 wf_sub_01 拆分代码节点以系统时钟生成，
    PLAN+yyyyMMddHHmmss+3位随机数；上下文沿用原值；V1.7 统一键：原 plan_id/execution_id 双键合并为 req_id 单键）
② 确认语义由智能体提示词【调度纪律】识别（V2.2 起**无需写 CONFIRMED 确认标记**——后端 save_product_config
     已移除该门禁，直接进入执行主干）
  ▼
③ 【环节1】直调 wf_sub_02 智能配置（入参 req_id；子流自查 requirement→提取执行方案原文→落地）
    出参 status/offer_id/save_result
    ├─ status==SUCCESS → 打印【环节1结果】→ 子流内部环节结果存储已写入
    │   （wf_sub_02 内置 save_node_result 节点，node_name=config，req_id=入参）
    └─ 异常 → 打印异常节点+原因 → 引导【重新执行】/【修改执行方案】（中断）
  ▼
④ 【环节2】调用 wf_sub_03 规格稽核（入参 req_id，子流自查 config 取 offer_id/config_json）
    出参 pass/error_list/audit_summary
    ├─ pass==1 → 打印【环节2结果】→ 子流内部已存 node_name=spec
    └─ pass==0/接口异常 → 中断引导（同上）
  ▼
⑤ 【环节3】调用 wf_sub_05 资费校准（入参 req_id，子流自查 config 取 config_json）
    出参 pass/risk_list
    ├─ pass==1 → 打印【环节3结果】→ 子流内部已存 node_name=fee
    └─ 不通过 → 中断引导（同上）
  ▼
⑥ 【环节4】调用 wf_sub_04 自动测试（入参 req_id，子流自查 config 取 offer_id）
    出参 globalId/测试报告（含受理验证结论 orderId/offerInstId）
    ├─ test_passed==通过 → 打印【环节4结果】→ 子流内部已存 node_name=test
    └─ 失败/超时 → 中断引导（同上）
  ▼
    ⑦ 四环节全部成功 → 智能体汇总打印各环节成功详情 + 提示"是否发起上线审批"
    ………… 用户回复"发起审批" → ⑧ 直调 wf_sub_06（入参 req_id：
    子流串行自查 5 类环节结果 → 子流 LLM/代码节点生成 7 章节报告 → 报告存储(node_name=report)
    → submit_release_approval 推送审批）→ 输出审批单号
```

**V2.2 调度要点（与 3.2 提示词一一对应）：**
1. **确认门禁移除（V2.2）**：后端 `save_product_config` 不再校验 confirmed 入参与存储 CONFIRMED 标记（原 NOT_CONFIRMED 门禁删除）——确认与否由智能体 LLM 语义识别判断，防跳步职责回归提示词约定；`submit_release_approval` 仍校验 req_id 四环节结果齐全（执行主干未走完不得发起审批的门禁保留）。
2. **串行纪律**：严禁并行调用、严禁跳过环节、严禁凭语义推断环节成败（仅依据工具出参字段 status/pass/test_passed 判定）。
3. **结果存储**：环节结果存储已下沉到子工作流内部——wf_sub_02~05 各内置 save_node_result 节点（req_id=入参 req_id，node_name=config/spec/fee/test），子流执行成功即自动落库，LLM 无需再调用存储工具；wf_sub_06 内部自查依赖此数据，存储缺失将导致审批校验失败（fail-safe）。
4. **续跑**：`重新执行` 时 LLM 按 fail_node 映射从失败环节续调（STAGE1_CONFIG→wf_sub_02、STAGE2_AUDIT→wf_sub_03、STAGE3_FEE→wf_sub_05、STAGE4_TEST→wf_sub_04），已成功环节不重复调用；入参按 req_id 自查存储回放。
5. **消息查询不走子流**：审批进度/监控结果由 LLM 直接调用工具13 `query_approval_status`、工具10 `query_product_monitor`（wf_sub_07/wf_sub_08 仅作平台不支持直调工具时的兜底）。

**每环节结果打印格式（LLM 输出统一模板）：**
```
【环节N/{环节名称}】✅ 执行成功
- 关键数据：{该环节关键输出（如 offer_id/save_result、audit_summary、
  risk_list 为空、测试统计+受理验证小节 orderId/offerInstId）}
- 已自动进入下一环节……
```

**异常处置输出格式（LLM 统一模板）：**
```
【执行中断】❌ 环节：{异常节点名称}
- 异常原因：{resultCode/resultMsg 或 pass=0 摘要}
- 关键明细：{error_list / risk_list / 失败测点 / 超时信息}
- 整改建议：{LLM 依据出参生成的建议}
请选择下一步：
① 回复【重新执行】：将自动从失败环节继续（已成功环节不重复执行）
② 回复【修改执行方案】：请说明修改意见，将重新生成执行方案并再次确认
```

**成功结果详情输出格式（四环节全部成功后统一模板）：**
```
【执行主干全部完成】✅ 共4个环节执行成功：
1. 智能配置：offer_id={...}，四类字段全部写入成功；
2. 配置规格稽核：通过，{audit_summary}；
3. 资费校准：通过，未发现叠加/互斥冲突；
4. 自动测试（含受理验证）：场景 N 个、测点 M 个全部一致；
   受理验证：orderId={...}，offerInstId={...}，各受理场景均通过。
是否发起上线审批？回复【发起审批】将汇总以上结果提交审批流；
回复【暂不】可稍后发送"发起审批"继续。
```

> **V1.5/V1.6 主工作流（已删除）要点存档**：原 `wf_cpcp_main` 在"需求分析"后经结束节点A 中断等待用户确认；确认后经选择器门禁进入执行主干固定串行段（wf_sub_02→realtime_spec_audit→check_billing_rule→wf_sub_04），每环节接结果打印节点，异常统一走异常处置节点；主干完成后提示"是否发起上线审批"，用户二次确认后进入 wf_sub_06。该模式因主流程对子工作流入参注入受限问题（V1.7 问题B 根因）被 LLM 智能调度模式替代；**V1.7 起主流程 JSON 已删除**（如需回退 V1.6 固定编排模式，可依据 git 历史恢复或参考 `V1.6\gen_workflows.py` 中主流程生成段的历史版本）。

### 6.2 子工作流拆分（11 个子工作流 JSON，V2.0 重塑 / V2.1 去主调度）

> V2.1 实现口径（与 `工作流配置/智能体工作流集V1.6/` 导出 JSON 一致，`gen_workflows_v2.py` 确定性生成）：**11 个子工作流 `wf_sub_00`~`wf_sub_10`（无 `wf_main_intent` 意图调度主流程，由智能体按 3.2 提示词【意图→工作流映射表】语义识别直调）**。各子工作流统一 **req_id 单必填入参**（V2.0 唯一批次标识，方案批次与执行主干共用；由 wf_sub_00 需求提报代码节点生成），子流程内部通过 query_node_result 自查所需上游结果，并在结束前通过**内置环节结果存储节点**（save_node_result，req_id=入参，node_name=本环节名）落库；确定性逻辑内嵌为 **type=6 代码节点**（CODE_*）。

| 子工作流 | 编码 | 入参 | 输出 | 关键节点 / 代码节点 |
| --- | --- | --- | --- | --- |
| 需求提报 | `wf_sub_00` | requirement_text, requirement_file | req_id / 需求工单号 | 开始 → LLM 要素提取（snake_case 扁平 JSON）→ **CODE_RENDER_REQ 需求提报确认渲染**（render_requirement_report）→ 需求工单审批（**approval-type=requirement**，node_name=requirement_report）→ 审批通过后流转 wf_sub_01 |
| 需求分析 | `wf_sub_01` | req_id | plan_json / plan_md（五列模块表格） | 开始 → CODE_EXTRACT_RECORD（取需求工单要素）→ 产品准入 LLM（选模板）→ query_similar_offer（相似产品 offerInfo）→ **CODE_GET_TEMPLATE 模板注册表选配**（/方案/templates/_registry.json）→ 要素提取 LLM → **CODE_VALIDATE_ELEMENTS 要素提取质量门禁**（validate_elements）→ **CODE_MERGE_NESTED 嵌套合并**（merge_nested）→ **CODE_OP_VALIDATE_NESTED 本体校验闸**（/validate-nested，R-C04/C06）→ **CODE_RENDER_TABLE 渲染四列表格**（render_table）→ 保存 requirement（node_name=requirement）→ 结束 |
| 智能配置（配置落地） | `wf_sub_02` | req_id（必填） | offer_id / save_result（含可选 group） | 开始(req_id) → query_node_result(自查 requirement) → CODE_EXTRACT_RECORD(取 result_json 原文) → save_product_config(配置落地) → **CODE_FUSION_GROUP_ECHO 融合组成员回显**（含可选 group/role/offer_id 成员集）→ **save_node_result(node_name=config)** → 结束 |
| 规格稽核（组维度） | `wf_sub_03` | req_id（必填） | pass / error_list / audit_summary | 开始(req_id) → 自查 config（取 offer_id/config_json）→ CODE_EXTRACT_RECORD → realtime_spec_audit(实时稽核, **组维度稽核**, error_list 含 group:role 定位) → 大模型(整改建议生成) → **save_node_result(node_name=spec)** → 结束 |
| 资费校准（成员分组） | `wf_sub_05` | req_id（必填） | pass / risk_list | 开始(req_id) → 自查 config（取 config_json）→ CODE_EXTRACT_RECORD → check_billing_rule(check_scene=all，**按 member_role 分组比对，E27 负值比对**) → 大模型(风险解读) → **save_node_result(node_name=fee)** → 结束 |
| 自动测试（含受理验证，重写） | `wf_sub_04` | req_id（必填） | globalId / 测试报告（九章节正式版） | 开始(req_id) → 自查 config（取 offer_id）→ offer_test(发起) → get_test_scenes → **CODE_POLL_PROGRESS 轮询进度**（type=6，asyncio.sleep 5s×360/30 分钟）→ get_test_result → **CODE_MAP_FIXED_CASES 确定式 31 条固定用例映射**（ACC/BILL/CUST + P0/P1 + 缺省 + E26 核对）→ 九章节正式版测试报告 → **CODE_DOWNLOAD_TEST_REPORT 报告下载链接**（/report/download）→ **save_node_result(node_name=test)** → 结束 |
| 上线审批（双轨） | `wf_sub_06` | req_id（必填） | approval_id / status | 开始(req_id) → 自查 四环节结果 → **CODE_SUMMARY_APPROVAL 审批汇总**（requirement/launch 双轨：requirement 轨=需求工单审批，launch 轨=上线审批，后端硬校验四环节）→ submit_release_approval(推送审批) → 审批通过后**自动上线 + 生成监控运维方案**（CODE_DOWNLOAD_LAUNCH_SCRIPT 上线脚本下载 /script/download，/*run@crm*/+/*run@billing*/）→ 结束 |
| 监控运维（异常分支） | `wf_sub_07` | offer_id / date_range | 指标与告警 / 工单号 | 开始 → query_product_monitor → 选择器(异常?) → 异常分支 **CODE_OP_ROOT_CAUSE 根因推理**（/ops/root-cause）→ **CODE_OP_CREATE_WO 建工单闭环**（/ops/work-orders）→ 结束；正常分支 send_alert/汇总 |
| 审批进度查询（双轨） | `wf_sub_08` | approval_id / offer_id | 审批状态摘要 | 开始 → query_approval_status（**双轨**：approval_id 或 offer_id）→ 大模型(状态摘要归纳) → 结束 |
| 存量产品查询（只读） | `wf_sub_09` | 产品名称 / 产品 ID | 存量销售品信息 | 开始 → **CODE_OP_QUERY_OFFER 只读查询** → query_similar_offer / query_node_result（只读，不落库不写）→ 结束 |
| 存量合规扫描 | `wf_sub_10` | 存量产品范围 | 合规扫描结论 | 开始 → **CODE_OP_SHELF_COMPLIANCE 合规扫描**（/shelf-compliance）→ 结束 |

> **V2.1 实现状态说明**：上表 11 个子工作流 JSON 为 V2.1 重塑后的当前实现基线（`工作流配置/智能体工作流集V1.6/`，`gen_workflows_v2.py` 生成；另有 2 份合并 export JSON 供平台联调参考）。原 `wf_main_intent` 意图调度主流程、平台子工作流（wf_sub_01~08）与 Skills 技能包（SKILL.md/flow-A~D/scripts 子命令）均已废弃（可依 git 历史回溯），后续演进以 11 子工作流 JSON + 智能体提示词语义调度 + knowledge/ 知识库 + 后端 `/api/v1/appstore/*` 适配端点为准。
>
> 说明：① 测试轮询采用 **type=6 代码节点**（CODE_POLL_PROGRESS，asyncio.sleep 间隔 5s、超时 30 分钟；注意平台代码节点 inputs 须为平铺 list 结构）；稽核为实时接口无需轮询。② 环节结果存储节点 req_id 均引用开始节点 req_id，node_name 与 6.1 节调度时序一致（requirement/config/spec/fee/test）；wf_sub_06 审批推送节点 req_id 引用开始节点，配合后端 `submit_release_approval` 硬校验。③ req_id 唯一批次标识全链路贯穿（PLAN+yyyyMMddHHmmss+3 位随机），不再使用 plan_id/execution_id 双键。④ 节点结果存储后端已落库持久化（pd_ai_node_results 表，H2/MySQL 双 DDL），服务重启不丢失。

### 6.3 关键节点配置要点

**① 需求分析大模型节点（需求分析助手，V1.2 重构；V2.3 重构为 7 环节新链路；@deprecated 本小节为 V2.3 平台工作流历史口径，V2.0 起以 wf_sub_01 模板轨为准）**
- 温度值：0.2
- 输入：`requirement_text`（+ `query_similar_offer` 返回的相似产品列表）
- 链路（V2.3）：**环节1 需求理解与要素拆解**（仅提取要素：24 字段未提及项 value 填空字符串，不做完整性判断、不输出 pending_fields）→ **环节2 相似产品查询**（入参=要素摘要 need_summary）→ **环节3 产品信息整合**（匹配产品全量配置 + 要素部分信息 → 最终产品信息，source 仅"原始需求/AI推理"）→ **环节4 字段本体推理**（工具14 reason 一体推理）
- 补全规则（V3.0 口径）：**仅价格类字段（套餐档位）未提取到 → "待补充"（禁止推理、禁止从相似产品照搬）**；套餐编码 → 不补全，值填"系统待生成"；其余缺失字段按取值链补全——相似产品 offerInfo 同构 24 字段（source"AI推理"）→ 本体推理引擎默认值（source"AI补全"）
- 输出：`plan_json`(string，执行方案 JSON，经**节点结果存储查询插件·结果存储**保存，key=req_id) + `plan_md`(string，五列模块表格：模块/分类/字段名称/字段值/备注)
- 执行方案 JSON 结构（建议，24 字段节选）：
```json
{
  "req_id": "PLAN20260914174633293",
  "fields": [
    {"field": "套餐名称", "category": "产品属性", "value": "校园青春卡", "source": "原始需求"},
    {"field": "套餐编码", "category": "产品属性", "value": "系统待生成", "source": "原始需求"},
    {"field": "套餐档位", "category": "产品属性", "value": "29元", "source": "原始需求"},
    {"field": "国内通用流量", "category": "套餐内基础资源", "value": "30GB", "source": "原始需求"},
    {"field": "付费方式", "category": "计费/支付/风控", "value": "后付费", "source": "AI补全"},
    {"field": "退订规则", "category": "变更/退订/拆机", "value": "允许退订，次月生效", "source": "AI补全"}
  ],
  "similar_offers": [{"similarOfferId": "900102308", "similarityScore": "0.92"}],
  "pending_fields": []
}
```
- 补全判定口径（V3.0）：仅套餐档位未提取到 → "待补充"；套餐编码 → 值填"系统待生成"（不计入 pending_fields）；其余字段（付费方式、生效方式、退订规则、销售渠道、资源类等）→ 基于最高相似度产品 offerInfo 对应字段值推理，标记"AI推理"（CODE_RENDER_TABLE 归一显示【AI补全】）；皆缺失时留空交引擎默认值补全。

**② 用户确认交互节点（V1.2 新增，V1.7 调整为智能体对话层，V2.1 由智能体提示词【调度纪律】识别）**
- 子工作流 wf_sub_00/wf_sub_01 输出执行方案表格 + 提示语："请核对以上执行方案，回复【确认执行】将触发智能配置落地；如需调整请直接说明修改意见。"
- 用户确认语义识别：由智能体提示词【调度纪律】识别确认词表"确认配置/确认执行/同意/可以/执行吧"等（未命中确认语义不触发执行主干）；确认类回复串行直调执行主干子流（无需写 CONFIRMED 确认标记、无需经主工作流注入 confirmed 参数）。

**③ 智能配置节点（V1.2 重构：直读 JSON，不重新生成；V1.6 调整为子工作流 req_id 自查；V2.2 加提取代码节点+门禁移除）**
- 节点链：开始节点(req_id) → **query_node_result（GET，req_id=开始节点入参，node_name=requirement）** → **代码节点提取 list[0].result_json 原文（V2.2 新增，CODE_EXTRACT_RECORD）** → `save_product_config`
- 配置项：按 req_id 自查从节点结果存储取回执行方案记录，**代码节点提取 list[0].result_json（执行方案对象原文）** 后透传 `save_product_config`（query_node_result 出参 list 是记录数组，禁止整体透传），**中间不得插入任何大模型节点/改写节点**，保证分析结果与落地配置一致；方案 key 由后端从 plan_json 的 `req_id` 键提取。
- 确认语义（V2.2/V2.1）：`save_product_config` **已移除确认门禁**（不再校验 confirmed 入参与存储 CONFIRMED 标记）——"未确认不配置"约束由智能体提示词【调度纪律】的确认语义识别保证，后端只做幂等与 plan_json 合法性校验。

**④ 实时稽核节点（V1.3 重构：实时接口，无文件上传/无轮询；V1.6 调整为子工作流 req_id 自查；V2.2 修复取值断链）**
- `wf_sub_03` 子工作流：开始节点(req_id) → query_node_result 自查 config（V2.2：按 req_id+node_name=config 读取环节结果记录）→ **代码节点提取 result_json 原文（V2.2）** → 单插件节点 `realtime_spec_audit`（入参 `offer_id + config_json`，**同步返回**稽核结果；offer_id/config_json 全部取自 config 环节结果）→ 大模型整改建议 → save_node_result(node_name=spec)。
- 判定：大模型节点根据 `pass`/`error_list` 提炼"通过/驳回 + 整改建议"；不通过走 `send_alert` + 终止分支。
- 原文件上传（audit_file_upload）、异步报告（audit_report_notify）、插入记录（audit_result_insert）、SQL 轮询（query_audit_by_sql）四个工具**不再使用**（保留在接口清单备查，接口清单中对应文件上传类接口暂不封装）。

**⑤ 智能测试节点组（V1.3：发起动作，报告含受理验证；V1.6 调整为子工作流 req_id 自查 + 代码节点轮询；V2.2 修复取值断链）**
- `wf_sub_04` 子工作流：开始节点(req_id) → query_node_result 自查 config（V2.2：读取 config 环节结果记录）→ **代码节点提取原文（V2.2）** → `offer_test`(offerId) **发起**（offerId 取自 config 环节结果）→ `get_test_scenes`(globalId) → **type=6 代码节点轮询 `get_test_progress`**（inputs 平铺 list；asyncio.sleep 间隔 5s、超时 30 分钟；done==true 退出；failed==true 提前退出并定位 failIndex）→ `get_test_result` → 大模型汇总测试报告 → save_node_result(node_name=test)
- **受理验证内嵌于测试结果**：`get_test_result` 返回的 `orderId`（受理订单号）、`offerInstId`（销售品实例 ID）证明实际受理成功；逐场景受理测试点（套餐新装 S_O_TC/副卡加装 S_ADD_CARD/套餐退订 S_U_TC，测点 P_EFF_DATE 生效时间、P_EXP_DATE 失效时间、P_STATUS 实例状态、P_RELY_REL 依赖关系、P_MUTEX_REL 互斥关系、P_ORD_CNT 订购数量、P_OFFER_NAME 名称、P_PAY_MODE 付费方式等）的比对结论即为受理验证结论，须写入测试报告。
- 测试报告模板（大模型节点）：测试流水/场景数与测点统计/逐场景明细/AI 总结与建议/**受理验证结论**（orderId、offerInstId、各受理场景是否通过）。

**⑥ 选择器节点（通用）**
- 条件模板：`{环节结果字段} 等于 通过值` → 走"通过"分支；否则走"驳回"分支。
- 支持且/或多条件（如 confirmed==true 且 req_id 非空；pass==1 且 failed==0；approve_confirmed==true）。

**⑦ 报告生成节点（V1.6 调整：移入 wf_sub_06 子工作流内部）**
- 位置：`wf_sub_06` 上线审批子工作流内（LLM 节点 501g），主流程不再单独设置报告汇总节点。
- 前置：子工作流以 req_id 单入参进入，**串行自查 5 类环节结果**（node_name=config/spec/fee/test + 需求摘要 requirement），代码节点合成结构化汇总后交给 LLM。
- 提示词要点：`请按标准模板汇总生成《销售品上线测试与稽核报告》，强制包含 7 章节：1.需求摘要 2.配置落地结果 3.稽核结论 4.资费结论 5.测试统计 6.受理验证结论（引用 orderId/offerInstId 与逐受理场景比对结果）7.上线建议。全通过→"建议上线"，任一环节未通过→"暂缓上线"。只基于输入数据生成，不得新增结论。`
- 输出：`report`(string) → 先 save_node_result 落库（node_name=report，req_id=入参 req_id）→ 再传给 `submit_release_approval` 的 `report_url/report` 字段。

**⑧ 串行结果打印与异常处置（V1.5 新增，V1.7/LLM 调度，V2.1 由智能体提示词【调度纪律】约束）**
- **环节结果打印**（V1.7）：每个环节子流返回后，由智能体按 6.1 节"每环节结果打印格式"模板输出该环节执行结果（引用出参原文，不凭语义推断成败）。
- **异常处置**（V1.7）：环节异常时立即中断调度，按"异常处置输出格式"模板输出异常环节名称、异常原因（引用接口返回原文，不臆测）、关键明细与整改建议，并引导【重新执行】/【修改执行方案】，不得自行重试。
- **续跑映射**（V1.7）：`重新执行` → 按 fail_node 续调（`STAGE1_CONFIG`→wf_sub_02、`STAGE2_AUDIT`→wf_sub_03、`STAGE3_FEE`→wf_sub_05、`STAGE4_TEST`→wf_sub_04）；已成功环节不重复调用（凭存储记录回放）。
- **环节结果存储（V1.7，下沉到子工作流）**：wf_sub_02~05 各内置 save_node_result 环节结果存储节点（req_id=入参 req_id，node_name=config/spec/fee/test），子流执行成功即自动落库（后端 pd_ai_node_results 表持久化），调度层无需补存；wf_sub_06 审批自查依赖此数据，`submit_release_approval` 后端硬校验四环节（config/spec/fee/test）结果齐全。

**⑨ 审批发起确认（V1.5 新增，V1.7/LLM 调度，V2.1 由智能体语义识别触发）**
- 四环节全部成功后，智能体输出"成功结果详情 + 是否发起上线审批"提示，等待用户回复。
- 用户回复"发起审批/确认上线" → 智能体语义识别命中"发起审批"直调 `wf_sub_06`（入参 req_id=上下文沿用原值：子流串行自查 5 类环节结果 → 子流 LLM/代码节点生成 7 章节报告 → 报告存储 → 审批推送，**审批推送节点 req_id 引用开始节点**）→ 输出审批单号。
- 用户回复"暂不" → 提示可稍后发送"发起审批"继续（执行方案与各环节结果均以 req_id 同键存储，随时可续）。
- 兜底（V1.7）：`submit_release_approval` **后端硬校验** req_id 入参 + 四环节（config/spec/fee/test）结果齐全，跳过执行主干直接发起审批将被拒绝。

**⑩ 消息查询实现（V1.5 新增，V2.1 由智能体语义识别直调）**
- **审批进度查询**：用户发送含"审批进度/审批状态"语义的消息 → 智能体语义识别"查询审批进度" → 直调 `wf_sub_08`（approval_id 优先，缺失时按 offer_id 查最新审批单）→ 大模型归纳输出"审批单号+状态+当前环节+审批意见"。
- **监控运维结果查询**：用户发送含"监控/运行监控/运维结果"语义的消息 → 智能体语义识别"查询监控运维" → 直调 `wf_sub_07`（offer_id 必填，为空时提示用户提供销售品 ID）→ 大模型归纳输出"订单量/异常量/计费差错率/告警列表"；异常时（error_count>0 或 fee_error_rate>阈值）进入根因推理（ops_root_cause）与建工单（create_work_order）闭环。

### 6.4 需求分析助手提示词（@deprecated，V1.2 历史版本，已被 V2.0 wf_sub_01 模板轨取代）

> **废弃声明（V3.0）**：本节为 V1.2~V2.3 时代的旧提示词摘录（以下含旧口径字段描述，仅供历史追溯，不再维护），
> 现行唯一权威：`wf_sub_01` 需求分析子工作流（模板轨）+ `knowledge/ontology-fields.md`/`ontology-fields.json`（3 模块/9 分类/24 字段注册表）+ 后端 FieldOntologyService。源文件已归档至 `方案/_deprecated/`。

> 以下提示词全文配置在 `wf_sub_01` 需求分析大模型节点（及智能体技能 1）。因篇幅较长，此处摘录核心段落，完整版以源文件为准。

```
# 角色
你是一名"产销品加载需求分析助手"。任务：将用户提交的自然语言需求、需求文件、
附件等，转换为可直接进入"销售品智能配置"环节的结构化产销品加载方案。
核心工作：业务需求 → 理解为业务要素 → 映射为实际配置字段 → 对缺失字段合理补全
→ 输出可执行的产销品加载执行方案。不直接操作CRM，不直接修改配置。

# 分析流程（7环节，V2.3）
环节1 需求理解与要素拆解（仅提取输入中的要素信息）→
环节2 相似产品匹配（调用 query_similar_offer，businessDesc=要素摘要）→
环节3 产品信息整合（匹配产品全量配置 + 要素部分信息 → 最终产品信息）→
环节4 本体推理（字段本体推理引擎修正产品信息）→
环节5 结构化输出（产品配置参数信息，缺值来源标注：原始需求/AI推理）
环节6 节点存储 → 环节7 结束

# 字段映射体系（四类）
1. 基础信息：产品属性/产品名称/产品编码/上线日期/下线日期/生效规则/产品有效期/
   展示周期/退订生效规则/同时订购次数/累计订购次数/是否上线统一运营/
   一级产品编码/是否通信类/申请依据/是否允许受理渠道等
2. 资源配置：流量资源类型/流量资源/流量单位/套外计费标准/是否结转至次月/
   语音资源类型/语音资源/语音单位/套外计费费率/短信资源/短信单位/
   短信套外计费费率等
3. 营销资源：收费方式/套餐固定费/收费科目/税率/优惠条件/优惠类型/优惠费用/
   保底方式等
4. 销售规则：渠道类型/适用地区/其他费用/违约责任/缴费续订方式/前项限制/
   前项限制关系/前项限制条件/后项限制/产品限制/业务限制/限制时间等
（映射示例："39元/月"→ 收费方式=月付 + 套餐固定费=39元，来源=原始需求；
 "包含5GB国内流量"→ 流量资源类型=国内流量 + 流量资源=5GB，来源=原始需求）

# 信息完整性与来源规则
字段三态：原始需求 / 可AI推理 / 待补充；引擎处理后新增第四态：本体推理。
- **取值链与本体推理引擎（V2.1，字段本体由后端推理引擎统一保证；V2.3 环节化）**：
  逐级降级取值：**原始需求 → 相似产品（最高相似度） → 本体默认值（推理引擎补全） → 待补充**，
  禁止跳级虚构。环节3 产品信息整合只负责前两级（需求要素有值→原始需求；无值→相似产品取值；
  两者皆缺失→留空）。字段形态由后端**字段本体推理引擎**（工具14 field_ontology_reason，
  FieldOntologyService，/api/v1/appstore/ontology/fields）注册表统一约束与校验：
  产品属性=基础/可选/增值；收费方式=按月/按量/一次性（月付/包月归一为按月）；
  渠道类型=实体渠道/电子渠道/直销渠道；适用地区=全国（不含港澳台）/指定省份；
  计费周期=自然月；销售品状态=在售/待上线（新需求一律填"待上线"）；
  优惠条件/优惠期无优惠填"无"；副卡规则无参照填"不允许办理副卡"；
  退订规则默认"允许退订，次月生效，当月费用不退还"；数值类字段必须带单位
  （GB/分钟/条/元）。LLM 环节3 整合后由 wf_sub_01 字段本体推理节点（环节4，工具14 **action=reason 一体推理，串行闭环**）
  逐字段执行：本体校验→非法值修正回写（枚举归一/渠道同义词映射/套餐固定费单位补全/
  产品名称 K1 模板归一/生效日期格式归一/资源类缺单位补全等，值不符合规则自动修正）→
  缺失与待补充字段按本体默认值推理补全（V2.2：**待补充项全部可推理**——退订规则/短信资源/
  优惠条件/优惠期/适用地区/计费周期等均按默认口径补全，**仅套餐固定费价格维持"待补充"**
  交用户确认）；方案输出拆分节点以推理后 fields_json 为准组装 plan_json（引擎兜底闭环，
  无法自动修正项经 violations 在结束节点提示用户）。
- **待补充判定（V2.2，唯一不可推理项=套餐固定费）**：
  需求与相似产品均未提取到时先填"待补充"（环节3/引擎间中间态），
  本体推理引擎对"待补充"字段按本体默认值推理补全（三类资源→"无"、
  优惠条件/优惠期→"无"、适用地区→全国、计费周期→自然月、退订规则→默认口径等），
  **仅套餐固定费（价格）维持"待补充"**——价格禁止推理与照搬，经 pending_fields
  提示用户在确认执行方案时补充。
  **pending_fields 判定单一事实源（V2.2）**：由本体推理引擎推理后字段数组
  反查（value=待补充）在方案输出拆分节点 004a 统一生成，环节3 LLM 不再
  自行判定 pending_fields（固定输出空数组），避免双重判定取值不一致导致
  待补充项判断分支路由歧义。
- **产品编码不做补全**：编码由智能配置环节落地后生成，需求未提供时
  字段值填"由智能配置生成"（不计入 pending_fields）。
- **除上述待补充情形外的其他缺失字段，按取值链取值**：以
  query_similar_offer 返回的相似产品（及知识库存量销售品资料）中该字段的
  稳定取值为依据（须符合本体推理引擎枚举/格式口径），相似产品亦缺失时
  留空由本体推理引擎按默认值补全；环节3 相似产品取值来源标"AI推理"，
  引擎补全/修正后的字段 source 统一改标"本体推理"。
- 来源标注（V2.3 缺值来源口径）："原始需求"=需求原文提取（LLM 环节1/3 标注）、
  "AI推理"=相似产品取值（LLM 环节3 标注）、"本体推理"=引擎按本体默认值
  推理补全或非法值修正回写（引擎自动标注）；禁止"系统默认/推测/猜测/
  历史产品"等其他来源。

# 最终输出
1. 需求理解（1~2句话）
2. 分析结果（已识别要素、匹配到的相似产品、基于相似产品推理补全了哪些字段、
   待补充信息——仅套餐固定费未提取到或三类资源全部未提取到的情形，
   提示用户在确认时补充）
3. 《产销品加载执行方案》Markdown 表格（固定4列：字段分类/字段名称/字段值/
   来源；同分类连续行合并显示）
同时生成执行方案 JSON 并调用节点结果存储查询插件（结果存储）保存，返回 req_id。

# 重要约束
需求原文最高优先级；不得虚构用户未提供的业务规则；历史产品只作参考不能覆盖
当前需求；字段名称尽量与实际配置工作台一致；
**仅当套餐固定费（月租费）未提取到，或流量/语音/短信三类资源一个都未提取到时
填"待补充"（禁止推理）；产品编码不补全（由智能配置生成）；其他缺失字段必须
基于相似产品推理补全（来源"AI推理"），不允许留空或待补充**；
不执行CRM配置、不做稽核/资费校准/自动测试、不判断能否上线；
最终输出必须能直接作为"销售品智能配置"的输入。
```

> 需求分析与提报的交互模式：用户上传需求文档（如 Excel/Word）或口述需求后，智能体不展示内部推理过程，按"需求理解 → 分析结果 → 执行方案表格"三段输出，并附确认提示。参考产品可在方案前简要说明（如"参考相似产品：5G-A套餐199元(900102308)，相似度0.92"），但表格来源列仍只填"原始需求/AI推理"。

### 6.5 工作流 JSON 实现（V2.1 重塑后：当前实现基线）

> 本节为 6.1~6.3 节平台工作流描述的 V2.1 代码实现等价物（对应关系见 1.4 节实现方式说明）。当前实现基线与 `工作流配置/智能体工作流集V1.6/` 导出的 11 个子工作流 JSON 一致，由 `gen_workflows_v2.py` 确定性生成；业务逻辑与第 2~6 章平台工作流口径严格等价，仅是「实现载体」迁移——原 `wf_main_intent` 意图调度主流程、`skills/cpcp-product-worker/` 技能包（SKILL.md / scripts / references/flow-A~D）与脚本子命令均已废弃，化为工作流 type=6 代码节点 + 智能体提示词语义调度 + `knowledge/` 知识库 + 后端 `/api/v1/appstore/*` 适配端点。

#### 6.5.1 工作流 JSON 集结构（11 个子工作流，V2.1）

```
工作流配置/智能体工作流集V1.6/            # 11 个子工作流 JSON 集（gen_workflows_v2.py 确定性生成；无意图调度主流程）
├── wf_sub_00_需求提报               # 要素提取→render_requirement_report→需求工单审批(approval-type=requirement)
├── wf_sub_01_需求分析               # 模板轨：要素提取→validate_elements→merge_nested→validate_nested→render_table→保存 requirement
├── wf_sub_02_智能配置               # 自查 requirement→save_product_config→CODE_FUSION_GROUP_ECHO 融合成员回显
├── wf_sub_03_规格稽核               # 组维度稽核 realtime_spec_audit
├── wf_sub_04_自动测试               # 重写：31 条固定用例 ACC/BILL/CUST + P0/P1 + 九章节正式版报告
├── wf_sub_05_资费校准               # 成员分组 check_billing_rule(check_scene=all)
├── wf_sub_06_上线审批               # 双轨 requirement/launch + 审批通过自动上线 + 监控运维方案
├── wf_sub_07_监控运维               # 异常分支：ops_root_cause 根因 + create_work_order 建工单闭环
├── wf_sub_08_审批进度查询           # 双轨 query_approval_status
├── wf_sub_09_存量产品查询           # 只读 CODE_OP_QUERY_OFFER
└── wf_sub_10_存量合规扫描           # shelf_compliance（/shelf-compliance）
```

> 说明：上表 11 个子工作流均为完整独立工作流（自带开始节点/req_id 入参/自查链路/结束节点），由智能体按 3.2 提示词【意图→工作流映射表】语义识别后直调；不再有 `wf_main_intent` 意图调度主流程。

#### 6.5.2 子工作流 ↔ 关键代码节点对照（V2.1）

| 子工作流 | 关键代码节点（type=6） |
| --- | --- |
| 需求提报 `wf_sub_00` | CODE_RENDER_REQ（render_requirement_report 需求提报单渲染）；需求工单审批 approval-type=requirement |
| 需求分析 `wf_sub_01` | CODE_EXTRACT_RECORD → CODE_VALIDATE_ELEMENTS（validate_elements 质量门禁）→ CODE_MERGE_NESTED（merge_nested）→ CODE_OP_VALIDATE_NESTED（/validate-nested 本体校验闸）→ CODE_RENDER_TABLE（render_table）→ CODE_GET_TEMPLATE（templates 注册表选配） |
| 智能配置 `wf_sub_02` | CODE_EXTRACT_RECORD（提取执行方案原文）→ CODE_FUSION_GROUP_ECHO（融合成员回显，含可选 group） |
| 规格稽核 `wf_sub_03` | CODE_EXTRACT_RECORD（取 config 出参原文） |
| 资费校准 `wf_sub_05` | CODE_EXTRACT_RECORD（取 config 出参原文） |
| 自动测试 `wf_sub_04` | CODE_POLL_PROGRESS（轮询测试进度）→ CODE_MAP_FIXED_CASES（31 条固定用例映射）→ CODE_DOWNLOAD_TEST_REPORT（/report/download 下载） |
| 上线审批 `wf_sub_06` | CODE_SUMMARY_APPROVAL（requirement/launch 双轨汇总）→ CODE_DOWNLOAD_LAUNCH_SCRIPT（/script/download 上线脚本下载） |
| 监控运维 `wf_sub_07` | CODE_OP_ROOT_CAUSE（/ops/root-cause 根因推理）→ CODE_OP_CREATE_WO（/ops/work-orders 建工单闭环） |
| 审批进度查询 `wf_sub_08` | query_approval_status 双轨（approval_id/offer_id，GET /api/v1/appstore/approval/status） |
| 存量产品查询 `wf_sub_09` | CODE_OP_QUERY_OFFER（只读，query_similar_offer / query_node_result） |
| 存量合规扫描 `wf_sub_10` | CODE_OP_SHELF_COMPLIANCE（/shelf-compliance） |

#### 6.5.3 意图调度与执行主干（智能体语义识别直调摘要）

意图路由规则与 3.2 节【意图→工作流调度映射表】等价：首次提报/修改需求→wf_sub_00→wf_sub_01；确认执行/重新执行失败环节→串行直调 wf_sub_02→03→05→04（重新执行按 fail_node 映射：STAGE1_CONFIG→wf_sub_02、STAGE2_AUDIT→wf_sub_03、STAGE3_FEE→wf_sub_05、STAGE4_TEST→wf_sub_04，req_id 沿用原值）；发起审批→wf_sub_06；审批通过自动上线+监控运维方案→wf_sub_06 尾段；审批进度查询→wf_sub_08；监控运维→wf_sub_07（异常分支根因闭环）；存量查询→wf_sub_09；存量合规扫描→wf_sub_10；业务问答→按 K1~K5 知识库检索；超范围→按答案为空提示回复。

调度纪律（确定性程序化，替代原 SKILL.md 核心纪律）：确认语义未命中不得进入执行主干；执行主干必须串行、严禁跳过环节、严禁重复调用已成功环节（续跑时已成功环节按存储记录回放）；仅依据后端出参字段判定成败（status/pass/test_passed），失败即中断引导；审批后端四环节硬校验兜底。

#### 6.5.4 后端适配端点与代码节点通用行为（等价原插件封装层）

- 请求侧裸报文（业务参数 JSON 置于顶层，V2.7 起 contractRoot/tcpCont 包裹整体移除，出参侧兼容解包保留，见 4.2 节通用要求）；
- 超时：同步类 60s（稽核类重试 1 次）、异步轮询类 30s；网络错误重试 1 次；写操作（save_product_config）不自动重试防重复写入；
- 错误码归一输出：PARAM_MISSING / HTTP_xxx / NET_ERROR / TIMEOUT / PARSE_ERROR / ONTOLOGY_EMPTY；出参 JSON 原样打印，模型逐字引用不加工；
- **type=6 代码节点统一约定**：确定性逻辑（merge_nested/render_table/validate_elements/get_template/render_requirement_report/map_fixed_cases/extract_record/dispatcher/poll_progress/fusion_group_echo 等）同一份 Python 逻辑整体内嵌于工作流 JSON（CODE_*），取代原技能包脚本子命令，inputs 须为平铺 list 结构（见《工作流JSON开发规范.md》）；
- 代码节点行为要点：CODE_RENDER_TABLE 由推理后 fields_json 重新生成四列表格（与字段严格一致）；CODE_MAP_FIXED_CASES 确定式 31 条固定用例映射（ACC/BILL/CUST + P0/P1）；CODE_POLL_PROGRESS 轮询测试进度（asyncio.sleep 5s×360/30 分钟）；CODE_EXTRACT_RECORD 提取 list[0].result_json 原文（查无报 E5，禁止数组整体透传）；
- 字段本体推理（ontology_reason）空返回防护：resultCode=0 但 fields_json 为空数组/空串时报 **ONTOLOGY_EMPTY**，提示引擎推理能力缺失/未实现，禁止跳过该步骤直接组装方案（E2b）；render_table 同步拒绝空 fields 组装（V2.7 基于实测反馈新增）；
- 大报文（plan_json/config_json/fields/report）一律经节点结果存储（req_id+node_name）或后端适配端点下载（/report/download、/script/download）传递，不经模型上下文中转；
- 网关 BASE_URL=http://10.86.13.201:31281（AppStoreV16Controller `/api/v1/appstore/*` 适配端点），替换真实实现仅改此值。

#### 6.5.5 按需加载与上下文成本控制

1. 智能体常驻提示词精简（3.2 节角色/职责/映射表/调度纪律/限制），子流/知识库命中意图后才加载对应单份；
2. K4 存量销售品（`knowledge/`）18 份文件禁止全量读取，仅按 similarOfferId 精确读取单文件；
3. 知识检索由向量召回改为文件按需读取（K1~K3/K5 文档量小全文读取可控），无切片断裂与召回参数调优问题；
4. 报告/整合类生成步骤在子流 LLM 节点提示词中加"逐字引用输入数据，不新增结论"约束（等价原温度 0.2 大模型节点的结构性任务纪律）。

#### 6.5.6 配置规范符合性（对齐《SitechAI开发平台配置规范.md》）

> 本节将《SitechAI开发平台配置规范.md》（`场景设计/SitechAI开发平台配置规范.md`，下称"配置规范"）逐条映射到本方案 12 个工作流 JSON 与插件/端点的落点，作为"全量对齐"的符合性自证。凡与本规范冲突处，一律以配置规范为准。

| 配置规范条款 | 本方案落点（对齐自证） |
| --- | --- |
| **平台四层架构**（智能体/插件/工作流/知识库） | 本方案即为四层落地：智能体（3 章产销品数字员工，含语义调度）、插件（4 章自研 8 工具 + V2.0 后端适配端点）、工作流（6.5 章 11 个子工作流 JSON）、知识库（5 章 K1~K5 + `knowledge/` 结构化资产）。 |
| **源码驱动纪律**（JSON 是生成器产物、禁止手改、改生成器后重新生成 + 全量校验 ALL_OK） | 11 个子工作流 JSON 全部由 `gen_workflows_v2.py` 确定性生成（6.5.1），插件由 `gen_plugins.py` 生成；改业务逻辑一律改生成器后重新生成并做 JSON 契约校验（导入平台自动校验 ALL_OK 项，见交付运维手册 2.3）。**禁止直接手改 `智能体工作流集V1.6/*.json`**。 |
| **节点 type 全集**（0开始/1LLM/2条件分支/3HTTP插件/6代码节点/9结束/13子流程） | 全部 11 个子工作流仅使用上述 type 值；无 type=5 以及任何未枚举类型；无**循环节点**（type 全集之外的循环类型不出现），轮询统一用 type=6 代码节点 CODE_POLL_PROGRESS（asyncio.sleep 5s×360/30 分钟，见 6.5.4）。 |
| **入参两态**（常量 vs 引用） | 各子流开始节点（type=0）入参以常量/上游引用两种形态按需配置：固定值（如平台参数）为常量；跨节点取值一律为引用（见《工作流JSON开发规范.md》入参约定）。 |
| **引用"三层一致"**（blockID、nameValue[0]、currValue 前缀均为上游 id；`nameValue[1]`/`currValue`=上游 id+出参名） | 跨节点引用（如 CODE_EXTRACT_RECORD 读上游 config 节点结果）严格满足三层一致：`currValue`/`nameValue[0]` 均以上游开始节点 ID 为前缀，`nameValue[1]`=上游 id+出参名（如 `{上游id}.config_json`）。 |
| **array 出参必须配 item 树**（ARRAY_ITEM_FIELDS 白名单） | 出参中凡 array 型（如 CODE_MAP_FIXED_CASES 的 fixed_cases、query_similar_offer 的映射结果）均配置 item 树，字段名取自配置规范 ARRAY_ITEM_FIELDS 白名单（id/name/value/desc 等），禁止无 item 树的裸数组。 |
| **命名 snake_case**（offerId 为唯一 camelCase 特例） | 节点出参、代码变量统一 snake_case（config_json/need_summary/elements_json/plan_json/req_id/fixed_cases 等）；仅后端相似产品标识保留 `offerId`（配置规范特许的 camelCase 特例），凡跨契约的其余参数一律 snake_case。 |
| **条件分支 sourcePort**（sourcePort=-1 否则/0 如果） | 全部 2 条件分支节点：主分支（命中条件）sourcePort=0，否则支 line sourcePort=-1；首节点 type=0（开始）的 else 支 line sourcePort=-1。 |
| **nid UUID 形态**（`a1b2c3d4-0000-4000-8000-{12位seq}`，seq 按子流分段） | 生成器按规范生成 nid：各子流 `wf_sub_00`~`wf_sub_10` 的 seq 分段递增，满足"单次生成全库唯一、seq 按子流分段"约束。 |
| **HTTP 插件走网关 BASE_URL** | 4.2/6.5.4：插件与代码节点统一以 `BASE_URL=http://10.86.13.201:31281` 为前缀，通过网关访问 AppStoreV16Controller `/api/v1/appstore/*` 适配端点；替换真实实现仅改 BASE_URL。 |
| **代码节点 type=6**（`async def main(args)`、args.params、urllib.request 用 BASE_URL 占位符、后端不可达一律 `backend_pending=1` 优雅回退、含 `_json` 出参名存 JSON 字符串） | 所有确定性逻辑均内嵌 type=6 代码节点（6.5.2），形态：`async def main(args)` + `args.params` 取入参；urllib.request 相对路径拼接 `BASE_URL` 占位符；后端不可达/异常一律返回 `backend_pending=1` 并输出降级文案（保离线 Demo，见 6.5.4 错误码归一）；JSON 型出参（config_json/plan_json/report_json 等）命名带 `_json` 后缀存 JSON 字符串。 |
| **LLM 节点纪律**（温度 0.2/top_p 0.5/max_tokens 2048/model qwen3-30b-a3b/提示词末尾"仅输出对应出参"） | 各子流 LLM 节点（type=1）统一模型 `qwen3-30b-a3b`、温度 0.2、top_p 0.5、max_tokens 2048（见 7 阶段 5 模型参数）；提示词末尾统一追加"仅输出对应出参字段"约束（3.2/6.5.5 结构性任务纪律的显式落点）。 |
| **LLM 出参绑定契约**（实测） | 平台回包 `{出参名: <模型返回>}`；模型返回**纯文本**整段填单出参；返回 **JSON 对象**则按出参名键匹配取值。**单出参承载 JSON 文本**（如 `elements_json`）必须让模型只输出顶层键=出参名、值为该 JSON 字符串（`{"elements_json":"{\"...\"}"}`），禁止输出裸字段对象（否则出参空）；多字段应声明多出参。详见《工作流JSON开发规范.md》§10.1。 |
| **无循环节点**（轮询用代码节点 asyncio.sleep 5s×360/30min） | 仅 CODE_POLL_PROGRESS 一处轮询（5s×360，30 分钟），以代码节点实现，无平台循环节点（见本表"节点 type 全集"行）。 |
| **req_id 统一贯穿**（PLAN+yyyyMMddHHmmss+3 位随机，代码节点系统时钟生成，LLM 不参与） | req_id 由代码节点用系统时钟生成（见 6.5.4/细化设计 6 章），LLM 节点不参与生成；同一批次方案/执行共用单键覆盖（V1.7 定稿），全链路贯穿（见 1.3/附录 A）。 |
| **状态/续跑回放靠 save_node_result + query_node_result 自查链路** | 各子流内置 save_node_result（node_name=config/spec/fee/test/requirement 等）+ CODE_EXTRACT_RECORD 按 req_id+node_name 自查（6.5.2/6.5.4），续跑按 fail_node 映射回放已成功环节。 |
| **优雅回退保离线 Demo** | 后端不可达一律 backend_pending=1 + 降级文案（见"代码节点 type=6"行），端到端演示剧本含离线回退观察点。 |
| **最佳实践：确认门禁取舍**（后端硬门禁改交 LLM 语义识别） | V2.2 已移除后端确认门禁，确认与否交智能体提示词【调度纪律】语义识别（见 8.1/8.2）；**审批保留四环节硬校验**（submit_release_approval 校验 config/spec/fee/test 结果齐全）。 |
| **最佳实践：取值断链**（query_node_result list 记录数组须代码节点提取 `list[0].result_json` 原文） | CODE_EXTRACT_RECORD 固定提取 `list[0].result_json`（V2.2 根除数组整体透传，见 6.5.2/6.5.4）。 |
| **最佳实践：出参单一事实源 / 字段补全引擎化 / 模拟结果兼容** | 方案输出单一数据源=推理后 fields_json（V2.2 改代码组装）；字段补全/修正全部引擎化（FieldOntologyService，见 4.4 工具14）；自研接口模拟结果兼容《产品信息.txt》18 套餐（见 2.1/4.2）。 |

> **符合性结论**：本方案 12 工作流 JSON 与插件/端点在上述全部配置规范条款上显式对齐，无未枚举节点类型、无平台循环节点、无 camelCase 违规（除特许 offerId）、无裸数组出参；改配置一律走生成器重新生成 + 全量校验，禁止手改 JSON。

## 7. 开发实施计划

| 阶段 | 工作项 | 产出 | 建议工期 |
| --- | --- | --- | --- |
| 阶段1 基础搭建 | 创建助手/知识分类；确认后端 `knowledge/` 知识资产（K1~K5、ontology-fields.json、seed_offer_groups.json、templates 注册表）、`/api/v1/appstore/*` 适配端点（AppStoreV16Controller）与网关 BASE_URL（http://10.86.13.201:31281）；确认「节点结果存储查询插件」可用性 | 知识库冷启动资产、接口核对记录、网关配置 | 3天 |
| 阶段2 后端适配端点开发 | 既有 14 个契约端点 + V2.0 新增 7 个适配端点（/ops/root-cause、/ops/work-orders、/shelf-compliance、/validate-nested、/explain、/report/download、/script/download）逐一联调（裸报文请求契约、异步测试轮询、实时稽核同步返回）；确定性逻辑产出为 `gen_workflows_v2.py` 生成载体 | 后端适配端点就绪、工作流生成脚本 | 5天 |
| 阶段3 知识库建设 | 规范文档采编、《产品信息.txt》5G-A 销售品资料切片入库（knowledge/）、FAQ 编制、切片校验 | K1~K5 知识库 + 结构化资产可用 | 3天 |
| 阶段4 工作流编排 | 由 `gen_workflows_v2.py` 生成 **11 个子工作流 JSON**（`wf_sub_00`~`wf_sub_10`，无 `wf_main_intent` 意图调度主流程，见 `工作流配置/智能体工作流集V1.6/`）并导入调试；各子流 type=6 代码节点（CODE_EXTRACT_RECORD/CODE_VALIDATE_ELEMENTS/CODE_MERGE_NESTED/CODE_RENDER_TABLE/CODE_MAP_FIXED_CASES/CODE_POLL_PROGRESS 等）、req_id 单必填自查链路、环节结果存储节点联调 | 已生成并导入的 11 个子工作流 JSON | 6天 |
| 阶段5 智能体集成 | 提示词、插件、工作流、知识库装配；模型参数调优 | 已发布智能体 | 2天 |
| 阶段6 验证与优化 | 以《产品信息.txt》销售品改写需求样例跑通全流程；验证"未确认不配置"约束（智能体确认语义识别，V2.2）、智能体语义串行直调的连续性与结果打印、异常中断引导与续跑、审批发起门禁（四环节硬校验）、消息查询（审批进度/监控结果）、测试报告受理验证结论完整性；提示词迭代 | 验证报告 | 4天 |
| 合计 | — | — | 约22个工作日 |

## 8. 测试与验收

### 8.1 分层测试
1. **插件级**：逐工具在"预览与调试"中验证接口连通、入参提取、出参归纳正确。重点：API 接口裸报文请求契约（业务参数置于顶层）、实时稽核接口同步返回、节点结果存储查询插件保存/查询一致、测试流水 globalId 传递。
2. **工作流级**：
   - 正向用例：完整合规需求（取《产品信息.txt》中某销售品资费规则改写）→ 执行方案生成（节点结果存储保存）→ **用户确认（智能体识别确认语义，V2.2 起无需写标记）** → 智能体语义直调串行执行主干（wf_sub_02→03→05→04，每环节打印结果，中途不停顿）→ 全部成功后打印成功详情并提示"是否发起审批" → 用户确认 → 审批单生成；
   - 反向用例：**未确认执行方案直接要求配置 → 智能体按提示词拒绝调度智能配置（确认语义识别）**；**主干全部成功后未确认直接要求审批 → 调度 wf_sub_06 后 submit_release_approval 因四环节结果校验拒绝/按提示词先引导确认**；构造资费冲突（如叠加优惠互斥）→ 资费校准拦截并中断主干；构造配置缺陷 → 实时稽核驳回（pass=0）中断主干；
   - 串行中断用例（V1.5/V1.7）：稽核不通过 → 验证智能体串行调度中断、打印异常节点"配置规格稽核"+原因+建议、引导重新执行/修改执行方案；选择【重新执行】→ 验证从稽核环节续调且不重复落地；选择【修改执行方案】→ 验证回到需求分析；
   - 硬校验用例（V1.7 新增，V2.2 修订）：跳过环节直接调 submit_release_approval（四环节结果不全）→ 校验拒绝；
   - 消息查询用例（V1.5）：审批推送后发送"查询审批进度"→ 返回审批单状态/当前环节；发送"查询监控结果"→ 返回监控指标与告警；
   - 分支用例：执行方案修改后覆盖保存并重新确认分支、测试失败(failed=true)分支（校验报告含失败定位与受理失败原因）、告警分支、审批"暂不"后隔轮续办分支。
3. **智能体级**：多轮对话体验（追问、执行方案确认交互、结构化输出）、引导问题命中、FAQ 回答准确率。

### 8.2 验收标准（建议）
| 指标 | 目标 |
| --- | --- |
| 端到端流程贯通率 | 100%（给定需求→执行方案→确认→执行主干自动串行（配置落地→实时稽核→资费→测试报告含受理验证）→成功详情+审批确认→审批单生成） |
| 确认门禁有效性 | 未确认时配置落地触发率 = 0%（智能体确认语义识别，V2.2）；未确认发起审批时审批推送触发率 = 0%（四环节结果校验）（V1.5/V2.2） |
| 串行调度纪律 | 正常情况下智能体语义调度串行完成四环节一次，中途无多余人工询问、无并行调用、无跳步；每环节结果打印率 100%（V1.5/V1.7/V2.1） |
| 异常处置完整性 | 异常中断时 100% 输出异常节点名称、异常原因、整改建议，并给出重新执行/修改执行方案引导（V1.5） |
| 续跑正确性 | 重新执行时已成功环节不重复执行，续跑起点与失败环节一致（V1.5） |
| 消息查询可用性 | 审批进度查询、监控结果查询命中率 100%（意图可识别、结果可返回）（V1.5） |
| 需求要素映射准确率（模块/分类/名称/值/备注） | ≥95%（以《产品信息.txt》销售品为基准样例；字段名须与 24 字段注册表一致，出现旧口径字段名（产品名称/套餐固定费/渠道类型等）计为映射失败） |
| AI补全字段标记正确率 | 100%（备注仅"原始需求/AI补全"两种，无违规来源） |
| 待补充规则符合率 | 100%（仅价格类套餐档位未提取到时填"待补充"，其余字段无"待补充"残留且均按取值链补全；套餐编码均为"系统待生成"） |
| 稽核/资费问题拦截率 | ≥95%（构造缺陷样例集，实时稽核同步返回） |
| 测试场景覆盖（即受理验证覆盖） | 覆盖 offerTest 匹配的全部场景（套餐新装/副卡加装/套餐退订等），测点比对结果完整回显 |
| 受理验证结论完整性 | 测试报告 100% 包含 orderId/offerInstId 及逐受理场景通过/失败结论 |
| 全流程耗时 | 相比人工缩短 ≥60% |

### 8.3 端到端演示剧本（V1.1 新增，V1.2 更新）
- 剧本文件：《产销品加载AI应用-端到端演示剧本.md》
- 用途：现场演示、验收评审、宣传展示；按幕走通 9 环节（示例产品可选用《产品信息.txt》中 5G-A 系列或 P20260010"畅享5G通用包"）。
- 剧本与验收的映射关系：
| 剧本环节 | 对应验收内容 | 演示方式 |
| --- | --- | --- |
| 幕1 需求提报/分析 | 需求要素映射准确率 + 执行方案生成 | 输入需求（可参考 5G-A 套餐 199 元资料改写）→ 输出执行方案表格 + req_id（节点结果存储保存） |
| 幕2 用户确认 | 确认门禁有效性 | 先演示"未确认要求配置→拒绝"，再回复"确认执行"→ 触发执行主干自动串行 |
| 幕3 执行主干串行（V1.5） | 串行连续性 + 每环节结果打印 | 一次运行连打四环节结果（智能配置→稽核→资费→测试），中途不停顿 |
| 幕3A 异常中断与引导（V1.5） | 异常处置完整性 + 续跑正确性 | 注入稽核缺陷 → 中断并打印异常节点/原因/建议 → 演示【重新执行】续跑 |
| 幕3B 成功详情与审批确认（V1.5） | 审批发起门禁 | 四环节成功详情打印 → 提示是否发起审批 → 确认后审批单生成 |
| 幕4 稽核+资费校准 | 稽核/资费问题拦截率 | 实时稽核接口单步调用同步出结果 + 零元月租红线反向演示 |
| 幕5 自动测试（含受理验证） | 测试场景/测点覆盖 + 受理验证结论 | offerTest 发起 → 场景/进度/结果三接口轮询 → 测点统计 + AI 总结 + orderId/offerInstId 受理验证结论输出 |
| 幕6-7 上线审批 | 端到端贯通 | 报告汇总（含受理验证）+ 审批单号输出 + **消息查询审批进度演示（V1.5）** |
| 幕8 监控运维 | 运维闭环 | 监控指标输出 + 可选 high 告警演示 + **消息查询监控结果演示（V1.5）** |
- 剧本附"反向分支速查表"，验收时按表逐项核验拦截能力（V1.2 新增"未确认不配置"一条），与 8.2 拦截率指标直接对应。

## 9. 上线与运维

1. **发布**：智能体发布到小思页面；工作流、插件保持版本可回退（平台支持保存草稿/发布双状态）。
2. **监控**：每日定时（或由运维对话触发）运行 `wf_sub_07` 监控子工作流，对 error_count 超阈值、fee_error_rate 异常的销售品调用 `send_alert` 告警；用户可随时发送"查询监控结果"消息触发即时查询（V1.5）。
3. **审批跟踪**：审批推送后，用户可随时发送"查询审批进度"消息调用 `query_approval_status` 查询审批状态（V1.5）；审批驳回时数字员工同步驳回原因并引导修改后重新发起。
4. **迭代机制**：
   - 知识库持续更新（新规范、新资费政策）；
   - 提示词与选择器阈值按稽核/测试误判情况调优；
   - 后端适配端点与工作流代码节点（`knowledge/`、`gen_workflows_v2.py`、11 子工作流 JSON）按系统升级同步维护。
5. **运维报表**：每周汇总全流程执行量、拦截量、测试通过率，评估数字员工效能。

## 10. 风险与对策

| 风险 | 对策 |
| --- | --- |
| 接口不稳定/字段变更 | 插件层隔离；出参加必填校验；维护接口版本；自研模拟接口以《产销品场景部分能力接口清单.xlsx》契约为基线，替换真实实现时契约不变 |
| 实时稽核接口响应慢/超时 | 插件层设置接口超时与重试（建议 60s 超时、重试 1 次）；超时转人工提示并保留请求报文供人工重放 |
| 自动测试长时间未完成 | 轮询代码节点设超时上限（30 分钟），超时转人工提示并保留 globalId 供人工续查 |
| 大模型解析幻觉 | 知识库增强 + 温度调低 + 结构化 JSON 输出 + 字段来源标记（原始需求/AI推理）+ 实时稽核兜底 |
| 配置落地与执行方案不一致 | 智能配置禁止大模型二次加工，直读节点结果存储 JSON 原样透传给 save_product_config；落地后可按 plan_json 人工抽查比对 |
| 用户未确认即触发生产写入 | 智能体提示词【技能2】确认语义识别约束（V2.2 起后端 save_product_config 已移除 CONFIRMED 门禁，"未确认不配置"由智能体语义识别保证） |
| 执行主干串行中断后重复执行已完成写操作 | 各子工作流以 req_id（统一键）、node_name=config/spec/fee/test/report 落库节点结果存储（后端持久化）；续跑时子工作流自查回放已成功环节，写接口不重复调用 |
| 未经确认发起审批 | V1.7 工具层硬校验：后端 submit_release_approval 校验 req_id 四环节（config/spec/fee/test）结果齐全 + 智能体提示词【技能4】审批引导约束 |
| 消息查询意图未命中 | 智能体提示词【技能5】明确"审批进度/审批状态/监控/运行监控"触发词；查询工具入参缺失时提示用户补齐 offer_id/approval_id |
| 测试报告缺受理验证结论 | 大模型报告节点提示词强制要求包含 orderId/offerInstId 与逐受理场景结论；验收按 8.2 指标核验 |
| 生产误操作 | 写入类插件前置用户确认；测试仅对接测试环境 |
| 资费漏洞漏检 | 知识库规则持续运营 + `check_scene=all` 全量校验 |
| 流程环节被跳过 | 双保险：智能体提示词串行纪律（严禁跳过环节、严禁凭语义推断成败）+ submit_release_approval 四环节结果硬校验（审批环节跳步即被拒绝）；配置落地环节依赖确认语义识别（V2.2） |

---

## 附：快速实施 Checklist

### A. 工作流 JSON 路线（V2.1 重塑后当前基线：11 子工作流 JSON + 智能体语义调度 + knowledge/ + 后端适配端点）
- [x] 知识资产迁至 `knowledge/` 目录：K1~K5 知识库、`ontology-fields.json`/`ontology-fields.md`、`seed_offer_groups.json`、templates 注册表（原 `references/`、`skills/` 废弃）
- [x] 后端 AppStoreV16Controller `/api/v1/appstore/*` 适配端点就绪（既有 14 个契约端点 + V2.0 新增 7 个：/ops/root-cause、/ops/work-orders、/shelf-compliance、/validate-nested、/explain、/report/download、/script/download；网关 BASE_URL=http://10.86.13.201:31281）
- [x] `gen_workflows_v2.py` 生成 **11 个子工作流 JSON**（`wf_sub_00`~`wf_sub_10`，无 `wf_main_intent` 意图调度主流程，见 `工作流配置/智能体工作流集V1.6/`）并导入
- [x] 确定性逻辑内嵌为 type=6 代码节点（CODE_EXTRACT_RECORD/CODE_VALIDATE_ELEMENTS/CODE_MERGE_NESTED/CODE_RENDER_TABLE/CODE_GET_TEMPLATE/CODE_RENDER_REQ/CODE_MAP_FIXED_CASES/CODE_POLL_PROGRESS/CODE_FUSION_GROUP_ECHO/CODE_OP_ROOT_CAUSE/CODE_OP_CREATE_WO/CODE_OP_SHELF_COMPLIANCE/CODE_OP_VALIDATE_NESTED/CODE_SUMMARY_APPROVAL/CODE_OP_QUERY_OFFER 等），取代原技能包脚本子命令
- [x] 智能体按 3.2 提示词【意图→工作流映射表】语义识别直调（需求提报/确认配置/失败续跑/上线审批/审批进度/监控运维/存量查询/存量合规/QNA 等），确定性纪律由【调度纪律】+ 后端硬校验兜底
- [x] 环节覆盖：需求提报（wf_sub_00 含需求工单审批 approval-type=requirement）、需求分析（wf_sub_01 模板轨）、智能配置（wf_sub_02 融合成员回显）、规格稽核（wf_sub_03 组维度）、资费校准（wf_sub_05 成员分组）、自动测试（wf_sub_04 31 条固定用例+九章节报告）、上线审批（wf_sub_06 双轨+自动上线+监控方案）、监控运维（wf_sub_07 根因闭环）、审批进度查询（wf_sub_08 双轨）、存量查询（wf_sub_09 只读）、存量合规（wf_sub_10）
- [ ] 14 个既有契约端点＋7 个适配端点逐一连通后端（含错误码 PARAM_MISSING/5002/ONTOLOGY_EMPTY 验证）
- [ ] 端到端联调：正向全流程 + 反向用例（未确认不配置、跳步审批被拒、稽核驳回中断引导、续跑不重复写）+ 18 销售品兼容回归（对齐 8.1/8.2 节）
- [ ] 按《端到端演示剧本》完成全流程彩排（含反向分支）
- [ ] 发布上线并接入监控运维

### B. 平台工作流路线（历史实现存档，V1.7~V2.5）
- [ ] 创建助手 `cpcp_product_worker`
- [ ] 13 个自研插件工具全部完成（模拟结果输出），模拟数据适配《产品信息.txt》全部 18 个销售品套餐；确认实时稽核接口（普通接口）地址与报文
- [ ] 确认平台「节点结果存储查询插件」可用（执行方案 JSON 保存/查询）
- [ ] 创建插件「产销品加载插件集」+ 12 个工具（6 API + 6 自研，V1.5 新增 query_approval_status）并发布
- [ ] 开发并联调自研接口：save_product_config（配置落地）、query_approval_status（审批进度查询）
- [ ] 创建 5 个知识分类并上传规范/FAQ（含《产品信息.txt》5G-A 销售品资料切片入库）
- [ ] 编排 8 个子工作流并发布（V1.7 LLM 智能调度模式：智能体直调，主流程 wf_cpcp_main 已删除）
- [ ] 后端工具层硬校验就绪：NodeResultService.latestRecord + submit_release_approval 四环节门禁（save_product_config 确认门禁已按 V2.2 移除）
- [ ] 配置需求分析程序提示词（~~按 `skills/cpcp-product-worker/references/flow-A-requirement.md`~~ + `ontology-fields.md` 24 字段口径；~~旧 6.4 节/《需求分析工作流可参考提示词.txt》已废弃~~；V2.0 起以 `wf_sub_01` 模板轨 `knowledge/ontology-fields.md/.json` + 后端 FieldOntologyService 为唯一权威）
- [ ] 智能体装配：提示词（含 V1.7 意图→子工作流智能调度映射表）/插件/工作流/知识库/模型参数/开场白/引导问题（工作流仅挂载 8 个子流，不挂载主流程）
- [ ] 正反向用例全流程调试通过（重点验证：未确认不配置（智能体确认语义识别）、执行主干串行不停顿、每环节结果打印、异常中断与重新执行/修改执行方案引导、审批发起门禁、审批进度/监控结果消息查询、节点结果存储读写一致、实时稽核同步返回、测试报告含受理验证结论、wf_sub_02~05 提取原文代码节点取值正确）
- [ ] 按《端到端演示剧本》完成全流程彩排（含反向分支）
- [ ] 发布上线并接入监控运维
