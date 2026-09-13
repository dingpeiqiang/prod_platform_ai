# 产销品加载 AI 应用 · 细化设计方案
> 平台：AI应用开发（九思大模型 · 低代码智能体平台）
> 场景：安徽电信 CPCP 产销品域 · 数字员工（必选场景）
> 版本：V1.3　日期：2026-09-13
> 依据文档：《产销品加载AI应用开发方案.md》（V1.7，下称"主方案"）

## 版本记录
| 版本 | 日期 | 变更说明 |
| --- | --- | --- |
| V1.0 | 2026-09-12 | 首版：插件细化设计（6 个 HTTP API 工具 + 节点结果存储查询插件 + 5 个自研工具的完整配置）、工作流细化设计（主流程 + 7 个子工作流逐节点配置）、知识库细化设计（5 个分类的文档清单/切片策略/召回参数/维护机制）、智能体装配清单、变量命名约定、异常处理矩阵 |
| V1.1 | 2026-09-12 | 对齐主方案 V1.5：① 主工作流执行主干改为**自动化串行**（智能配置→稽核→资费→测试中途不停顿），新增每环节结果打印节点、统一异常处置节点（异常A）、续跑判定选择器（resume_action/fail_node/execution_id）；② 全部成功后新增"成功结果详情汇总"节点与"审批发起确认"中断点（approve_confirmed 门禁），审批改为用户确认后发起；③ 新增工具13 `query_approval_status`（审批进度查询）与 `wf_sub_08` 审批进度查询子工作流，支持消息查询审批进度与监控运维结果；④ 异常处理矩阵同步更新（E17~E23） |
| V1.2 | 2026-09-12 | 对齐主方案 V1.6：① 工具1~6 定位调整为**全部自研实现并采用模拟结果输出**，清除外部 ApiID 依赖，2.1 节标题与各工具定义同步改写；② 工具7~13 补充模拟实现说明，与工具1~6 统一为自研插件集；③ 新增 2.5 节"模拟结果兼容性要求"：模拟种子数据覆盖《产品信息.txt》全部 **18 个销售品**（原 K4 记载 9 条为误，已更正），测试预期值 presetValue 取自该销售品规则值；④ K4 知识库条目 9→18 更正（4.2.1/4.2.3/第 5 章/附录 C）；⑤ 自测用例补 18 套餐兼容性用例（附录 E）；⑥ 《产销品场景部分能力接口清单.xlsx》降级为接口契约参考 |
| V1.3 | 2026-09-13 | 对齐主方案 V1.7（LLM 智能调度模式）：① 智能体装配改为**仅挂载 `wf_sub_01~08` 八个子工作流**，主流程 `wf_cpcp_main` 弃用归档（1.2/3.0/第 5 章同步）；② 3.3 时序说明重写为"LLM 智能调度三段式时序"（确认标记→串行直调→审批引导），新增【意图→子工作流智能调度映射表】替代旧【意图→主工作流入参注入映射表】；③ 3.4.3 门禁条件改写为工具层硬校验（CONFIRMED 标记/四环节结果）+ LLM 串行纪律；④ wf_sub_02/wf_sub_06 相关节点说明同步（CONFIRMED 门禁、四环节校验）；⑤ 异常矩阵 E3/E4/E15 触发点更新；⑥ 附录 B/C 核对清单同步 |
| V1.4 | 2026-09-13 | 对齐主方案 V2.0（**本体+相似产品组合补全**）：① 知识分类 5→6（新增 **K6 字段本体库**，四类18字段类型/枚举/格式/默认规则/兜底口径）；② K6 挂载 wf_sub_01 节点2/节点4（4.1/4.2.1/4.3 同步）；③ 3.4.2 重写为"组合补全策略 + 字段形态校验 + 待补充判定"；④ 6.3 枚举值约定新增 K6 本体口径 |
| V1.5 | 2026-09-13 | 对齐主方案 V2.1（**字段补全改为本体推理引擎实现**）：① 删除 K6 字段本体库，知识分类 6→5（恢复 K1~K5）；② 3.4.2 组合补全策略改写为「取值链 + 后端字段本体推理引擎（工具14 field_ontology_reason，**action=reason 一体推理**：校验+修正回写+默认值补全）」；③ wf_sub_01 新增「字段本体推理」节点（节点4 后**串行闭环**，方案输出拆分以推理后 fields_json 为准）；④ 4.1/4.2/4.3/6.3 同步（K6 行删除、知识库挂载恢复 K1~K5） |
| V1.6 | 2026-09-13 | 对齐主方案 V2.2（**确认门禁移除 + 子工作流取值断链修复**）：① 工具7 `save_product_config` **移除确认门禁**——删除 confirmed==true 校验与存储 CONFIRMED 标记校验（NodeResultService.existingNodes 一并删除），NOT_CONFIRMED 状态不再出现；确认与否由智能体 LLM 语义识别保证；② wf_sub_02 新增「提取执行方案原文」代码节点（query_node_result 出参 list 记录数组 → 提取 list[0].result_json 原文再传 save_product_config）；③ wf_sub_03/04/05 取值断链修复：新增 query_node_result 自查（req_id+node_name=config）+ 提取原文代码节点，offer_id/config_json 改从 config 环节结果取值；④ 3.2/3.3/3.4.3/7.2/附录 C 同步（CONFIRMED 相关表述更新为 V2.2 口径）；⑤ **待补充项判定收归引擎单一事实源**：wf_sub_01 方案输出拆分节点 004a 的 pending_fields 改为从本体推理引擎返回的推理后字段数组反查（value=待补充），不再采信节点4 LLM 自判的 pending_fields；节点4 提示词同步——pending_fields 固定输出空数组，待补充判定职责移交引擎；⑥ 字段本体推理引擎修正增强：渠道类型多选归一补同义词映射表（营业厅/门店/实体→实体渠道，APP/网厅/线上/电子→电子渠道，直销/客户经理/政企→直销渠道）；reason 修正项补 defaulted=0 统一 fixed 明细动作结构；⑦ **待补充项全部可推理 + 值不符合规则自动修正**：本体注册表补齐 18 字段默认值（生效日期→立即生效、三类资源→无、适用地区→全国、计费周期→自然月、生效日期/资源单位格式修正等），推理引擎对 value=待补充 字段一律按默认值推理补全（仅套餐固定费价格维持待补充）；correctValue 新增产品名称 K1 模板归一（"5G-A 套餐"→"5G-A单品套餐待定档位元"）、生效日期 yyyyMMdd 归一、资源类缺单位补全（60G→60GB）；工具14 导出契约描述同步 V2.2 口径；⑧ **来源标注新增"本体推理"**：引擎补全/修正后的字段 source 由"AI补全"改标"本体推理"，来源三态扩展为四种（原始需求/AI补全=LLM 节点4 标注，本体推理=引擎自动标注）；节点4 提示词与结束节点文案同步 |

## 文档定位与阅读指引
| 章节 | 内容 | 面向读者 |
| --- | --- | --- |
| 第 1 章 | 与主方案的对应关系、总体装配视图 | 全体 |
| 第 2 章 | **插件细化设计**：每个工具的完整配置项（接口/入出参/为空提示/是否提参/出参归纳/超时/错误处理/tcpCont 拼装） | 插件开发人员 |
| 第 3 章 | **工作流细化设计**：主流程逐节点说明（V1.7 弃用归档） + 8 个子工作流逐节点配置（节点类型/参数引用/循环伪代码/选择器条件/温度/提示词）+ 3.3 LLM 智能调度时序 | 工作流编排人员 |
| 第 4 章 | **知识库细化设计**：5 个知识分类的文档清单、切片策略、命名规范、召回参数、维护机制（V1.5 恢复 K1~K5，字段本体改由推理引擎承载） | 知识运营人员 |
| 第 5 章 | 智能体装配清单 | 平台配置人员 |
| 第 6 章 | 变量命名约定 | 全体 |
| 第 7 章 | 异常处理矩阵 | 全体 |
| 附录 | 实施核对清单 | 项目管理 |

> 约定：本文档与主方案保持术语一致。文中"插件"指平台插件工具；`req_id`（V1.7 统一键，原 plan_id/execution_id 双键合并）指执行方案与各环节结果在节点结果存储中的 key；`globalId` 指测试流水号。

---

## 1. 总览

### 1.1 与主方案的对应关系
| 主方案章节 | 本细化文档对应章节 | 细化内容 |
| --- | --- | --- |
| 2.1 原子能力→插件清单 | 第 2 章 | 每个工具补齐：为空提示、是否提参、出参归纳、超时、错误处理、tcpCont 拼装规则、调用方（哪个工作流节点） |
| 3 智能体设计 | 第 5 章 | 落地到平台界面的装配清单（逐项勾选） |
| 4 插件设计 | 第 2 章 | 同上 |
| 5 知识库设计 | 第 4 章 | 文档清单、切片大小/重叠、命名规范、召回参数、更新机制 |
| 6 工作流设计 | 第 3 章 | LLM 智能调度时序（3.3）+ 子工作流逐节点：节点类型、入参引用表达式、选择器条件、循环伪代码、大模型节点温度值与提示词；主流程逐节点配置为弃用归档参考 |
| 8 测试与验收 | 附录 | 实施核对清单（含插件级/工作流级自测项） |

### 1.2 总体装配视图（一图看全）
```
智能体 cpcp_product_worker（数字员工统一入口，V1.7 LLM 智能调度中枢）
  ├── 提示词（角色+技能+限制，主方案 3.2，含意图→子工作流智能调度映射表/确认语义识别（V2.2：无需写确认标记）/串行直调/异常引导/消息查询技能）
  ├── 插件（13 个自研插件集，V1.6 全部自研实现+模拟结果输出）：工具1~6（原 HTTP API 工具，清除外部 ApiID）+ 节点结果存储查询插件 + 工具7~11、13（含 V1.5 新增 query_approval_status）；后端含工具层硬校验（V2.2 起仅四环节校验，CONFIRMED 门禁/四环节门禁）
  ├── 工作流（8 个子工作流）：wf_sub_01~08（V1.7 由 LLM 按意图映射表直调；主流程 wf_cpcp_main 弃用、保留归档）
  ├── 知识库（5 个分类）：业务规范 / 资费规则 / 测试规范 / 存量销售品资料 / FAQ
  └── 模型参数：温度 0.2、多轮对话 20 轮、top_p 0.5
```

### 1.3 环节 → 插件 → 工作流 → 知识库 映射总表
| 业务环节 | 子工作流 | 调用插件（工具编号） | 知识库依赖 | 关键输出 |
| --- | --- | --- | --- | --- |
| 需求提报 | `wf_sub_01` 开始节点 | 无（文件上传为平台原生） | 产销品业务规范（引导话术） | requirement_text / requirement_file |
| 需求分析 | `wf_sub_01` | 工具1 `query_similar_offer`、节点结果存储查询插件·结果存储 | 业务规范 + **存量销售品资料库** | req_id / plan_md / plan_json |
| 用户确认 | 智能体 LLM（识别确认语义后直调子流，V2.2 起无需写标记） | —（无存储写入） | — | — |
| **执行主干自动化串行**（V1.5，V1.7 LLM 调度） | 智能体 LLM 串行直调（wf_sub_02→wf_sub_03→wf_sub_05→wf_sub_04 连续执行） | 见下列各环节行 | 各环节对应知识库 | 每环节结果打印；异常中断并引导重新执行/修改执行方案 |
| 智能配置 | `wf_sub_02` | 节点结果存储查询插件·结果查询、工具7 `save_product_config` | — | product_id / offer_id / save_result |
| 规格稽核（实时） | `wf_sub_03` | 工具2 `realtime_spec_audit` | 业务规范（稽核标准参照） | pass / error_list / audit_summary |
| 资费校准 | `wf_sub_05` | 工具8 `check_billing_rule` | **资费规则库** | pass / risk_list |
| 自动测试（含受理验证） | `wf_sub_04` | 工具3 `offer_test`、工具4 `get_test_scenes`、工具5 `get_test_progress`、工具6 `get_test_result` | **测试规范库** + 存量销售品资料库（预期值核对） | globalId / 测试报告（含受理验证结论） |
| 成功结果详情与审批确认（V1.5，V1.7 LLM 调度层） | 智能体 LLM（打印成功详情 + 等待确认后直调 wf_sub_06） | 无（汇总引用各环节输出） | — | 各环节成功详情 / 用户确认后发起审批 |
| 上线审批 | `wf_sub_06` | 工具9 `submit_release_approval` | — | approval_id / status |
| **审批进度查询**（V1.5） | `wf_sub_08`（或智能体直调工具13） | 工具13 `query_approval_status` | — | 审批状态 / 当前环节 / 意见 |
| **监控结果查询**（V1.5） | `wf_sub_07`（或智能体直调工具11） | 工具10 `query_product_monitor` | FAQ（运维问答） | 指标 / 告警记录 |
| 监控运维 | `wf_sub_07` | 工具10 `query_product_monitor`、工具11 `send_alert` | FAQ（运维问答） | 指标 / 告警记录 |

---

## 2. 插件细化设计

### 2.0 插件集公共约定（所有工具共用）

#### 2.0.1 插件集基本信息
| 项 | 值 |
| --- | --- |
| 插件名称 | 产销品加载插件集（V1.6：13 个工具全部自研实现，模拟结果输出） |
| 插件描述 | 封装产销品加载全流程所需的能力：相似度分析、实时规格稽核、自动化测试（发起/场景/进度/结果）、配置落地、计费校验、上线审批、监控告警；V1.6 起不再对接外部 ApiID，模拟结果须兼容《产品信息.txt》全部 18 个销售品（见 2.5） |
| 接口协议 | http/https |
| 鉴权方式 | 自研服务自行实现（token/签名由插件封装层处理，工作流不感知）；契约定义仍参考《产销品场景部分能力接口清单.xlsx》，便于后续替换真实实现 |

#### 2.0.2 tcpCont 报文头拼装规则（API 网关类工具统一适用）
以下字段由**插件封装层统一拼装**，工作流与大模型节点只透传业务参数，不出现 tcpCont：

| 字段 | 拼装规则 | 示例 |
| --- | --- | --- |
| `transactionId` | yyyyMMddHHmmssSSS + 4~6位随机数（当前毫秒时间戳） | `20260912102450123456` |
| `reqTime` | yyyyMMddHHmmssSSS | `20260912102450123` |
| `globalId` | 与 `transactionId` 同值 | 同上 |
| `version` | 固定 `V1.0` | `V1.0` |
| `sign` | 本项目写死占位 `123` | `123` |
| `svcCode` | 按接口要求填固定值（稽核类 `5012010056`） | `5012010056` |
| `appKey` | 按接口要求填固定值（稽核类 `eOrder1`） | `eOrder1` |
| `dstSysId` | 按接口要求填固定值（稽核类 `OrderCenter`） | `OrderCenter` |
| `apiCode` | 留空 | `""` |

报文结构（contractRoot 包裹）：
```json
{
  "contractRoot": {
    "tcpCont": { "...": "如上表" },
    "svcCont": { "requestObject": { "...": "业务参数" } }
  }
}
```

#### 2.0.3 通用超时与重试
| 项 | 同步类工具 | 异步轮询类工具 |
| --- | --- | --- |
| 单次请求超时 | 60s（`realtime_spec_audit` 建议 60s，超时重试 1 次） | 30s |
| 重试 | 网络类错误重试 1 次 | 由工作流代码节点轮询控制（见 3.4.4） |
| 超时后行为 | 返回 `resultCode=TIMEOUT`，工作流转人工提示并保留请求报文供人工重放 | 工作流按超时上限（30 分钟）终止轮询 |

#### 2.0.4 通用错误处理
| 错误类型 | 判定条件 | 插件层处理 | 返回给工作流 |
| --- | --- | --- | --- |
| 网络异常 | 连接失败/超时 | 重试 1 次后放弃 | `resultCode="NET_ERROR"`，`resultMsg=异常描述` |
| 网关错误 | HTTP 非 200 | 直接返回 | `resultCode="HTTP_"+状态码` |
| 业务失败 | `resultCode="1"` | 原样返回 | 透传 resultCode/resultMsg |
| 参数缺失 | 必填入参为空 | 拦截，不发起请求 | `resultCode="PARAM_MISSING"`，`resultMsg=缺失参数名` |
| 出参解析失败 | 报文结构不符合接口定义 | 记录原始报文 | `resultCode="PARSE_ERROR"` + 原始报文片段 |

#### 2.0.5 出参归纳（是否归纳总结）设置口径
- 查询类工具（工具2/4/5/6/10）：选"是/LLM"，由平台对出参做归纳提炼；
- 执行类工具（工具1/3/7/8/9/11）：选"否"，由工作流大模型节点统一汇总，避免二次加工；
- 平台复用插件（节点结果存储查询插件）：按平台默认。

#### 2.0.6 入参"为空提示"与"是否提参"通用口径
- **是否提参**：由大模型/工作流从上下文自动提取的参数选"是"；须由上一节点显式传入的参数选"否"（如 `config_json` 这类大报文，固定由工作流变量引用，不提参）。
- **为空提示**：每个必填参数必须填写，格式：`缺少{参数中文名}，请{获取方式}`（示例见各工具定义）。

---

### 2.1 自研能力接口工具定义（工具1~6，V1.6 全部自研模拟实现）

#### 工具1：相似度分析 `query_similar_offer`
| 项 | 配置 |
| --- | --- |
| 实现方式 | **自研模拟实现（V1.6）**：基于《产品信息.txt》18 个销售品构建相似度匹配模拟服务，返回结构化模拟结果；契约仍参考《产销品场景部分能力接口清单.xlsx》行2 定义，便于后续替换真实实现 |
| 接口 | POST（contractRoot 报文，tcpCont 按 2.0.2 拼装，svcCode/appKey/dstSysId 按契约填固定值） |
| 工具描述 | 通过业务需求描述查询相似销售品，返回相似产品列表及相似度评分。用于需求分析环节匹配历史产品，支撑 AI 补全 |
| 调用方 | `wf_sub_01` 需求分析子工作流（大模型节点之后、字段映射补全节点之前） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `businessDesc` | string | 是 | 是 | 缺少业务需求描述，请提供需求原文或需求文档摘要 | 业务需求描述文本，≤5000字符，超出由工作流节点先做摘要压缩 |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `resultCode` | string | 0 成功 / 1 失败 |
| `resultMsg` | string | 处理结果描述 |
| `similarOfferList` | array | 相似产品列表 |
| `similarOfferList[].similarOfferId` | string | 相似销售品 ID（如 900102308） |
| `similarOfferList[].similarOfferName` | string | 相似销售品名称 |
| `similarOfferList[].similarityScore` | string | 相似度评分（0~1） |
| `similarOfferList[].similarityDesc` | string | 相似原因描述 |

| 归纳 | 否（由 wf_sub_01 大模型节点消费，作为 AI 补全依据） |
| --- | --- |
| 超时/重试 | 60s / 重试 1 次 |
| 错误处理 | resultCode=1 时，wf_sub_01 继续走"无相似产品"分支（仅用知识库存量资料补全），不中断流程 |

#### 工具2：实时规格稽核 `realtime_spec_audit`
| 项 | 配置 |
| --- | --- |
| 实现方式 | **自研模拟实现（V1.6）**：解析配置 JSON 与《产品信息.txt》规则库比对，同步返回模拟稽核结果；契约仍参考《产销品场景部分能力接口清单.xlsx》定义 |
| 接口 | POST（contractRoot 报文，tcpCont 按 2.0.2 拼装，svcCode=5012010056 / appKey=eOrder1 / dstSysId=OrderCenter） |
| 工具描述 | 配置落地后按销售品/配置内容实时发起稽核，同步返回稽核结果（通过/驳回 + 问题明细 + 整改建议） |
| 调用方 | `wf_sub_03` 规格稽核子工作流稽核节点（自查 config 取 offer_id/config_json 后调用；V1.7 由 LLM 串行直调子流触发） |

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
| 接口 | POST（requestObject 报文） |
| 工具描述 | 发起动作：按销售品 ID 发起自动化测试（异步执行，测试平台自动完成受理类场景执行与受理验证），返回测试流水 globalId |
| 调用方 | `wf_sub_04` 自动测试子工作流首节点 |

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
| 接口 | POST（requestObject 报文） |
| 工具描述 | 测试发起后查询本次测试匹配的测试场景集合（即受理验证覆盖范围，如 套餐新装（C网）/副卡加装/套餐退订（C网）） |
| 调用方 | `wf_sub_04`（offer_test 之后、轮询之前） |

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
| 接口 | POST（requestObject 报文） |
| 工具描述 | 轮询测试任务当前步骤、是否完成、是否失败 |
| 调用方 | `wf_sub_04` 代码节点0304 轮询调用（间隔 5s，超时 30 分钟） |

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
| 超时/重试 | 30s / 不重试（由代码节点0304 轮询控制） |
| 错误处理 | 单次查询失败不终止，下一轮重查；连续 5 次失败终止循环转人工 |

#### 工具6：查询测试结果 `get_test_result`
| 项 | 配置 |
| --- | --- |
| 实现方式 | **自研模拟实现（V1.6）**：按 globalId 生成逐测点比对明细，**presetValue 取自该销售品在《产品信息.txt》中的规则值**，testValue 模拟生成（默认与 presetValue 一致，可构造不一致用例），受理凭证 orderId/offerInstId 模拟生成；契约仍参考接口清单行10 |
| 接口 | POST（requestObject 报文） |
| 工具描述 | 测试全部完成后查询逐场景测试点比对明细与 AI 场景总结；orderId/offerInstId 为实际受理生成的订单号/销售品实例 ID，作为受理验证结论依据 |
| 调用方 | `wf_sub_04`（done=true 后调用一次） |

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

### 2.2 平台复用插件（不自研）：节点结果存储查询插件（V1.6 更新）

| 项 | 配置 |
| --- | --- |
| 来源 | 平台已有通用插件，直接挂载，**不自研**（V1.3 起替代原自研 save_plan_json/get_plan_json）；V1.6 起后端由 `NodeResultService`（MyBatis-Plus）落库 `pd_ai_node_results` 表持久化（H2/MySQL 双 DDL），服务重启结果不丢失 |
| 保存（save_node_result） | POST `/api/v1/appstore/result/save`，入参 req_id/node_name/result_json/status（默认 ok）；**V1.7 环节结果存储已下沉到各子工作流内部**：wf_sub_01(req_id=入参, node_name=requirement)、wf_sub_02~05(内置存储节点, req_id=入参统一键, node_name=config/spec/fee/test)、wf_sub_06 报告存储(node_name=report) |
| 查询（query_node_result） | **GET** `/api/v1/appstore/result/query`，入参 req_id（必填）/node_name（可选）/latest_only（默认1）；出参 code/msg/total/list（取 list[0].result_json 为结果原文）；**各子工作流开始后自查上游环节结果（submit_way=get）** |
| key（req_id）规范 | V1.7 统一键：执行方案与执行主干共用单键 `PLAN` + yyyyMMddHHmmss + 3位随机数（如 `PLAN20260913143025087`，由 wf_sub_01 拆分代码节点以系统时钟生成、每次唯一，LLM 不参与生成）；同键**覆盖写**；后端硬校验 PLAN 格式（5002）与唯一性冲突（5006） |
| node_name 枚举 | requirement / config / spec / fee / test / **report**（V1.6 新增 report） |
| value 大小 | result_json ≤64KB（超限返回 5004）；执行方案 JSON 一般 <20KB，超限时压缩仅保留 fields/similar_offers/pending_fields 三段 |
| 读写一致性自测 | 保存后立即按 req_id+node_name 查询，比对 result_json 一致；服务重启后可查询（持久化验证） |

---

### 2.3 自研能力接口工具定义（工具7~11、13，V1.6 统一自研+模拟结果输出）

#### 工具7：配置落地 `save_product_config`（V1.2 核心新增）
| 项 | 配置 |
| --- | --- |
| 接口 | POST `https://{cpcp-gateway}/api/v1/appstore/product/config/save` |
| 实现方式 | **自研模拟实现（V1.6）**：写入模拟 CRM 销售品配置库（内存/存储模拟），生成 product_id/offer_id，模拟结果兼容 18 个销售品；**V2.2 确认门禁已移除**（详见下方） |
| 工具描述 | 读取执行方案 JSON，将基础信息/资源配置/营销资源/销售规则四类字段写入 CRM 销售品配置 |
| 调用方 | `wf_sub_02` 智能配置子工作流（**唯一写入节点，中间无任何大模型节点**） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `req_id` | string | 是 | 是 | 缺少执行方案key，请先完成需求分析并确认执行方案 | 执行方案存储 key（V1.7 统一键，方案key由后端从 plan_json 的 req_id 键提取） |
| `plan_json` | string | 是 | 否（工作流变量引用） | 缺少执行方案JSON，请先完成需求分析并确认 | **必须为节点结果存储查询插件取回的 JSON 原文，原样透传** |
| `operator` | string | 否 | 是 | 可为空 | 操作人（从会话上下文取） |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `product_id` | string | CRM 产品 ID |
| `offer_id` | string | 销售品 ID（后续稽核/测试入参） |
| `save_result` | object | 各字段分类写入结果（基础信息/资源配置/营销资源/销售规则 各自 success/fail 及原因） |
| `status` | string | SUCCESS / PARTIAL / FAIL |

| 归纳 | 否 |
| --- | --- |
| 超时/重试 | 60s / **不自动重试**（写操作防重复写入；失败由用户重新触发） |
| **确认门禁（V2.2 移除）** | 原 V1.7 后端硬校验（confirmed==true + 存储中 req_id 的 CONFIRMED 标记，无标记返回 NOT_CONFIRMED）**已删除**——联调发现 LLM 跳步/漏写标记导致合法调用被误拒，确认与否改由外层智能体 LLM 语义识别保证；后端保留幂等（同 plan_json 重放返回原结果）与 plan_json 合法性校验；插件入参 confirmed 保留为兼容字段（后端仅记录不校验） |
| 错误处理 | status=PARTIAL 时返回失败分类明细供用户修正；status=FAIL 终止 wf_sub_02 |

#### 工具8：计费规则校验 `check_billing_rule`
| 项 | 配置 |
| --- | --- |
| 接口 | POST `https://{billing-check}/api/v1/appstore/rules/verify` |
| 实现方式 | **自研模拟实现（V1.6）**：按《产品信息.txt》该销售品的资费/叠加/互斥规则校验配置 JSON，输出模拟风险清单（默认通过，可构造冲突用例）；契约保持不变 |
| 工具描述 | 校验套餐计费逻辑、优惠叠加规则，输出资费风险清单 |
| 调用方 | `wf_sub_05` 资费校准子工作流校验节点（自查 config 取 config_json 后调用；V1.7 由 LLM 串行直调子流触发） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `config_json` | string | 是 | 否（工作流变量引用） | 缺少落地配置JSON，请先完成配置落地 | 落地配置 JSON |
| `check_scene` | string | 否 | 是 | 默认 all | 枚举：fee/overlay/superposition/all（计费/叠加/互斥叠加/全量） |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `pass` | int | 1 通过 / 0 不通过 |
| `risk_list` | array | risk_type 风险类型 / risk_desc 风险描述 / suggest 建议 |

| 归纳 | 是 |
| --- | --- |
| 超时/重试 | 60s / 重试 1 次 |
| 错误处理 | 不通过走资费驳回分支（终止 + 返回风险清单）；接口异常提示重试或人工校准 |

#### 工具9：上线审批推送 `submit_release_approval`
| 项 | 配置 |
| --- | --- |
| 接口 | POST `https://{oa-gateway}/api/v1/appstore/approval/submit` |
| 实现方式 | **自研模拟实现（V1.6）**：生成模拟审批单号 approval_id 并写入模拟审批状态库（供工具13 查询），幂等规则保持不变；契约保持不变 |
| 工具描述 | 汇总测试与稽核报告，推送上线审批流 |
| 调用方 | `wf_sub_06` 上线审批子工作流 |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `req_id` | string | 是 | 是 | 缺少执行方案key，请先完成执行主干 | 执行方案存储 key（V1.7 统一键，后端四环节硬校验依据） |
| `product_id` | string | 是 | 是 | 缺少产品ID，请先完成配置落地 | CRM 产品 ID |
| `report_url` | string | 是 | 否（工作流变量引用报告全文） | 缺少上线报告，请先完成测试报告生成 | 报告内容或链接（大模型节点 report 输出） |
| `approval_flow` | string | 否 | 是 | 默认 standard | 枚举：standard/urgent |

| 出参 | 类型 | 说明 |
| --- | --- | --- |
| `approval_id` | string | 审批单号 |
| `status` | string | 提交状态 |

| 归纳 | 否 |
| --- | --- |
| 超时/重试 | 30s / 重试 1 次（幂等：同 product_id 重复提交返回原 approval_id） |
| **后端硬校验（V1.7）** | 后端校验 req_id 入参存在 + 逐一查询 `NodeResultService.latestRecord(req_id, "config"/"spec"/"fee"/"test")` 四条记录全部非空（V1.7 统一键，原 execution_id 参数合并为 req_id）；缺失任一环节拒绝推送，防止 LLM 跳步/绕过执行主干发起审批 |

#### 工具10：监控查询 `query_product_monitor`
| 项 | 配置 |
| --- | --- |
| 接口 | GET `https://{monitor}/api/v1/appstore/product/monitor` |
| 实现方式 | **自研模拟实现（V1.6）**：按销售品返回模拟运行指标（订单量/异常量/计费差错率/告警列表），可构造 error_count>0 演示告警分支；契约保持不变 |
| 工具描述 | 查询上线后销售品运行指标（订单量、异常量、计费差错） |
| 调用方 | `wf_sub_07` 监控运维子工作流（每日定时或对话触发） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `product_id` | string | 是 | 是 | 缺少产品ID，请提供要查询的销售品 | — |
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
| 接口 | POST `https://{monitor}/api/v1/appstore/alert/send` |
| 实现方式 | **自研模拟实现（V1.6）**：生成模拟告警单号 alert_id 并返回推送成功状态，模拟结果输出；契约保持不变 |
| 工具描述 | 推送异常告警到运维群/工单 |
| 调用方 | `wf_sub_07`（异常分支）、主工作流稽核驳回分支 |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `product_id` | string | 是 | 是 | 缺少产品ID，请提供告警关联销售品 | — |
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
| 接口 | GET `https://{oa-gateway}/api/v1/appstore/approval/status` |
| 实现方式 | **自研模拟实现（V1.6）**：从模拟审批状态库（工具9 写入）查询并返回审批状态/当前环节/意见；契约保持不变 |
| 工具描述 | 按 approval_id 或 product_id 查询上线审批单当前状态，支撑用户发送消息查询审批进度 |
| 调用方 | `wf_sub_08` 审批进度查询子工作流（或智能体提示词【技能5】直接调用） |

| 入参 | 类型 | 必填 | 是否提参 | 为空提示 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `approval_id` | string | 条件必填（与 product_id 至少一个） | 是 | 请提供审批单号或销售品ID，以便查询审批进度 | 审批单号（submit_release_approval 出参），优先使用 |
| `product_id` | string | 条件必填（与 approval_id 至少一个） | 是 | 请提供审批单号或销售品ID，以便查询审批进度 | 产品 ID，缺失 approval_id 时按其查最新审批单 |

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

### 2.4 插件级自测要点（对应主方案 8.1 分层测试第 1 层）
| # | 自测项 | 通过标准 |
| --- | --- | --- |
| 1 | 工具1 businessDesc 5000 字符上限 | 5000 字符正常返回；5001 字符被工作流摘要节点截断后调用 |
| 2 | 工具2 同步返回 | 调用后 60s 内返回 pass/error_list；不产生任何文件/异步任务 |
| 3 | 工具3 globalId 格式 | `50` 开头 + 19 位（50 + yyyyMMddHHmmss + 10位随机数） |
| 4 | 工具5 轮询字段 | done/failed/failIndex 与场景状态映射（0 成功/1 失败/2 已中止/NULL 进行中）一致 |
| 5 | 工具6 受理凭证 | orderId/offerInstId 非空（测试环境有效销售品） |
| 6 | 工具7 门禁（V2.2 修订） | 确认门禁已移除——confirmed 任意值均可落地（入参仅记录）；同 plan_json 幂等不产生重复写入 |
| 7 | 工具7 幂等 | 同 plan_json 重复提交不产生重复销售品（或返回已存在 offer_id） |
| 8 | tcpCont 拼装 | 抓包验证 transactionId/reqTime/globalId/version/sign/svcCode/appKey/dstSysId 符合 2.0.2 |
| 9 | 节点结果存储读写一致 | 保存→按 req_id+node_name 查询 result_json 逐字节一致；覆盖写后查询为新值；服务重启后可查询（H2/MySQL 持久化） |
| 10 | 每个必填入参"为空提示" | 在预览与调试中置空必填参数，提示文案正确弹出 |
| 11 | 模拟数据 18 套餐覆盖（V1.6） | 依次以 18 个销售品 ID 作为输入调用工具1/2/3/6/8/10 | 每个销售品均返回结构化模拟结果，资费规则值与《产品信息.txt》该销售品记录一致；无"写死单一套餐"回退 |
| 12 | 测试预期值口径（V1.6） | 抽取 900102308、900117022 两类套餐对比工具6 presetValue | presetValue 与《产品信息.txt》对应销售品规则值逐项一致 |

### 2.5 模拟结果兼容性要求（V1.6 新增，与主方案"模拟结果兼容性要求"对齐）
全部自研接口（工具1~11、13）的模拟输出统一遵守：
1. **种子数据**：以《产品信息.txt》全部 **18 个销售品**为种子数据（5G-A 系列 10 个 + 权益随心选系列 8 个），不写死单一套餐样例；
2. **可复现性**：任一销售品作为输入时，相似度分析、稽核、资费校验、测试（场景/进度/结果/受理验证）、监控等接口均能返回与该销售品资费规则一致的结构化模拟结果，保证端到端演示对任意套餐可复现；
3. **预期值口径**：测试结果中的预期值（presetValue）取自该销售品在《产品信息.txt》中的规则值；testValue 模拟生成（默认与 presetValue 一致，支持构造不一致用例验证异常分支）；
4. **契约不变**：接口路径/入参/出参契约仍参考《产销品场景部分能力接口清单.xlsx》设计，后续替换真实实现时前端与工作流无需改动。

---

## 3. 工作流细化设计

### 3.0 工作流清单与节点图例（V1.6 实现口径：节点数与平台导出 JSON 一致；V1.7 主流程弃用）
| 编码 | 名称 | 类型 | 节点数/边数 | V1.7 状态 |
| --- | --- | --- | --- | --- |
| `wf_cpcp_main` | 产销品加载主流程（仅调度与判定，无存储节点） | 主工作流 | 25/31（平台导出 30/36 含触发分支节点） | **弃用，保留归档** |
| `wf_sub_01` | 需求分析（执行方案生成） | 子工作流 | 9/8 | 挂载，LLM 直调 |
| `wf_sub_02` | 智能配置（配置落地，req_id 单入参自查） | 子工作流 | 7/6 | 挂载，LLM 直调 |
| `wf_sub_03` | 规格稽核（实时，req_id 单入参自查） | 子工作流 | 8/7 | 挂载，LLM 直调 |
| `wf_sub_04` | 自动测试（含受理验证，req_id 单入参自查） | 子工作流 | 11/10 | 挂载，LLM 直调 |
| `wf_sub_05` | 资费校准（req_id 单入参自查） | 子工作流 | 8/7 | 挂载，LLM 直调 |
| `wf_sub_06` | 上线审批（req_id 单入参，串行自查 5 类结果→7 章节报告→报告存储→审批推送） | 子工作流 | 11/10 | 挂载，LLM 直调 |
| `wf_sub_07` | 监控运维 | 子工作流 | 5 | 挂载（LLM 直调工具10/11 兜底） |
| `wf_sub_08` | 审批进度查询（V1.1 新增） | 子工作流 | 3 | 挂载（LLM 直调工具13 兜底） |

> V1.6 统一模式（V1.8 更新）：wf_sub_02~06 开始节点统一为 **req_id 单入参**（描述"执行方案存储key（V1.8 统一键：PLAN+yyyyMMddHHmmss+3位随机数，方案批次与执行主干共用，同键覆盖）"），子流程内部以 query_node_result（GET，req_id=开始节点入参、node_name=环节名、latest_only=1、submit_way=get）自查所需上游结果；结束节点前由代码节点合成 result_json 并 save_node_result 落库（node_name 枚举：requirement/config/spec/fee/test/report + V1.7 新增 CONFIRMED）。**wf_sub_01 开始节点为 requirement_text/requirement_file 双入参**，req_id 由 **004a 拆分代码节点以系统时钟生成**（每次分析重新生成，LLM 不参与生成，详见 3.4.1 节）。主流程删除全部存储节点（含旧 0016/0016b），报告生成（LLM）也移入 wf_sub_06 内部（节点 501g）。
>
> **V1.7 变更**：主流程 `wf_cpcp_main` 固定编排弃用（因平台主流程对子工作流入参注入受限，曾致"确认执行"后重复需求分析），执行主干调度职责移交智能体 LLM（见 3.3 节时序）；3.1 节主流程逐节点配置**保留作为归档参考**，不再实施。

节点类型图例：`[开始/结束]` 流程起止　`[LLM]` 大模型节点　`[插件]` 插件工具节点　`[存储]` 节点结果存储查询插件　`[代码]` 代码节点　`[循环]` 循环节点　`[选择]` 选择器节点　`[子流]` 子工作流调用节点

> 编排说明（V1.7）：8 个子工作流由智能体 LLM 直调，无需嵌套；若平台不支持智能体直调子工作流，可回退 V1.6 主流程编排模式（`wf_cpcp_main` 归档 JSON 可复用）。

---

### 3.1 主工作流 `wf_cpcp_main` 逐节点配置（【已弃用·归档参考】，V1.1 对齐主方案 V1.5：执行主干自动化串行）

> **V1.7 起本节仅为归档参考，不再实施**：主流程固定编排由智能体 LLM 智能调度替代（见 3.3 节），工作流挂载清单中不再包含 `wf_cpcp_main`。

| # | 节点 | 类型 | 入参/引用 | 输出变量 | 配置要点 |
| --- | --- | --- | --- | --- | --- |
| 1 | 开始 | [开始] | `requirement_text`(string,必填)　`requirement_file`(string,选填)　`req_id`(string,选填,执行方案存储key)　`confirmed`(bool,选填,默认false)　`resume_action`(string,选填: retry_from_fail/revise_plan)　`fail_node`(string,选填,上次失败环节编码)　`approve_confirmed`(bool,选填,审批发起确认,默认false) | — | 各续跑/确认参数由智能体对话层按用户回复注入（V1.7 统一键：原 plan_id/execution_id 双参合并为 req_id 单参） |
| 2 | 入口判定 | [选择] | confirmed、req_id | branch | 条件①：`confirmed==true 且 req_id 非空` → 节点4；条件②：否则 → 节点3 |
| 3 | 需求分析 | [子流] | requirement_text, requirement_file | req_id, plan_md, pending_fields | 调用 wf_sub_01；结束节点A 输出执行方案表格，**主流程在此中断** |
| 3A | 结束节点A | [结束] | plan_md, req_id, pending_fields | 对话输出 | 输出模板见 3.1.1；等待用户回复"确认执行"后携带 req_id+confirmed=true 重入 |
| 4 | 续跑判定 | [选择] | resume_action, fail_node, approve_confirmed | branch | ①`approve_confirmed==true` → 节点16（审批确认后续办）；②`resume_action==revise_plan` → 节点3（修改执行方案）；③`resume_action==retry_from_fail` → 按 fail_node 跳转（映射见 3.4.3）；④无 → 节点5（首次执行） |
| 5 | 【环节1】智能配置 | [子流] | req_id | product_id, offer_id, save_result, status | 调用 wf_sub_02（req_id 单入参，内部自查执行方案 JSON 后落地）；前置由节点2 入口判定保证执行方案已保存（有待补充项的方案不产出 req_id 无法进入）；子流程内部存储 node_name=config |
| 6 | 环节1判定与打印 | [选择]+输出 | status | — | `SUCCESS/PARTIAL` → 按 3.1.2 模板打印环节1成功结果 → 节点7；`FAIL` → 节点21（异常A，fail_node=STAGE1_CONFIG） |
| 7 | 【环节2】实时规格稽核 | [子流] | req_id | pass, error_list, audit_summary | 调用 wf_sub_03（req_id 单入参，自查 config 取 offer_id/config_json；实时接口同步返回）；子流程内部存储 node_name=spec |
| 8 | 环节2判定与打印 | [选择]+输出 | pass | — | `pass==1` → 打印环节2成功结果 → 节点9；`pass==0` 或接口异常 → 节点21（fail_node=STAGE2_AUDIT） |
| 9 | 【环节3】资费校准 | [子流] | req_id | pass, risk_list, risk_summary | 调用 wf_sub_05（req_id 单入参，自查 config 取 config_json）；子流程内部存储 node_name=fee |
| 10 | 环节3判定与打印 | [选择]+输出 | pass | — | `pass==1` → 打印环节3成功结果 → 节点11；`pass==0` 或接口异常 → 节点21（fail_node=STAGE3_FEE） |
| 11 | 【环节4】自动测试 | [子流] | req_id | test_report, test_passed, globalId | 调用 wf_sub_04（req_id 单入参，自查 config 取 offer_id；发起→代码节点0304轮询→结果→报告，报告含受理验证结论）；子流程内部存储 node_name=test |
| 12 | 环节4判定与打印 | [选择]+输出 | test_passed | — | 通过 → 打印环节4成功结果（含受理验证结论）→ 节点13；失败/超时 → 节点21（fail_node=STAGE4_TEST） |
| 13 | 主干完成判定 | [选择] | 各环节结果 | branch | 四环节全部成功（all_passed）→ 节点14；否则 → 节点21 |
| 14 | 成功结果详情汇总 | [LLM] | 四环节存储结果（按 req_id=入参统一键自查 config/spec/fee/test） | stage_summary | 温度 0.2；按 3.1.3 模板打印各环节成功结果详情，并提示"是否发起上线审批"；**主流程第二次中断**，等待用户回复"发起审批"（approve_confirmed=true）或"暂不" |
| 15 | 审批发起判定 | [选择] | approve_confirmed | branch | `true` → 节点17；`false` → 结束（提示"已为您保留执行结果，回复【发起审批】可随时继续"） |
| 17 | 上线审批 | [子流] | req_id | approval_id, status, report | 调用 wf_sub_06（req_id 单入参：内部串行自查 5 类环节结果 → LLM 节点 501g 生成 7 章节报告 → 报告存储 node_name=report → 审批推送，工具层校验 approve_confirmed）；**主流程无报告汇总节点（旧 0016/0016b 已删除）** |
| 18 | 结束节点B | [结束] | — | — | 输出：product_id / req_id / approval_id / 各环节结果摘要 / 下一步指引（"可发送消息查询审批进度或监控运维结果"） |
| 21 | 异常处置（异常A） | [LLM]（+可选[插件] send_alert） | fail_node, 失败环节出参 | exception_summary | 统一异常出口；提示词见 3.1.5；输出异常节点+原因+明细+建议，并引导用户：①【重新执行】→ 注入 resume_action=retry_from_fail+fail_node 重入；②【修改执行方案】→ 注入 resume_action=revise_plan 重入；严重异常（稽核驳回等）可联动 send_alert(high) |

> 主流程"两次中断"说明：① 结束节点A（执行方案确认）；② 节点14（审批发起确认）。执行主干（节点5~12）内部**无任何人工等待点**。续跑时节点4 按 resume_action/fail_node 跳转；已成功环节结果由各子工作流按 req_id=入参统一键、node_name=config/spec/fee/test 自查回放，**写接口不重复调用**。主流程**无存储节点**（25 节点/31 边）。

#### 3.1.1 结束节点A 输出模板
```
{{plan_md（Markdown 表格，固定4列：字段分类/字段名称/字段值/来源）}}

【待补充字段】{{pending_fields | 为空时显示"无，所有字段均已明确"}}

【若存在待补充字段】执行方案暂未保存、暂不能执行（回复【确认执行】无效）：
- 请直接补充价格/资源类字段值，将更新执行方案并再次确认；
【若待补充字段为空（req_id 已生成）】《产销品加载执行方案》已生成并保存（req_id：{{req_id}}），请核对：
- 回复【确认执行】：将自动串行执行 智能配置→稽核→资费校准→自动测试 四个环节
  （每环节执行后向您打印结果，仅异常时中断）；
- 如需调整：请直接说明修改意见（仅价格、资源类字段须由您补充，其余字段已按相似产品补全）。
```

#### 3.1.2 每环节结果打印模板（节点6/8/10/12 统一）
```
【环节N/{环节名称}】✅ 执行成功
- 关键数据：{该环节关键输出}
  环节1：product_id / offer_id / 四类字段写入结果（save_result）
  环节2：稽核通过 + audit_summary
  环节3：资费校准通过 + risk_list 为空说明
  环节4：场景数/测点数统计 + 受理验证结论（orderId/offerInstId + 逐受理场景结论）
- 已自动进入下一环节……（环节4 时改为"- 执行主干全部完成"）
```
> 打印节点实现：执行类环节结果直接引用插件出参拼装（不经过大模型加工）；如平台输出节点不支持纯文本拼装，可用温度 0.2 的小 LLM 节点仅做格式化（提示词注明"逐字引用输入数据，不新增内容"）。

#### 3.1.3 节点14 成功结果详情汇总（含审批引导）
| 项 | 配置 |
| --- | --- |
| 模型参数 | 温度 0.2 |
| 输入引用 | 各子工作流存储结果（req_id=入参统一键、node_name=config/spec/fee/test，由 query_node_result 自查取回）或直接引用节点5/7/9/11 输出 |
| 提示词 | 见下 |
| 输出 | `stage_summary`(string)；主流程中断等待审批确认 |

提示词：
```
执行主干四个环节全部成功，请按以下模板输出（逐字引用输入数据，不新增结论）：

【执行主干全部完成】✅ 共4个环节执行成功：
1. 智能配置：product_id={...}，offer_id={...}，四类字段全部写入成功；
2. 配置规格稽核：通过，{audit_summary}；
3. 资费校准：通过，未发现叠加/互斥冲突；
4. 自动测试（含受理验证）：场景 N 个、测点 M 个全部一致；
   受理验证：orderId={...}，offerInstId={...}，各受理场景均通过。

是否发起上线审批？回复【发起审批】将汇总以上结果提交审批流；回复【暂不】可稍后发送"发起审批"继续。
```

#### 3.1.4 报告生成大模型节点（V1.6 调整：位于 wf_sub_06 内部，节点 501g；主流程原节点16 已删除）
| 项 | 配置 |
| --- | --- |
| 模型参数 | 温度 0.2 |
| 输入引用 | wf_sub_06 内部串行自查合成的结构化汇总（代码节点 501s：config/spec/fee/test 四类环节结果 + 需求摘要） |
| 提示词 | 见下 |
| 输出 | `report`(string)；先 save_node_result 落库（node_name=report），再传给 submit_release_approval |

提示词（强制 7 章节）：
```
请按标准模板汇总生成《销售品上线测试与稽核报告》，强制包含 7 章节：
1. 需求摘要（引用 requirement 存储结果，仅列关键字段）；
2. 配置落地结果（config：save_result 各分类写入情况、product_id/offer_id）；
3. 稽核结论（spec：audit_summary，通过/驳回）；
4. 资费结论（fee：risk_list 是否为空）；
5. 测试统计（test：场景数/测点数/成功/失败统计，失败测点逐条列出）；
6. 受理验证结论（强制章节，不得省略）：引用测试结果中的 orderId、offerInstId，
   并逐受理场景（套餐新装/副卡加装/套餐退订）给出通过/失败结论；
7. 上线建议：全部通过 → "建议上线"；任一环节未通过 → "暂缓上线"。
只基于输入数据生成，不得新增结论。
```

#### 3.1.5 节点21 异常处置节点（异常A；主流程当前为 25 节点/31 边，异常处置为统一出口）
| 项 | 配置 |
| --- | --- |
| 模型参数 | 温度 0.2 |
| 输入引用 | fail_node + 失败环节出参（status/resultCode/resultMsg 或 error_list/risk_list/失败测点/超时信息） |
| 提示词 | 见下 |
| 输出 | `exception_summary`(string)；随后主流程结束，等待用户注入 resume_action 重入 |
| 可选联动 | 稽核驳回（STAGE2_AUDIT）等严重异常 → send_alert(alarm_level=high) |

提示词：
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

---

### 3.2 子工作流逐节点配置

#### 3.2.1 `wf_sub_01` 需求分析（执行方案生成，V1.6：9 节点/8 边）
| # | 节点 | 类型 | 入参/引用 | 输出变量 | 配置要点 |
| --- | --- | --- | --- | --- | --- |
| 1 | 开始 | [开始] | requirement_text(string,必填)　requirement_file(string,选填) | — | 文件地址由平台文件上传组件生成 |
| 2 | 需求理解与要素拆解 | [LLM] | requirement_text（+文件解析文本） | 要素拆解中间结果 | 温度 0.2；提示词=主方案 6.4 第 1~3 步（理解需求→提取拆解业务要素→识别完整性）；输出结构化要素 JSON（临时变量，不落存储） |
| 3 | 相似产品查询 | [插件] | businessDesc=requirement_text（>5000字符时引用节点2输出的需求摘要） | similarOfferList | 工具1 `query_similar_offer` |
| 4 | 字段映射与补全 | [LLM] | 要素拆解结果 + similarOfferList + 知识库检索（存量销售品资料库） | **plan_output（单出参）** | 温度 0.2；提示词=主方案 6.4 全文（含 V1.4 补全规则）；**LLM 节点仅输出 plan_output 一个出参**（内含完整 JSON 文本） |
| 004a | 拆分方案字段 | [代码] | plan_output + 节点31推理后 fields_json | plan_json, plan_md, pending_fields, req_id | 代码节点拆分 4 个出参：**fields 与 pending_fields 均以节点31推理后结果为准**（fields 覆盖 plan_json；pending_fields 从推理后字段数组反查 value=待补充 生成，不采信 LLM 自判，V2.2 单一事实源）；req_id 系统生成（PLAN+时间戳+随机数） |
| 5 | 待补充项判断 | [选择] | pending_fields | branch | `pending_fields` 为空（长度=0）→ 节点6 保存后进入确认；**非空 → 直接进入节点7（有待补充项结束），不保存执行方案、不产出 req_id**；判定数据源已收归引擎（V2.2），LLM 双重判定歧义已消除 |
| 6 | 保存执行方案 | [存储] | req_id=节点004a出参 req_id　node_name=requirement　result_json=plan_json | req_id | 调用节点结果存储查询插件·结果存储；**仅无待补充项时执行**；修改场景覆盖写同 key |
| 7 | 结束(有待补充项) | [结束] | plan_md, pending_fields | — | 输出待补充提示，**不保存执行方案、不产出 req_id**，**从源头禁止进入智能配置**（无执行方案记录可落地）；用户补充后重新走需求分析 |
| 8 | 结束(无待补充项) | [结束] | req_id, plan_md | — | 返回智能体对话层（执行方案确认中断点，LLM 等待用户"确认执行"） |

> 提示词全文以主方案 6.4 节为准（源自《需求分析工作流可参考提示词.txt》），此处不重复。节点 4 的知识库检索参数见 4.3 节（K4 检索 top_k=3、score 阈值 0.75）。

#### 3.2.2 `wf_sub_02` 智能配置（配置落地，V1.6：req_id 单入参自查；V2.2：8 节点/5 边，含提取原文代码节点）
| # | 节点 | 类型 | 入参/引用 | 输出变量 | 配置要点 |
| --- | --- | --- | --- | --- | --- |
| 1 | 开始 | [开始] | req_id(string,必填，执行方案存储key，V1.7 统一键) | — | 由智能体确认语义识别保证已确认（V2.2：save_product_config 确认门禁已移除，不再校验 CONFIRMED 标记） |
| 2 | 自查执行方案 | [存储] | req_id=req_id　node_name=requirement　latest_only=1　submit_way=get | 查询结果 | query_node_result（**GET**）；出参 list 为记录数组 |
| 3 | 提取执行方案原文 | [代码] | query_list=节点2出参 list | record_json | **V2.2 新增 CODE_EXTRACT_RECORD**：提取 list[0].result_json（执行方案对象原文）；查无时兜底透传原始入参 |
| 4 | 配置落地 | [插件] | req_id=开始节点入参、plan_json=节点3输出（原样透传）、confirmed=true（仅记录） | product_id, offer_id, save_result, status | 工具7 `save_product_config`；**入参 req_id/plan_json（V1.7 统一键，方案key由后端从 plan_json 的 req_id 键提取）；节点3→节点4 之间禁止插入任何大模型节点/改写节点；V2.2 起后端不校验 confirmed** |
| 5 | 存储环节结果 | [存储] | req_id=req_id　node_name=config　result_json=节点4出参 save_result | — | save_node_result；主流程续跑回放依据 |
| 6 | 结束 | [结束] | product_id, offer_id, save_result, status | — | 返回智能体对话层 |

#### 3.2.3 `wf_sub_03` 规格稽核（实时，V1.6：req_id 单入参自查，8 节点/7 边）
| # | 节点 | 类型 | 入参/引用 | 输出变量 | 配置要点 |
| --- | --- | --- | --- | --- | --- |
| 1 | 开始 | [开始] | req_id(string,必填) | — | — |
| 2 | 自查配置结果 | [存储] | req_id=req_id　node_name=config　latest_only=1 | 查询结果 | query_node_result（GET）；**V2.2 新增自查节点（原实现 offer_id/config_json 引用开始节点不存在的出参，断链已修复）** |
| 3 | 提取配置结果原文 | [代码] | query_list=节点2出参 list | record_json | **V2.2 新增 CODE_EXTRACT_RECORD**：提取 list[0].result_json（落地结果原文，内含 offer_id） |
| 4 | 实时稽核 | [插件] | offer_id（智能体调度透传）, config_json=节点3输出, audit_scene=all | pass, error_list, audit_summary | 工具2 `realtime_spec_audit`；同步返回，无文件上传/无轮询 |
| 5 | 整改建议生成 | [LLM] | error_list, audit_summary | audit_suggest | 温度 0.2；提示词："将稽核问题明细整理为可执行的整改建议清单，按严重级别排序；pass=1 时输出'稽核通过'。不新增稽核结论。" |
| 6 | 存储环节结果 | [存储] | req_id=req_id　node_name=spec　result_json=节点4出参 audit_summary | — | save_node_result |
| 7 | 结束 | [结束] | pass, error_list, audit_suggest | — | 返回智能体对话层 |

#### 3.2.4 `wf_sub_04` 自动测试（含受理验证，V1.6：req_id 单入参自查；V2.2：13 节点/12 边，含自查+提取节点）
| # | 节点 | 类型 | 入参/引用 | 输出变量 | 配置要点 |
| --- | --- | --- | --- | --- | --- |
| 1 | 开始 | [开始] | req_id(string,必填) | — | — |
| 2 | 自查配置结果 | [存储] | req_id=req_id　node_name=config | 查询结果 | query_node_result（GET）；**V2.2 新增自查节点（修复 offer_id 断链）** |
| 3 | 提取配置结果原文 | [代码] | query_list=节点2出参 list | record_json | **V2.2 新增 CODE_EXTRACT_RECORD** |
| 4 | 发起测试 | [插件] | offerId=开始节点 offer_id（智能体调度透传，**camelCase 特例保持**，与后端工具3 参数名一致） | globalId | 工具3 `offer_test`；resultCode=1 时直接终止 |
| 5 | 查询测试场景 | [插件] | globalId | testScenes | 工具4 `get_test_scenes`；记录受理验证覆盖范围 |
| 0304 | 轮询测试进度 | [代码]（type=6） | globalId（**inputs 必须为平铺 list，非 {loopParam, inputParameters} 嵌套**） | done, failed, failIndex, fail_reason | 节点内代码：asyncio.sleep(5) 间隔轮询工具5 `get_test_progress`，最多 360 次（30 分钟），连续 5 次失败终止转人工；伪代码见 3.4.4 |
| 6 | 查询测试结果 | [插件] | globalId | 测试结果（含 orderId, offerInstId, offerName） | 工具6 `get_test_result`；仅 done=true 后调用 |
| 7 | 测试报告生成 | [LLM] | 测试结果 + testScenes（节点5 场景清单） | test_report, test_passed | 温度 0.2；提示词见 3.4.5；**报告必须含受理验证结论** |
| 8 | 合成结果 | [代码] | 节点7出参 | result_json | 代码节点合成环节结果 JSON（含 test_passed/fail_reason） |
| 9 | 存储环节结果 | [存储] | req_id=req_id　node_name=test　result_json | — | save_node_result |
| 10 | 结束 | [结束] | test_report, test_passed, globalId | — | 返回主工作流节点11 |

#### 3.2.5 `wf_sub_05` 资费校准（V1.6：req_id 单入参自查；V2.2：10 节点/9 边，含自查+提取节点）
| # | 节点 | 类型 | 入参/引用 | 输出变量 | 配置要点 |
| --- | --- | --- | --- | --- | --- |
| 1 | 开始 | [开始] | req_id(string,必填) | — | — |
| 2 | 自查配置结果 | [存储] | req_id=req_id　node_name=config | 查询结果 | query_node_result（GET）；**V2.2 新增自查节点（修复 config_json 断链）** |
| 3 | 提取配置结果原文 | [代码] | query_list=节点2出参 list | record_json | **V2.2 新增 CODE_EXTRACT_RECORD** |
| 4 | 计费校验 | [插件] | config_json=节点3输出, check_scene=all | pass, risk_list | 工具8 `check_billing_rule`；all=全量校验（计费/叠加/互斥） |
| 5 | 风险解读 | [LLM] | risk_list + 知识库检索（资费规则库） | risk_summary | 温度 0.2；提示词："将资费风险清单翻译为业务语言，说明每条风险的影响与建议；risk_list 为空时输出'资费校验通过，未发现叠加/互斥冲突'。可引用资费规则库知识作为解释依据，但不得新增风险结论。" |
| 6 | 合成结果 | [代码] | 节点4/5出参 | result_json | 代码节点合成环节结果 JSON |
| 7 | 存储环节结果 | [存储] | req_id=req_id　node_name=fee　result_json | — | save_node_result |
| 8 | 结束 | [结束] | pass, risk_list, risk_summary | — | 返回主工作流节点9 |

#### 3.2.6 `wf_sub_06` 上线审批（V1.6 重设计：req_id 单入参串行自查，11 节点/10 边）
| # | 节点 | 类型 | 入参/引用 | 输出变量 | 配置要点 |
| --- | --- | --- | --- | --- | --- |
| 1 | 开始 | [开始] | req_id(string,必填，执行方案存储key，V1.7 统一键) | — | 进入本子工作流前智能体已保证 approve_confirmed=true |
| 2 | 自查配置结果 501q1 | [存储] | req_id=req_id　node_name=config | config 结果 | query_node_result（GET） |
| 3 | 自查稽核结果 501q2 | [存储] | req_id=req_id　node_name=spec | spec 结果 | 与 q1 **串行**（q1→q2→q3→q4→q5 链式连接，非星型并行） |
| 4 | 自查资费结果 501q3 | [存储] | req_id=req_id　node_name=fee | fee 结果 | 串行 |
| 5 | 自查测试结果 501q4 | [存储] | req_id=req_id　node_name=test | test 结果 | 串行 |
| 6 | 自查执行方案 501q5 | [存储] | req_id=req_id　node_name=requirement | 需求摘要 | 串行；用于报告"需求摘要"章节（V1.7 统一键，与环节结果同键） |
| 7 | 合成汇总 501s | [代码] | 5 类自查结果 | 结构化汇总（含 product_id/orderId/offerInstId 等） | 提取各环节关键字段合成 LLM 输入 |
| 8 | 报告生成 501g | [LLM] | 结构化汇总 | report | 温度 0.2；提示词见 3.1.4；**强制 7 章节：需求摘要/配置落地/稽核结论/资费结论/测试统计/受理验证（orderId/offerInstId）/上线建议；全通过→"建议上线"，任一未通过→"暂缓上线"** |
| 9 | 报告存储 501r | [存储] | req_id=req_id　node_name=report　result_json=report | — | save_node_result |
| 10 | 审批推送 0502 | [插件] | req_id=开始节点入参, product_id, report_url=report, approval_flow=standard | approval_id, status | 工具9 `submit_release_approval`；**V1.7 后端硬校验 req_id 四环节（config/spec/fee/test）结果齐全**（`NodeResultService.latestRecord` 逐环节查询，缺失即拒绝），LLM 跳步执行主干也无法推送审批 |
| 11 | 结束 | [结束] | approval_id, status, report | — | 返回智能体对话层（输出审批单号） |

> V1.6 重设计说明：旧版 wf_sub_06 仅 3 节点（入参 product_id+report，报告由主流程 016 LLM 生成后传入）。新版改为**子工作流自治**——req_id 单入参进入，串行自查 5 类环节结果，内部 LLM 生成报告并落库（node_name=report），再推送审批；主流程相应删除节点16（报告汇总 LLM）与 0016b（报告存储）。

#### 3.2.7 `wf_sub_07` 监控运维
| # | 节点 | 类型 | 入参/引用 | 输出变量 | 配置要点 |
| --- | --- | --- | --- | --- | --- |
| 1 | 开始 | [开始] | product_id(string,必填)　date_range(string,选填) | — | 支持每日定时触发（平台定时任务）与对话触发两种方式 |
| 2 | 监控查询 | [插件] | product_id, date_range, metric=all | order_count, error_count, fee_error_rate, alarm_list | 工具10 `query_product_monitor` |
| 3 | 异常判定 | [选择] | error_count, fee_error_rate | branch | `error_count>0 或 fee_error_rate>0.1` → 节点4；否则 → 结束（输出正常摘要） |
| 4 | 异常告警 | [插件] | product_id, alarm_level, content | alert_id | 工具11 `send_alert`；content 由前置小节点（[LLM]，温度 0.2）生成告警文案 |
| 5 | 结束 | [结束] | 指标摘要, alert_id | — | — |

#### 3.2.8 `wf_sub_08` 审批进度查询（V1.1 新增）
| # | 节点 | 类型 | 入参/引用 | 输出变量 | 配置要点 |
| --- | --- | --- | --- | --- | --- |
| 1 | 开始 | [开始] | approval_id(string,选填)　product_id(string,选填) | — | 二者至少一个非空（由智能体提示词保证，为空时提示用户补齐） |
| 2 | 审批状态查询 | [插件] | approval_id, product_id | approval_id, status, current_node, approver, opinion, update_time | 工具13 `query_approval_status` |
| 3 | 状态摘要归纳 | [LLM] | 节点2出参 | approval_summary | 温度 0.2；提示词："按'审批单号 {approval_id}｜状态：{status}｜当前环节：{current_node}（审批人 {approver}）｜最近意见：{opinion}｜更新时间：{update_time}'格式输出；status=驳回 时附驳回原因并提示可修改执行方案后重新发起。" |
| 4 | 结束 | [结束] | approval_summary | — | — |

> `wf_sub_08` 为轻量查询子工作流：若平台智能体支持直接调用插件工具，可省略，由智能体提示词【技能5】直接调用工具13（监控查询同理可直接调用工具10）。

---

### 3.3 LLM 智能调度时序说明（V1.7 重写：确认门禁下沉工具层，智能体直调子工作流）

```
第一轮：
用户输入需求 → 智能体按意图映射表命中【首次提报需求】
→ LLM 直调 wf_sub_01 需求分析（requirement_text=用户需求）
→ 子流内部：LLM 节点4 单出参 plan_output → 字段本体推理（工具14 reason）→ 代码节点 004a 拆分
  （fields/pending_fields 以推理后结果为准，V2.2 单一事实源）→ 存储(key=req_id 统一键, node_name=requirement)
→ LLM 向用户输出执行方案表格 + 确认提示 → 【中断：等待执行方案确认】

用户侧：核对表格 → 回复"确认执行"（智能体提示词【技能2】识别确认意图）

第二轮：
LLM 执行确认流程（严格串行，不等用户再发消息）：
  ① 取上下文中执行方案存储键 req_id（沿用原值，不新生成）
  ② 串行直调子工作流（每环节返回后打印结果，环节结果由子流内部存储节点落库）：
    环节1 直调 wf_sub_02 智能配置（req_id 入参，子流自查 requirement→提取执行方案原文
         →落地+存储 config；V2.2 起后端不校验 CONFIRMED 标记，确认语义由 LLM 识别保证）
    → 环节2 直调 wf_sub_03 规格稽核（V2.2：自查 config+提取原文取 offer_id/config_json，存储 spec）
    → 环节3 直调 wf_sub_05 资费校准（V2.2：自查 config+提取原文取 config_json，存储 fee）
    → 环节4 直调 wf_sub_04 自动测试（V2.2：自查 config+提取原文取 offer_id，代码节点轮询，存储 test）
→ 全部成功 → LLM 打印各环节成功结果详情 + 提示"是否发起上线审批" → 【中断：等待审批发起确认】
（任一环节异常 → LLM 立即中断调度：打印异常环节+原因+建议，引导【重新执行】/【修改执行方案】）

第三轮（用户回复"发起审批"）：
LLM 按意图映射表命中【发起审批】→ 直调 wf_sub_06 上线审批（req_id 入参）
→ 子流内部串行自查 5 类环节结果 → LLM 生成 7 章节报告并存储 node_name=report
→ submit_release_approval 推送（后端硬校验 req_id 四环节结果齐全）
→ 输出审批单号（提示可消息查询审批进度）

异常处置后的续跑（可选）：
用户回复【重新执行】→ LLM 按 fail_node 映射从失败环节续调
  （STAGE1_CONFIG→wf_sub_02、STAGE2_AUDIT→wf_sub_03、STAGE3_FEE→wf_sub_05、STAGE4_TEST→wf_sub_04）
  （各子工作流按 req_id 自查回放已成功环节，不重复调用写接口）
用户回复【修改执行方案】+ 修改意见 → LLM 直调 wf_sub_01（requirement_text=<原需求+修改意见>，覆盖保存）
```

- 两次中断（执行方案确认/审批发起确认）由智能体对话层承担：确认意图识别由提示词【技能2】控制，审批引导回复由【技能4】控制。
- **串行纪律（提示词【限制】强制）**：严禁并行调用子工作流、严禁跳过环节、严禁凭语义推断环节成败（仅依据出参字段 status/pass/test_passed 判定）、严禁不带参数重复调用需求分析冒充执行。

【意图→子工作流智能调度映射表（与主方案 3.2 提示词保持一致，V1.7 替代旧"主工作流入参注入映射表"）】
| 用户意图 | 调度动作（串行） | 入参来源 |
| --- | --- | --- |
| 首次提报需求/重新分析 | 直调 wf_sub_01 | requirement_text=用户需求 |
| 确认执行 | 串行直调 wf_sub_02→wf_sub_03→wf_sub_05→wf_sub_04（V2.2：无需写 CONFIRMED 确认标记，确认语义由 LLM 识别） | req_id=<上一次执行方案存储键>（沿用原值，不新生成）；后续环节入参取上一环节出参 |
| 修改执行方案 | 直调 wf_sub_01（覆盖保存同 req_id） | requirement_text=<原需求+修改意见> |
| 重新执行失败环节 | 按 fail_node 续调对应子流（STAGE1_CONFIG→wf_sub_02、STAGE2_AUDIT→wf_sub_03、STAGE3_FEE→wf_sub_05、STAGE4_TEST→wf_sub_04）；已成功环节不重复调用 | req_id=<原值>；入参按 req_id 自查存储回放 |
| 发起审批 | 直调 wf_sub_06（子流自查四环节结果生成报告并推送） | req_id=<原值> |
| 查询审批进度/监控结果 | 不走子工作流，LLM 直调工具13/工具10 | approval_id 或 product_id |

- req_id 以多轮对话内最近一次值为准（上下文记忆）；若用户确认时 req_id 未知（如新会话），LLM 先调 query_node_result 检索最近执行方案，取回 req_id 后再执行确认流程。
- **确认门禁（V2.2 修订）**：`save_product_config` 确认门禁已移除（原 CONFIRMED 标记校验删除，不再返回 NOT_CONFIRMED）——"未确认不配置"由智能体提示词【技能2】确认语义识别保证；`submit_release_approval` 后端仍硬校验 req_id 入参 + 四环节（config/spec/fee/test）结果齐全（LLM 跳步执行主干也无法推送审批）。

### 3.4 关键算法与提示词细化

#### 3.4.1 req_id 生成规则（V1.7 统一键，代码节点系统生成）
```
req_id 由 wf_sub_01 拆分代码节点（004a）以系统时钟生成，LLM 不参与生成：
req_id = "PLAN" + datetime.now().strftime("%Y%m%d%H%M%S") + 3位随机数(%03d)
示例：PLAN20260913143025087（系统时钟保证取真实当前时刻，每次分析重新生成、必然不同）
plan_json 内的 req_id 键亦由代码节点注入（LLM 输出空字符串，禁止自行生成）
```
后端唯一性硬校验（NodeResultService.save，双保险）：
```
① 格式校验：req_id 须匹配 PLAN\d{17}（PLAN+14位时间戳+3位随机数），非法返回 5002；
② 唯一性冲突拦截：requirement 环节同 req_id 重写且 result_json 内容不同（LLM 照抄历史
   req_id 写入新方案）→ 拒绝并返回 5006，防止旧方案被静默覆盖；
   修改方案场景为同内容覆盖，不受影响。
```

#### 3.4.2 待补充字段判定逻辑与组合补全策略（wf_sub_01 节点4 提示词内含，此处为程序化校验口径，V2.0 更新）
```
【取值链（V2.1 本体推理引擎，字段形态与默认值由后端 FieldOntologyService 统一保证）】
逐级降级取值：原始需求 → 相似产品（最高相似度） → 本体默认值（推理引擎补全） → 待补充（禁止跳级虚构）
字段形态校验与默认值补全（wf_sub_01「字段本体推理」节点，工具14 action=reason 一体推理，串行闭环：
校验→非法值修正回写→缺失与待补充字段默认值推理补全，方案以推理后结果为准）：
  产品属性 ∈ {基础, 可选, 增值}（套餐类默认基础、权益包默认增值）
  收费方式 ∈ {按月, 按量, 一次性}（月付/包月归一为按月）
  渠道类型 ⊆ {实体渠道, 电子渠道, 直销渠道}（无参照默认三者全选；营业厅/门店→实体渠道、
    APP/网厅/线上→电子渠道、直销/客户经理/政企→直销渠道同义词映射归一）
  适用地区 ∈ {全国（不含港澳台）, 指定省份}（默认全国）
  计费周期 ∈ {自然月}（默认）
  销售品状态 ∈ {在售, 待上线}（新需求一律填"待上线"，禁止取相似产品的"在售"）
  数值类字段必须带单位（GB/分钟/条/元），缺单位自动补全（60G→60GB）；优惠条件/优惠期无优惠填"无"；
  副卡规则无参照填"不允许办理副卡"；退订规则默认"允许退订，次月生效，当月费用不退还"

【待补充判定（V2.2 口径：唯一不可推理项=套餐固定费）】
套餐固定费未提取到
    → 值="待补充"（价格禁止推理、禁止从相似产品照搬），引擎维持"待补充"加入 pending_fields
其他分类字段缺失或值="待补充"
    → 引擎一律按本体默认值推理补全（三类资源→"无"、优惠条件/优惠期→"无"、
      适用地区→全国、计费周期→自然月、退订规则→默认口径等），来源="AI补全"
产品编码：恒填"由智能配置生成"（不计入 pending_fields）
来源枚举校验：LLM 节点4 标注仅允许 {原始需求, AI补全}；"本体推理"由引擎自动标注
（引擎补全/修正后 source 改标"本体推理"），出现其他值 → 校验失败重新生成
枚举/格式值校验：字段值不符合本体推理引擎枚举/格式 → 引擎自动修正回写
  （枚举归一：月付/包月→按月；渠道同义词映射；产品名称 K1 模板归一——"5G-A 套餐"→
  "5G-A单品套餐待定档位元"档位占位交确认补充；生效日期 yyyyMMdd→yyyy-MM-dd；
  资源类缺单位补全；套餐固定费缺周期补全 199元→199元/月）；
  无法修正的经 violations 在结束节点提示用户（引擎兜底闭环）
```

#### 3.4.3 门禁与续跑条件（V1.7：门禁下沉工具层；V2.2：确认门禁移除，确认语义由智能体识别）

> 原主流程选择器门禁（节点2 确认门禁/节点4 续跑判定/节点15 审批发起门禁）随 `wf_cpcp_main` 弃用而废止，改由以下机制保障：

```
① 确认门禁（V2.2 修订：已移除后端硬校验，确认语义由智能体识别）：
   save_product_config 不再校验 confirmed 入参与存储 CONFIRMED 标记
   （原 NodeResultService.latestRecord(req_id,"CONFIRMED") 校验与 NOT_CONFIRMED
   拒绝逻辑已删除；NodeResultService.existingNodes 诊断方法一并删除）；
   "未确认不配置"由智能体提示词【技能2】确认语义识别保证——未识别到确认类
   回复时 LLM 不调度 wf_sub_02；
   后端保留：plan_json 合法性校验（5001）+ 同 plan_json 幂等重放

② LLM 串行纪律（提示词【限制】强制）：
   严禁并行调用子工作流；严禁跳过环节；仅依据出参字段判定成败
   （status==SUCCESS / pass==1 / test_passed==通过），失败即中断引导；
   环节结果由子流内部存储节点落库（V1.7 下沉）。

③ 续跑映射（LLM 执行，fail_node → 子工作流）：
    STAGE1_CONFIG → wf_sub_02（智能配置，须重新落地）
    STAGE2_AUDIT  → wf_sub_03（规格稽核，V2.2：自查 config+提取原文复用 offer_id/config_json）
    STAGE3_FEE    → wf_sub_05（资费校准，V2.2：自查 config+提取原文复用 config_json）
    STAGE4_TEST   → wf_sub_04（自动测试，V2.2：自查 config+提取原文复用 offer_id）
   续跑前置：各子工作流按 req_id=入参统一键、node_name=config/spec/fee/test 自查回放
   已成功环节结果（不重复调用写接口）；req_id 沿用原值。

④ 审批发起门禁（工具层硬校验，V1.7）：
   submit_release_approval 后端校验：req_id 入参存在 + 逐一查询
   latestRecord(req_id, "config"/"spec"/"fee"/"test") 四条记录全部非空
   ├─ 四环节齐全 → 放行审批推送
   └─ 缺失任一环节 → 校验拒绝（提示先完成执行主干）
   配套：智能体提示词【技能4】约定四环节全部成功且用户明确确认后才调 wf_sub_06。

⑤ 兜底：用户确认时 req_id 未知 → LLM 先调 query_node_result 检索最近执行方案；
   检索不到 → 提示用户重新提报需求，不得凭空编造 req_id。
```

#### 3.4.4 测试轮询代码节点（wf_sub_04 节点0304，type=6 代码节点，V1.6 更新）
> 实现方式变更：不再使用循环节点，改为 **type=6 代码节点**内嵌轮询代码。平台约束：`code` 字段必填；`inputs` 必须为**平铺 list**（非 {loopParam, inputParameters} 嵌套，否则入参无法注入）。
```
# 平台代码节点（type=6）伪代码（args.params 取入参，ret: Output 返回）
interval = 5            # 轮询间隔（秒），用 asyncio.sleep(5)
max_retry = 360         # 最多 360 次（30 分钟超时）
consecutive_fail = 0    # 连续查询失败计数

for i in range(max_retry):
    resp = get_test_progress(globalId)      # 平台工具调用
    if resp 查询失败:
        consecutive_fail += 1
        if consecutive_fail >= 5:
            ret = {"done": False, "failed": True, "fail_reason": "连续查询失败，转人工（保留 globalId）"}
            return
        await asyncio.sleep(interval); continue
    consecutive_fail = 0

    if resp.done == true:
        ret = {"done": True, "failed": False}
        return
    if resp.failed == true:
        ret = {"done": False, "failed": True, "failIndex": resp.failIndex,
               "fail_reason": "测试失败/中止，仍取完整结果供报告定位失败原因"}
        return
    await asyncio.sleep(interval)

ret = {"done": False, "failed": True, "fail_reason": "测试超时（30 分钟），请凭 globalId 人工续查"}
```

#### 3.4.5 测试报告生成提示词（wf_sub_04 节点6）
```
你是产销品自动测试报告生成助手。基于输入的测试结果数据生成《销售品自动测试报告》，
必须包含以下章节：
1. 测试概要：被测销售品（offerName）、测试流水（globalId）、场景总数/测点总数/通过/失败统计；
2. 受理验证结论（强制章节，不得省略）：
   - 受理凭证：orderId={{orderId}}，offerInstId={{offerInstId}}；
     若为空，写明"未获取到受理凭证，需人工核实"；
   - 逐受理场景结论：按场景（套餐新装 S_O_TC / 副卡加装 S_ADD_CARD / 套餐退订 S_U_TC）
     分别给出该场景受理是否成功 + 关键测点（P_EFF_DATE/P_EXP_DATE/P_STATUS/P_ORD_CNT 等）比对结论；
3. 逐场景明细：每场景测试点比对情况（测点编码/名称/预期值 presetValue/实测值 testValue/结论），
   仅展开列出 resultCode=1（不一致）的测点明细，一致测点按场景汇总条数；
4. AI 总结与建议：引用 objTestSceneRel 的 resultMsg/summaryDesc/suggestion；
5. 总体结论：test_passed（全部场景全部测点一致且受理凭证非空 → 通过；否则失败）。
只基于输入数据生成，不得虚构测点或结论。
```

#### 3.4.6 大模型节点温度值汇总
| 节点 | 温度 | 理由 |
| --- | --- | --- |
| wf_sub_01 节点2/节点4（需求解析、字段映射补全） | 0.2 | 结构化输出，抑制幻觉 |
| wf_sub_03 节点5（整改建议） | 0.2 | 忠实于稽核结果 |
| wf_sub_04 节点7（测试报告） | 0.2 | 数据引用型生成 |
| wf_sub_05 节点5（风险解读） | 0.2 | 忠实于校验结果 |
| wf_sub_06 节点8 501g（7 章节上线报告） | 0.2 | 汇总引用型，报告结构强制 |
| 智能体 LLM（V1.7 环节结果打印/成功详情汇总/异常处置说明，3.1.2/3.1.3/3.1.5 模板） | 0.2 | 逐字引用出参与模板，不加工不臆测 |
| wf_sub_07 告警文案 | 0.2 | — |
| wf_sub_08 节点3（审批状态摘要） | 0.2 | 结构化摘要 |
| 智能体对话（交互层） | 0.2 | 主方案 3.3 统一配置，严谨场景 |

### 3.5 工作流级自测用例（对应主方案 8.1 分层测试第 2 层，V1.3 对齐 V1.7 LLM 智能调度）
| # | 用例 | 步骤 | 预期 |
| --- | --- | --- | --- |
| 1 | 正向全流程 | 输入《产品信息.txt》900102308 改写需求 → 确认 → LLM 串行直调 → 审批确认 | 执行方案生成→确认（智能体识别确认语义，V2.2 无需写标记）→四环节串行直调（每环节打印结果、不停顿）→成功详情+审批提示→确认→审批单生成 |
| 2 | 未确认不配置 | 第一轮结束后直接输入"帮我配置落地"（不携带确认） | LLM 拒绝并提示先确认执行方案；save_product_config 未被调用（CRM 无记录） |
| 3 | 执行方案修改 | 回复修改意见（如"套餐固定费改为 39 元"） | LLM 直调 wf_sub_01 覆盖写同 req_id，重新等待确认；确认后落地为修改后版本 |
| 4 | 待补充字段 | 需求不含套餐固定费且不含流量/语音/短信资源 | 套餐固定费与全部资源字段=待补充列入 pending_fields；其余字段均为 AI补全且无"待补充"残留；产品编码="由智能配置生成"；req_id 为空、无法进入确认流程 |
| 4b | 资源部分提供不拦截 | 需求仅含流量资源（无语音/短信、无价格） | 仅套餐固定费=待补充；语音/短信资源按相似产品 AI 补全（任一类资源已提取到则其余资源字段不算缺失）；产品编码="由智能配置生成" |
| 5 | 串行连续性 | 确认后观察执行过程 | LLM 串行直调四环节一次完成，中途无多余人工询问、无并行调用；每个环节执行后均打印结果（引用出参原文） |
| 6 | 稽核驳回中断与引导 | 构造配置缺陷（如互斥优惠叠加） | 实时稽核同步返回 pass=0 → LLM 立即中断调度 → 打印"异常环节：配置规格稽核+原因+error_list 明细+建议" → 引导重新执行/修改执行方案 |
| 7 | 重新执行续跑 | 用例6 后回复【重新执行】 | LLM 从稽核环节续调 wf_sub_03（不重复调用 wf_sub_02/save_product_config，CRM 无重复记录）；已成功环节结果回放打印 |
| 8 | 修改执行方案回退 | 用例6 后回复【修改执行方案】+ 修改意见 | LLM 直调 wf_sub_01 重新生成执行方案，再次等待确认 |
| 9 | 资费驳回中断 | 构造资费冲突样例 | 资费校准 pass=0 → LLM 中断，打印异常节点+风险清单+引导 |
| 10 | 测试失败中断 | 构造配置错误使测点不一致 | LLM 中断，打印异常节点+失败测点明细+受理验证结论+引导 |
| 11 | 测试超时中断 | 模拟进度长期不 done | 30 分钟超时 → LLM 打印"自动测试超时+globalId"+引导 |
| 12 | 落地与方案一致性 | 对比 plan_json 与 save_result | 四类字段逐项一致，无二次生成痕迹 |
| 13 | 审批发起门禁 | 主干全部成功后不回复"发起审批"，直接要求"帮我上线" | LLM 按提示词【技能4】提示先确认发起审批；wf_sub_06/submit_release_approval 未被调用；回复"暂不"后隔轮发送"发起审批"可续办 |
| 14 | 审批进度消息查询 | 审批推送后发送"查询审批进度" | LLM 直调工具13 返回"审批单号+状态+当前环节+审批人+意见"摘要；未发起审批时提示"未找到审批单" |
| 15 | 监控结果消息查询 | 发送"查询销售品 900102308 监控结果" | LLM 直调工具10 返回订单量/异常量/计费差错率/告警列表；error_count>0 时附告警建议 |
| 16 | 监控告警分支 | wf_sub_07 定时触发且构造 error_count>0 | send_alert 触发，告警文案含产品与异常摘要 |
| 17 | 确认语义识别（V2.2 修订，原确认标记硬校验废止） | 未回复确认直接要求配置 | 智能体按提示词【技能2】拒绝调度智能配置（后端门禁已移除，confirmed 任意值均可落地）；CRM 无写入 |
| 18 | 四环节硬校验（V1.7） | 模拟 LLM 跳步：环节不全直接调 submit_release_approval | 后端校验拒绝（四环节结果缺失）；LLM 引导先完成执行主干 |
| 19 | 18 套餐全量兼容（V1.6） | 依次以 18 个销售品（5G-A 系列 10 个 + 权益随心选系列 8 个）改写需求走正向全流程 | 每个销售品需求分析→确认→四环节串行→成功详情均正常；测试报告 presetValue 与《产品信息.txt》规则值一致 |
| 20 | 权益随心选类套餐兼容（V1.6） | 以 900117022（娱乐版19.9元）改写需求走全流程 | 权益类字段（权益内容/档次资费）AI 补全正确，无"待补充"误标；稽核与资费校验通过 |
| 21 | 未收录销售品降级（V1.6） | 输入不在 18 个销售品之列的产品 ID | 系统提示"该销售品不在存量资料范围，请提供完整需求信息"，不返回伪造模拟数据 |

---

## 4. 知识库细化设计

### 4.1 知识分类总表
| # | 分类名称 | 用途 | 召回场景（挂载点） |
| --- | --- | --- | --- |
| K1 | 产销品业务规范库 | 规格稽核判定依据、需求解析参照、需求提报引导 | wf_sub_01 节点2/节点4；智能体对话 |
| K2 | 资费规则库 | 资费校准依据、风险解读增强 | wf_sub_05 节点3 |
| K3 | 测试规范库 | 测试用例设计规范、受理/计费测试标准、报告模板 | wf_sub_04 节点6 |
| K4 | 存量销售品资料库 | AI 补全字段参照、相似度结果解读、测试预期值人工核对基准 | wf_sub_01 节点4（核心） |
| K5 | FAQ | 智能体直接问答 | 智能体问答（FAQ 直接返回=否，需大模型归纳） |

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
| K4 | 《产品信息.txt》 | 需求方提供 | 全部 18 个销售品全量规则：5G-A 系列 10 个 + 权益随心选系列 8 个（切片入库，见 4.2.3） |
| K5 | 《产销品加载FAQ》 | 自编 | 高频问答（稽核不通过怎么办、如何查询存量产品等） |

#### 4.2.2 命名规范
```
[分类代码]_[文档名]_[版本号]
分类代码：K1规范 / K2资费 / K3测试 / K4存量 / K5FAQ
示例：K2资费_叠加优惠约束说明_V1.0.docx
版本更新：同名新版本上传后删除旧版本切片（先传新再删旧，避免召回空窗）
```

#### 4.2.3 《产品信息.txt》切片入库方案（K4 核心）
| 项 | 策略 |
| --- | --- |
| 源内容 | 18 条销售品记录：5G-A 系列 10 个（900102308 5G-A套餐199元 / 900113043 单品239元 / 900113046 融合199元 / 900102307 融合299元 / 900102313 融合399元 / 900113044 融合239元 / 900102310 单品299元 / 900102312 单品399元 / 900113045 单品199元 / 900102306 融合199元）+ 权益随心选系列 8 个（900117022/900117020/900117027/900117026/900117023/900117024/900117025/900117021，19.9元/29.9元 各娱乐/生活/出行/商超版） |
| 一级拆分 | 按销售品 ID 拆分为 18 个独立文档（每条记录一个文档），解决单条记录过长（>2000字符）导致的切片断裂 |
| 二级拆分 | 每个销售品内部按章节拆分切片：套内资费 / 套外资费 / 过渡期资费 / 副卡 / 流量结转 / 断网授权 / 停机规则 / 计费周期与付费方式 / 销售渠道 / 套餐订购 / 套餐变更 / 退订拆机携出 |
| 切片标题前缀 | 每个切片首行加元信息：`[销售品ID] [销售品名称] [章节名]`（如 `[900102308] [5G-A套餐199元] [套外资费]`），提升检索命中与引用可读性 |
| 用途边界 | 仅用于：① AI 补全字段参照（wf_sub_01 节点4 检索）；② 需求样例改写测试；③ 测试预期值（presetValue）人工核对基准。**不得作为新需求字段来源覆盖用户原始需求**（写入 wf_sub_01 提示词约束） |

### 4.3 切片策略与召回参数
| 项 | 配置 |
| --- | --- |
| 切片大小 | 512 token/片（K4 按章节自然边界拆分，单片 ≤800 token） |
| 切片重叠 | 50 token |
| 检索方式 | 混合检索（语义 + 关键词），语义权重 0.7 |
| top_k | 3（wf_sub_01 节点4 检索 K4）；其他场景默认 2 |
| score 阈值 | 0.75（低于阈值的切片不进入提示词，避免弱相关噪声） |
| 引用展示 | 开启（回复中标注命中文档名，便于核验） |
| 知识库挂载点 | wf_sub_01 节点2/4（K1+K4）、wf_sub_04 节点6（K3）、wf_sub_05 节点3（K2）、智能体对话（K5 及全部分类兜底） |

### 4.4 知识库维护机制
| 机制 | 说明 |
| --- | --- |
| 更新触发 | 新规范发布 / 新资费政策 / 新销售品上线后，由知识运营人员按 4.2.2 命名规范上传新版本 |
| 版本管理 | 同名文档先传新后删旧；每月核对分类内文档版本清单 |
| 质量校验 | 每次更新后执行 3 组固定问答回归（例：查 900102308 套外资费 → 应命中阶梯计费描述） |
| 过期清理 | 下线销售品资料保留 6 个月后归档（不删除，避免历史追溯断档） |
| 权限 | 知识库读写权限限于项目组；FAQ 更新需业务复核 |

---

## 5. 智能体装配清单（平台界面逐项核对）

| # | 装配项 | 配置值 | 核对要点 |
| --- | --- | --- | --- |
| 1 | 助手代码/名称 | `cpcp_product_worker` / 产销品数字员工 | 与主方案 3.1 一致 |
| 2 | 功能介绍 | 主方案 3.1 文案 | — |
| 3 | 提示词 | 主方案 3.2 全文（角色+技能+限制） | 确认含 V1.4 待补充规则、意图→子工作流智能调度映射表/确认语义识别（V2.2：无需写确认标记）/串行直调/异常引导/消息查询技能 |
| 4 | 插件挂载 | 自研插件集 13 个（V1.6：工具1~6 自研模拟实现 + 节点结果存储查询插件 + 工具7~11、13） | 逐一在插件市场勾选；工具13 为审批进度查询 |
| 5 | 工作流挂载 | **仅挂载 `wf_sub_01~08` 八个子工作流（V1.7）** | **不挂载主流程 `wf_cpcp_main`（弃用归档）**；wf_sub_08/wf_sub_07 兜底保留（LLM 默认直调工具13/工具10） |
| 6 | 知识库挂载 | K1~K5 五个分类（V1.5 恢复五分类，字段本体由后端推理引擎承载，不占知识分类） | K4 需确认 18 个销售品文档切片完成 |
| 7 | 模型配置 | 温度 0.2 / 多轮对话 20 轮 / top_p 0.5 | — |
| 8 | FAQ 直接返回 | 否 | 需大模型归纳 |
| 9 | 开场白 | 主方案 3.3 文案 | — |
| 10 | 引导问题 | 5 条（主方案 3.3，V1.5 版：含审批进度查询/监控结果查询/重新执行） | 第 2 条覆盖"确认执行"演示路径 |
| 11 | 答案为空提示 | "抱歉，我仅支持产销品加载相关业务，请更换问题或联系管理员。" | — |
| 12 | 发布范围 | 所有人可见 | 先草稿调试，验收后发布 |

---

## 6. 变量命名约定

### 6.1 工作流变量
| 风格 | 适用 | 示例 |
| --- | --- | --- |
| 小写下划线 | 工作流输入/输出变量 | `requirement_text`、`req_id`（子工作流单入参/存储统一键）、`test_report`、`pending_fields`、`resume_action`、`fail_node`、`approve_confirmed`、`result_json`（子流程自查/存储载荷） |
| 驼峰 | 接口原始出参字段（与接口清单保持一致，不做改名；特例：`offerId` 为后端工具3 参数名，保持 camelCase） | `globalId`、`offerId`、`testScenes`、`orderId`、`offerInstId`、`presetValue`、`testValue` |
| `wf_sub_XX.` 前缀 | 子工作流输出在主流程中的引用 | `wf_sub_02.save_result` |
| req_id + node_name | 节点结果存储寻址（V1.7 统一键：req_id=执行方案存储键；node_name=requirement/config/spec/fee/test/report（V2.2 起 CONFIRMED 废止）） | req_id=`PLAN20260913143025087`、node_name=`spec` |

### 6.2 存储与流水
| 变量 | 规则 | 示例 |
| --- | --- | --- |
| `req_id` | `PLAN` + yyyyMMddHHmmss + 3位随机数（V1.7 统一键，wf_sub_01 拆分代码节点以系统时钟生成、每次分析重新生成） | `PLAN20260913143025087` |
| `globalId`（测试流水） | 接口返回原值，不得重生成 | `50202608252017364447983718` |
| `transactionId` | yyyyMMddHHmmssSSS + 4~6位随机数 | `20260912102450123456` |
| node_name 枚举 | requirement / config / spec / fee / test / report（V1.6 新增 report）；CONFIRMED 已废止（V2.2 确认门禁移除） | `report` |
| 存储介质 | 后端 `pd_ai_node_results` 表持久化（NodeResultService 落库；H2/MySQL 双 DDL），同键覆盖 | — |

### 6.3 枚举值约定（提示词/选择器统一使用）
| 枚举 | 取值 |
| --- | --- |
| 字段来源 | 原始需求 / AI补全（仅两种） |
| 待补充范围 | 仅两种触发情形：套餐固定费（月租费）未提取到，或流量/语音/短信三类资源全部未提取到（任一类资源已提取到则其余资源字段不算缺失） |
| 产品编码 | 不补全：由智能配置环节落地后生成，需求未提供时值填"由智能配置生成" |
| 稽核场景 | spec / fee / all |
| 资费校验场景 | fee / overlay / superposition / all |
| 告警级别 | high / middle / low |
| 确认状态 | 智能体确认语义识别（V2.2：后端 CONFIRMED 确认标记校验已删除，"未确认不配置"由提示词【技能2】保证；node_name 枚举不再使用 CONFIRMED） |
| 审批发起确认 | 用户对话确认"发起审批"（V1.1 新增；V1.7 起门禁由 submit_release_approval 四环节硬校验保障） |
| 续跑指令 | retry_from_fail（从失败环节续跑）/ revise_plan（修改执行方案）（V1.1 新增；V1.7 起由 LLM 按 fail_node 映射续调子流） |
| 失败环节编码 | STAGE1_CONFIG / STAGE2_AUDIT / STAGE3_FEE / STAGE4_TEST（V1.1 新增） |
| 审批状态 | 审批中 / 通过 / 驳回（V1.1 新增） |
| 测试场景编码 | S_O_TC（套餐新装）/ S_ADD_CARD（副卡加装）/ S_U_TC（套餐退订） |
| 测点编码 | P_EFF_DATE / P_EXP_DATE / P_STATUS / P_MAIN_PROD / P_RELY_REL / P_MUTEX_REL / P_ORD_CNT / P_OFFER_NAME / P_OFFER_TYPE / P_PAY_MODE |

---

## 7. 异常处理矩阵

| # | 异常场景 | 触发点 | 系统行为 | 用户感知 | 恢复方式 |
| --- | --- | --- | --- | --- | --- |
| E1 | 相似度分析接口失败 | wf_sub_01 节点3 | 跳过相似产品，仅用知识库 K4 补全 | "相似产品服务暂不可用，已基于存量资料补全" | 自动降级，无需干预 |
| E2 | LLM 输出来源枚举违规 | wf_sub_01 节点4 | 程序化校验失败 → 重新生成（最多2次） | 无感知或"解析重试中" | 2次失败后转人工 |
| E3 | 未确认即触发配置 | 智能体 LLM 意图判定（提示词【技能2】，V2.2） | 拒绝进入执行主干 | "请先确认执行方案后再触发智能配置" | 用户回复确认后 LLM 直调执行主干 |
| E4 | 绕过确认直调 save_product_config（LLM 跳步/外部直调） | V2.2 门禁已移除——后端不再拦截，仅做 plan_json 合法性与幂等校验 | 正常落地（确认与否由调用方保证） | 调用方收到落地结果 | 防跳步职责回归智能体提示词【技能2】确认语义识别 |
| E5 | 自查无执行方案（req_id/node_name 查无记录或 total=0） | wf_sub_02 自查节点2 | 终止并返回提示 | "未找到执行方案，请先完成需求分析" | 重新走需求分析 |
| E6 | 配置落地 FAIL | wf_sub_02 落地节点4 → LLM 结果判定 | **主干中断** → LLM 异常处置输出 | 打印"异常环节：智能配置"+失败分类明细+建议，引导重新执行/修改执行方案 | 用户二选一后 LLM 续调 |
| E7 | 实时稽核超时 | wf_sub_03 稽核节点4 | 重试1次后仍超时 → **主干中断** → LLM 异常处置 | "异常环节：配置规格稽核（超时）"，保留请求报文 | 回复【重新执行】LLM 续调稽核环节 |
| E8 | 稽核 pass=0 | LLM 环节2 结果判定（wf_sub_03 出参） | **主干中断** → LLM 异常处置（可联动 send_alert(high)） | 打印异常环节+error_list 明细+整改建议+引导 | 重新执行（不推荐）或修改执行方案 |
| E9 | 资费校验 pass=0 | LLM 环节3 结果判定（wf_sub_05 出参） | **主干中断** → LLM 异常处置 | 打印异常环节+资费风险清单+引导 | 修改执行方案（修正资费） |
| E10 | 测试发起失败 resultCode=1 | wf_sub_04 发起节点4 → LLM 结果判定 | **主干中断** → LLM 异常处置 | 打印"异常环节：销售品自动测试"+resultMsg+引导 | 排查销售品状态后重新执行 |
| E11 | 测试场景为空 | wf_sub_04 场景节点5 | **主干中断** → 异常A | "该销售品未匹配到测试场景，请检查配置"+引导 | 修改执行方案后重测 |
| E12 | 进度查询连续5次失败 | wf_sub_04 节点0304 | **主干中断** → 异常A | 提示凭 globalId 人工续查+引导 | 人工查询后决定重新执行 |
| E13 | 测试超时（30分钟） | wf_sub_04 节点0304 | **主干中断** → 异常A | 提示超时 + globalId + 引导 | 人工续查或重新执行 |
| E14 | orderId/offerInstId 为空 | wf_sub_04 节点7 | 报告正常生成（不算异常，不中断） | 受理验证结论标注"未获取到受理凭证，需人工核实" | 人工核实受理结果 |
| E15 | 未经确认发起审批 | LLM 审批引导（提示词【技能4】）/ wf_sub_06 节点10 后端硬校验 | 四环节结果不齐时拒绝推送 | "执行结果已保留，回复【发起审批】后才能提交审批流" | 用户确认后 LLM 直调 wf_sub_06 |
| E16 | 审批推送失败 | wf_sub_06 节点10 | 重试1次后终止 | 返回失败原因 | 修复后重新推送（幂等） |
| E17 | 监控接口失败 | wf_sub_07 节点2 | 终止本轮 | "监控查询失败" | 下一周期自动重试 |
| E18 | 续跑参数非法 | LLM 续跑映射（fail_node 非法或 req_id 查无记录） | 按首次执行处理（重新串行调度主干） | "未找到可续跑的执行记录，已从头开始执行主干" | 无需干预 |
| E19 | 重新执行时写接口重复调用防护 | 各子工作流自查前置校验 | 查 req_id=入参统一键 + node_name=本环节名 已存在成功记录 → 跳过写接口直接回放 | "环节已成功，直接从失败环节继续" | 无需干预 |
| E20 | 主干中段接口异常（稽核/资费/测试接口网络错误） | wf_sub_03/05/04 出参 resultCode=NET_ERROR/TIMEOUT | **主干中断** → LLM 异常处置 | 打印异常环节+网络异常说明+引导 | 稍后回复【重新执行】LLM 续跑 |
| E21 | 审批进度查询无审批单 | wf_sub_08 节点2 | 返回查无记录 | "未找到该销售品的审批单，请确认是否已发起审批" | 先发起审批后再查询 |
| E22 | 监控/审批查询缺少必填参数 | 智能体消息查询入口 | 不发起调用，先追问用户 | "请提供销售品ID（或审批单号）" | 用户补齐参数后重查 |
| E23 | 环节结果存储写入失败 | 各子工作流结束前存储节点 | 打印结果不受影响；记录存储失败日志 | 无感知（仅影响续跑回放） | 续跑退化为全量重跑，提示用户 |

---

## 附录：实施核对清单

### A. 插件阶段（对应主方案阶段2）
- [ ] 13 个工具全部录入（6 API + 节点结果存储查询插件 + 6 自研，含工具13 query_approval_status）
- [ ] tcpCont 拼装自测通过（2.4 节自测项 #8）
- [ ] 实时稽核插件工具（普通接口）完成联通测试（工具2）
- [ ] save_product_config 幂等与合法性校验验证通过（#6/#7；V2.2 确认门禁已移除）
- [ ] submit_release_approval 审批发起校验（NOT_CONFIRMED 拒绝）验证通过（工具9）
- [ ] 节点结果存储读写一致（#9）
- [ ] 每个必填入参"为空提示"验证（#10）

### B. 工作流阶段（对应主方案阶段4）
- [ ] wf_sub_01~06 导入后核对学生节点数/边数（3.0 表）：sub_01=9/8、sub_02=6/5（V2.2 含提取原文代码节点）、sub_03=7/6（V2.2 含自查+提取）、sub_04=13/12（V2.2 含自查+提取）、sub_05=10/9（V2.2 含自查+提取）、sub_06=11/10
- [ ] 各子工作流 req_id 单入参自查链路验证：开始(req_id) → query_node_result(GET，submit_way=get) → **提取原文代码节点（V2.2 CODE_EXTRACT_RECORD）** → 工具节点；结束前 save_node_result 链路逐条验证
- [ ] wf_sub_02 落地节点入参验证：req_id=开始节点入参、plan_json=提取节点 record_json 原样透传（非 list 数组）、confirmed=true（仅记录）；后端 PARAM_MISSING 不再出现
- [ ] wf_sub_04 节点0304 验证：type=6 代码节点、inputs 平铺 list、asyncio.sleep(5) 轮询正常、fail_reason 出参非空
- [ ] wf_sub_06 串行自查验证：501q1→q2→q3→q4→q5→501s 链式连接；报告 7 章节完整；node_name=report 落库
- [ ] 8 个子工作流单独调试通过（含 wf_sub_08 审批进度查询）
- [ ] V1.7 LLM 智能调度联调：意图映射表命中、确认语义识别（V2.2：无需写 CONFIRMED 标记）、串行直调 wf_sub_02→03→05→04、每环节结果打印、异常中断引导、续跑映射
- [ ] 工具层校验联调（V2.2 修订）：save_product_config 确认门禁已移除（verified 任意值可落地+幂等）；四环节不全调 submit_release_approval 被拒绝
- [ ] **不挂载主流程 wf_cpcp_main（V1.7 弃用归档，JSON 保留）**
- [ ] 3.5 节工作流级用例 #1~#21 全部通过

### C. 知识库阶段（对应主方案阶段3）
- [ ] 5 个分类创建，K1~K3/K5 文档上传
- [ ] 《产品信息.txt》18 个销售品按 4.2.3 拆分切片入库
- [ ] 固定问答回归 3 组通过

### D. 集成与验收（对应主方案阶段5~6）
- [ ] 第 5 章装配清单 12 项逐项勾选（重点：工作流仅挂载 8 个子流、提示词含 V1.7 意图映射表）
- [ ] 主方案 8.2 验收指标全量核验（重点：确认门禁 0% 触发率（工具层硬校验）、LLM 串行调度连续性、异常处置完整性、续跑正确性、消息查询可用性、待补充规则符合率 100%、受理验证结论完整性）
- [ ] 端到端演示剧本彩排（含反向分支与异常引导分支）

### E. 模拟数据 18 销售品兼容性核对（V1.6 新增，对应 2.5 节与 3.5 用例 #17~#19）
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

核对要点：① 工具1 相似度分析能命中对应销售品；② 工具6 presetValue 与该销售品《产品信息.txt》规则值一致；③ 工具2/8 稽核与资费校验按该销售品规则判定；④ 权益随心选系列重点核对权益类字段 AI 补全。
