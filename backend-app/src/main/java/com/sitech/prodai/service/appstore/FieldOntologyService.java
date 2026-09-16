package com.sitech.prodai.service.appstore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 产销品加载 AI 应用 · 字段本体推理服务（V3.0，工具14 field_ontology_reason 后端）。
 * <p>
 * 设计模式：规则引擎（策略+责任链组合）——新 24 字段（3 模块/9 分类）的本体定义（枚举/格式/默认值/兜底口径）
 * 以代码常量建模为字段本体注册表，推理按「枚举校验→格式校验→默认值推理→兜底口径」逐字段执行：
 * <ol>
 *   <li>validate：LLM 补全结果逐字段做本体合法性推理，非法值返回 reason（期望规则）供 LLM 重填；</li>
 *   <li>complete：缺失/待补充字段按本体默认值推理补全（待补充项全部可推理，仅价格类维持待补充）。</li>
 * </ol>
 * 本体定义内聚于本服务（单一事实源），知识侧不再维护 K6 文档。
 * V3.0 字段重构：字段名/分类对齐《平台配置清单》输出样例（套餐名称/套餐编码/套餐档位/...），
 * 来源标注收敛为两态：原始需求/AI补全。
 */
@Service
public class FieldOntologyService {

    private static final Logger log = LoggerFactory.getLogger(FieldOntologyService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 字段本体定义：枚举/格式/默认值/兜底口径（V3.0 新 24 字段：3 模块/9 分类，字段名与《平台配置清单》样例一一对应） */
    private static final Map<String, FieldSpec> SPECS = new LinkedHashMap<>();

    /** 渠道类型同义词映射表：[变体关键词, 本体枚举值]，供多选归一先行命中 */
    private static final String[][] CHANNEL_SYNONYMS = {
            {"营业厅", "实体渠道"}, {"门店", "实体渠道"}, {"实体", "实体渠道"},
            {"APP", "电子渠道"}, {"app", "电子渠道"}, {"网厅", "电子渠道"},
            {"线上", "电子渠道"}, {"电子", "电子渠道"},
            {"直销", "直销渠道"}, {"客户经理", "直销渠道"}, {"政企", "直销渠道"},
    };

    static {
        // 基础信息 / 产品属性
        spec("套餐名称", "产品属性", null, null,
                "文本；用户命名原样保留（口语名不强制归一模板）", "待补充", false);
        spec("套餐编码", "产品属性", null, Pattern.compile("^\\d{9}$|^系统待生成$"),
                "9位数字或\"系统待生成\"（引擎不做补全，由智能配置环节落地后生成）",
                "系统待生成", false);
        spec("套餐档位", "产品属性", null, Pattern.compile("^\\d+(\\.\\d+)?元$|^待补充$"),
                "金额（元），须与月费/月租一致，如 29元", "待补充", false);
        spec("套餐属性", "产品属性", Arrays.asList("主资费", "可选包", "增值包"), null,
                "枚举：主资费/可选包/增值包", "主资费", false);
        spec("计费周期", "产品属性", Arrays.asList("自然月"), null,
                "枚举：自然月", "自然月", false);
        // 基础信息 / 生命周期
        spec("套餐有效期", "生命周期", null, null,
                "文本；如\"长期有效\"/\"2年，自动续展\"", "长期有效", false);
        spec("到期处理方式", "生命周期", Arrays.asList("自动续订", "自动续展", "到期终止"), null,
                "枚举：自动续订/自动续展/到期终止", "自动续订", false);
        // 基础信息 / 销售属性
        spec("适用用户", "销售属性", null, null,
                "文本；默认\"新老用户均可订购\"", "新老用户均可订购", false);
        spec("销售渠道", "销售属性", Arrays.asList("实体渠道", "电子渠道", "直销渠道"), null,
                "枚举（可多选，顿号分隔）：实体渠道/电子渠道/直销渠道；无参照默认三者全选",
                "实体渠道、电子渠道、直销渠道", false);
        // 资源配置 / 套餐内基础资源
        spec("国内通用流量", "套餐内基础资源", null, Pattern.compile("^\\d+(\\.\\d+)?GB$|^无$|^待补充$"),
                "数值+单位（GB），如 30GB", "无", false);
        spec("本地语音", "套餐内基础资源", null, Pattern.compile("^\\d+分钟(（省内）)?$|^\\d+分钟$|^无$|^待补充$"),
                "数值+单位（分钟），如 100分钟（省内）", "无", false);
        spec("短信", "套餐内基础资源", null, Pattern.compile("^\\d+条$|^无$|^待补充$"),
                "数值+单位（条），如 50条；无短信填\"无\"", "无", false);
        // 资源配置 / 套餐内权益配置
        spec("是否允许办理副卡", "套餐内权益配置", Arrays.asList("允许", "不允许"), null,
                "枚举：允许/不允许", "允许", false);
        // 资源配置 / 套外资费标准
        spec("套外流量-计费标准", "套外资费标准", null, null,
                "金额+单位（元/GB 或阶梯描述），如 1元/GB", "无", false);
        spec("套外语音-国内通话", "套外资费标准", null, null,
                "金额+单位（元/分钟），如 0.15元/分钟", "无", false);
        spec("套外短彩信-短/彩信", "套外资费标准", null, null,
                "金额+单位（元/条），如 0.1元/条", "无", false);
        // 业务规则 / 订购与生效
        spec("新入网生效方式", "订购与生效", Arrays.asList("立即生效", "次月1日生效"), null,
                "枚举：立即生效/次月1日生效", "立即生效", false);
        spec("老用户生效方式", "订购与生效", Arrays.asList("立即生效", "次月1日生效"), null,
                "枚举：立即生效/次月1日生效", "次月1日生效", false);
        spec("过渡期资费规则", "订购与生效", null, null,
                "文本；默认\"按日（当月实际天数）计扣\"", "按日（当月实际天数）计扣", false);
        // 业务规则 / 变更/退订/拆机
        spec("套餐变更范围", "变更/退订/拆机", null, null,
                "文本；默认\"可变更至中国电信其他在售套餐\"", "可变更至中国电信其他在售套餐", false);
        spec("变更生效方式", "变更/退订/拆机", Arrays.asList("次月1号生效", "立即生效"), null,
                "枚举：次月1号生效/立即生效", "次月1号生效", false);
        spec("退订规则", "变更/退订/拆机", null, null,
                "文本；默认口径：允许退订，次月生效", "允许退订，次月生效", false);
        // 业务规则 / 计费/支付/风控
        spec("付费方式", "计费/支付/风控", Arrays.asList("后付费", "预付费"), null,
                "枚举：后付费/预付费", "后付费", false);
        spec("支付方式", "计费/支付/风控", Arrays.asList("账单支付", "充值支付"), null,
                "枚举：账单支付/充值支付", "账单支付", false);
        spec("流量结转规则", "计费/支付/风控", Arrays.asList("结转", "不结转"), null,
                "枚举：结转/不结转", "结转", false);
        spec("断网授权", "计费/支付/风控", null, null,
                "文本；默认\"套外流量使用至600元时暂停上网\"", "套外流量使用至600元时暂停上网", false);
    }

    private static void spec(String field, String category, List<String> enums, Pattern format,
                             String rule, String defaultValue, boolean fallback) {
        SPECS.put(field, new FieldSpec(field, category, enums, format, rule, defaultValue, fallback));
    }

    /** 字段本体规格（值对象） */
    static final class FieldSpec {
        final String field;
        final String category;
        final List<String> enums;
        final Pattern format;
        final String rule;
        final String defaultValue;
        final boolean fallback;
        final boolean multi;

        FieldSpec(String field, String category, List<String> enums, Pattern format,
                  String rule, String defaultValue, boolean fallback) {
            this.field = field;
            this.category = category;
            this.enums = enums;
            this.format = format;
            this.rule = rule;
            this.defaultValue = defaultValue;
            this.fallback = fallback;
            this.multi = enums != null && "销售渠道".equals(field);
        }
    }

    /**
     * 接口一：字段本体推理 validate——LLM 补全结果逐字段校验合法性。
     * 入参 fields_json：[{"field":"..","value":"..","source":".."}]；
     * 出参 pass=1/0，violations 数组每项含 field/value/reason（期望规则）。
     */
    public Map<String, Object> validate(String fieldsJson) {
        List<Map<String, Object>> fields = parseFields(fieldsJson);
        List<Map<String, Object>> violations = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            String field = str(f.get("field"));
            String value = str(f.get("value"));
            FieldSpec spec = SPECS.get(field);
            if (spec == null || value.isEmpty() || "待补充".equals(value)) {
                continue; // 未注册字段不校验（透传）；待补充由 pending_fields 流程处理
            }
            String err = checkValue(spec, value);
            if (err != null) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("field", field);
                v.put("value", value);
                v.put("reason", err);
                violations.add(v);
            }
        }
        Map<String, Object> body = ok();
        body.put("pass", violations.isEmpty() ? "1" : "0");
        body.put("violations", violations);
        return body;
    }

    /**
     * 接口二：字段本体推理 complete——缺失字段按本体默认值推理补全。
     * 入参 fields_json 同上；对 value 为空且 default_value 非空的字段补默认值（source=AI补全）；
     * V2.2：待补充项（value=待补充）同样按默认值推理补全，仅价格类（套餐档位）维持"待补充"。
     * 出参 completed 数组（field/value/defaulted/reason），fields_json 为补全后的完整数组。
     */
    public Map<String, Object> complete(String fieldsJson) {
        List<Map<String, Object>> fields = parseFields(fieldsJson);
        List<Map<String, Object>> completed = new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            String field = str(f.get("field"));
            String value = str(f.get("value"));
            FieldSpec spec = SPECS.get(field);
            if (spec != null && (value.isEmpty() || "待补充".equals(value))) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("field", field);
                if (spec.defaultValue != null && !"待补充".equals(spec.defaultValue)) {
                    value = spec.defaultValue;
                    c.put("value", value);
                    c.put("defaulted", "1");
                    c.put("reason", spec.rule);
                    completed.add(c);
                } else if ("待补充".equals(spec.defaultValue)) {
                    c.put("value", "待补充");
                    c.put("defaulted", "0");
                    c.put("reason", "价格类字段不可推理，维持待补充：" + spec.rule);
                    completed.add(c);
                } else {
                    c.put("value", "");
                    c.put("defaulted", "0");
                    c.put("reason", "本体无默认值，需 LLM 按相似产品补全");
                    completed.add(c);
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", field);
            item.put("value", value);
            item.put("source", str(f.get("source")));
            out.add(item);
        }
        Map<String, Object> body = ok();
        body.put("completed", completed);
        body.put("fields_json", toJson(out));
        return body;
    }

    /**
     * 接口四（V2.1 一体推理）：reason = validate + 修正 + complete，闭环动作。
     * 逐字段执行：
     * <ol>
     *   <li>value 为空或"待补充" → 默认值推理补全（V2.2：待补充项全部可推理，仅价格类（套餐档位）例外——
     *       价格必须由用户确认，维持"待补充"交待补充流程）；</li>
     *   <li>value 非空 → 本体校验；非法值直接按本体规则**修正回写**（枚举归一：月付/包月→后付费、
     *       渠道类型同义词映射、套餐档位金额归一（312元/月→312元）、资源缺单位补全、
     *       套餐名称去首尾空白（口语名保留）等；
     *       无法修正的保留原值并记入 violations）；</li>
     *   <li>修正回写后 source 统一标"AI补全"（原值非"原始需求"时）。</li>
     * </ol>
     * 出参：fixed（修正/补全明细）、violations（无法修正项）、fields_json（推理后的完整字段数组）。
     * 工作流 004a 代码节点从 fields_json 取推理后结果组装 plan_json，实现引擎兜底闭环。
     */
    public Map<String, Object> reason(String fieldsJson) {
        List<Map<String, Object>> fields = parseFields(fieldsJson);
        List<Map<String, Object>> fixed = new ArrayList<>();
        List<Map<String, Object>> violations = new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            String field = str(f.get("field"));
            String value = str(f.get("value"));
            String source = str(f.get("source"));
            String remark = str(f.get("remark"));
            FieldSpec spec = SPECS.get(field);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", field);
            item.put("value", value);
            item.put("source", source);
            if (spec == null) {
                out.add(item); // 未注册字段透传
                continue;
            }
            // ⓪ V2.9 语义备注消歧：值非空且备注含"非"语义归属说明（如"发布时间，非生效方式"）
            //    → 本字段不参与本体校验/修正，保留原值并在 fixed 中留痕，交上游决定是否另立字段
            if (!remark.isEmpty() && isExclusionRemark(remark) && !value.isEmpty()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("field", field);
                c.put("value", value);
                c.put("defaulted", "0");
                c.put("action", "remark_excluded");
                c.put("reason", "用户备注\"" + remark + "\"表明该值非本字段语义，已跳过本体校验（值原样保留，不生成 violation）");
                fixed.add(c);
                out.add(item);
                continue;
            }
            // ① 缺失/待补充 → 默认值推理补全（V3.0：待补充项全部可推理，价格类除外——价格必须由用户确认）
            if (value.isEmpty() || "待补充".equals(value)) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("field", field);
                if (spec.defaultValue != null) {
                    boolean fromPending = "待补充".equals(value);
                    value = spec.defaultValue;
                    source = "AI补全";
                    c.put("value", value);
                    c.put("defaulted", "1");
                    c.put("action", "defaulted");
                    c.put("reason", (fromPending ? "待补充项按本体默认值推理补全：" : "缺失字段按本体默认值推理补全：") + spec.rule);
                    // 价格类字段无法推理：保持"待补充"，交待补充流程提示用户补充
                    if ("待补充".equals(value)) {
                        c.put("action", "fallback");
                        c.put("defaulted", "0");
                    }
                } else {
                    c.put("value", "");
                    c.put("defaulted", "0");
                    c.put("action", "none");
                    c.put("reason", "本体无默认值，维持留空");
                }
                fixed.add(c);
            } else if (!"系统待生成".equals(value)) {
                // ② 非空 → 本体校验 + 修正回写
                String err = checkValue(spec, value);
                if (err != null) {
                    String corrected = correctValue(spec, value);
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("field", field);
                    c.put("value", value);
                    if (corrected != null) {
                        value = corrected;
                        if (!"原始需求".equals(source)) {
                            source = "AI补全";
                        }
                        c.put("defaulted", "0");
                        c.put("corrected", corrected);
                        c.put("reason", err + "；已按本体规则修正");
                    } else {
                        c.put("defaulted", "0");
                        c.put("corrected", "");
                        c.put("reason", err + "；无法自动修正，保留原值待人工处理");
                        Map<String, Object> v = new LinkedHashMap<>();
                        v.put("field", field);
                        v.put("value", value);
                        v.put("reason", err);
                        violations.add(v);
                    }
                    fixed.add(c);
                }
            }
            item.put("value", value);
            item.put("source", source);
            out.add(item);
        }
        Map<String, Object> body = ok();
        body.put("fixed", fixed);
        body.put("violations", violations);
        body.put("fields_json", toJson(out));
        return body;
    }

    /** 枚举归一修正：把 LLM 的变体表述映射为本体枚举合法值；无法修正返回 null */
    private String correctValue(FieldSpec spec, String value) {
        String v = value.trim();
        // 套餐档位金额归一："312元"、"312"、"312元/月"→"312元"（金额统一为"数值+元"，周期口径由计费周期字段承载）
        if ("套餐档位".equals(spec.field)) {
            java.util.regex.Matcher m = Pattern.compile("^(\\d+(\\.\\d+)?)元?(?:/月|每月|/月租)?$").matcher(v);
            if (m.matches()) {
                return m.group(1) + "元";
            }
            return null;
        }
        // 套餐名称归一：仅去除首尾空白与多余分隔符，口语名保留（V3.0 不强制 K1 模板）
        if ("套餐名称".equals(spec.field)) {
            String n = v.replace("　", " ").trim();
            return n.isEmpty() ? null : n;
        }
        // 套外资费归一：数值缺单位补单位
        if ("套外流量-计费标准".equals(spec.field)) {
            java.util.regex.Matcher m = Pattern.compile("^(\\d+(\\.\\d+)?)\\s*元\\s*/\\s*[Gg][Bb]?$").matcher(v);
            if (m.matches()) {
                return m.group(1) + "元/GB";
            }
            return null;
        }
        if ("套外语音-国内通话".equals(spec.field)) {
            java.util.regex.Matcher m = Pattern.compile("^(\\d+(\\.\\d+)?)\\s*元\\s*/\\s*分?钟?$").matcher(v);
            if (m.matches()) {
                return m.group(1) + "元/分钟";
            }
            return null;
        }
        if ("套外短彩信-短/彩信".equals(spec.field)) {
            java.util.regex.Matcher m = Pattern.compile("^(\\d+(\\.\\d+)?)\\s*元\\s*/\\s*条?$").matcher(v);
            if (m.matches()) {
                return m.group(1) + "元/条";
            }
            return null;
        }
        // 资源类归一：缺单位补单位（"60G"→"60GB"、"1000分钟"缺"分钟"等）
        if ("国内通用流量".equals(spec.field)) {
            java.util.regex.Matcher m = Pattern.compile("^(\\d+(\\.\\d+)?)[GgＧ][BbＢ]?$").matcher(v);
            if (m.matches()) {
                return m.group(1) + "GB";
            }
            if (v.matches("^无|待补充$")) {
                return v;
            }
            return null;
        }
        if ("本地语音".equals(spec.field)) {
            java.util.regex.Matcher m = Pattern.compile("^(\\d+)分?钟?（?(省内)?）?$").matcher(v);
            if (m.matches()) {
                return m.group(2) == null || m.group(2).isEmpty()
                        ? m.group(1) + "分钟" : m.group(1) + "分钟（省内）";
            }
            return null;
        }
        if ("短信".equals(spec.field)) {
            java.util.regex.Matcher m = Pattern.compile("^(\\d+)条?$").matcher(v);
            if (m.matches()) {
                return m.group(1) + "条";
            }
            return null;
        }
        if (spec.enums == null) {
            return null;
        }
        // 付费方式归一：月付/包月→后付费；先付/预付→预付费
        if ("付费方式".equals(spec.field)) {
            if (v.contains("后付") || v.contains("月付") || v.contains("包月")) {
                return "后付费";
            }
            if (v.contains("预付") || v.contains("先付") || v.contains("充值")) {
                return "预付费";
            }
            return null;
        }
        // 单枚举字段：包含匹配
        if (!spec.multi) {
            for (String e : spec.enums) {
                if (v.contains(e)) {
                    return e;
                }
            }
            return null;
        }
        // 多选字段（销售渠道）：逐项归一（先查同义词映射表，再枚举包含匹配）
        StringBuilder sb = new StringBuilder();
        for (String part : v.split("[、,，]")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            String hit = null;
            // ① 同义词映射：营业厅/门店→实体渠道，APP/网厅/线上/电子→电子渠道，直销/客户经理/政企→直销渠道
            for (String[] kv : CHANNEL_SYNONYMS) {
                if (p.contains(kv[0])) {
                    hit = kv[1];
                    break;
                }
            }
            // ② 枚举包含匹配（同义词表未命中时）
            if (hit == null) {
                for (String e : spec.enums) {
                    if (p.contains(e)) {
                        hit = e;
                        break;
                    }
                }
            }
            if (hit != null && !sb.toString().contains(hit)) {
                if (sb.length() > 0) {
                    sb.append("、");
                }
                sb.append(hit);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 接口三：本体定义查询 ontology（供调试/演示查看注册的字段本体规格）。
     */
    public Map<String, Object> ontology() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (FieldSpec s : SPECS.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("field", s.field);
            m.put("category", s.category);
            m.put("enums", s.enums == null ? "" : String.join("/", s.enums));
            m.put("rule", s.rule);
            m.put("default_value", s.defaultValue == null ? "" : s.defaultValue);
            m.put("fallback", s.fallback ? "1" : "0");
            list.add(m);
        }
        Map<String, Object> body = ok();
        body.put("fields", list);
        return body;
    }

    /**
     * V2.0 接口五：融合组级校验 group_check（技能包 cpcp_api.py ontology_reason 组结构分支第二动作）。
     * 入参（cpcp_api.py 组结构分支报文）：{action:"group_check", offer_type, group_rules,
     * main_offer:{role, fields}, member_offers:[{role, fields}]}（fields 可为推理后 fields_json 串或数组）；
     * 出参：{code, msg, group_violations:[{item, level, desc, suggest}]}——格式与 violations 同构。
     * 校验口径（POC 规则引擎，确定性输出）：
     * ① 成员角色封闭枚举：主卡套餐/宽带/天翼高清/副卡功能费/权益包/其他，越界 → error；
     * ② 组级互斥：同角色成员重复出现（如两个权益包）→ error；
     * ③ 依赖核对：group_rules.依赖 中 type=OPTIONAL_DEPEND 的成员出现在 member_offers
     *    时为可选依赖（通过，留 warning 提示确认办理口径）；
     * ④ 成员价格跨成员照搬检测：两成员同名价格字段值完全一致且非"待补充/省内自定" → warning（人工核对）。
     */
    public Map<String, Object> groupCheck(Map<String, Object> req) {
        Set<String> MEMBER_ROLES = Set.of("主卡套餐", "宽带", "天翼高清", "副卡功能费", "权益包", "其他");
        List<Map<String, Object>> violations = new ArrayList<>();
        List<Map<String, Object>> members = castMemberOffers(req.get("member_offers"));
        // ① 成员角色封闭枚举
        for (Map<String, Object> m : members) {
            String role = str(m.get("role"));
            if (!MEMBER_ROLES.contains(role)) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("item", "group:" + role);
                v.put("level", "error");
                v.put("desc", "成员角色\"" + role + "\"不在封闭枚举内（主卡套餐/宽带/天翼高清/副卡功能费/权益包/其他）");
                v.put("suggest", "修正成员角色命名，成员构成以 offer_group 下发为准");
                violations.add(v);
            }
        }
        // ② 组级互斥：同角色重复
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> m : members) {
            String role = str(m.get("role"));
            if (!seen.add(role)) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("item", "group:" + role);
                v.put("level", "error");
                v.put("desc", "融合组内成员\"" + role + "\"重复出现，违反组级互斥约束");
                v.put("suggest", "移除重复成员，同角色成员仅保留一个");
                violations.add(v);
            }
        }
        // ③ 依赖核对：OPTIONAL_DEPEND 成员出现时提示确认（warning，不阻断）
        Object rulesObj = req.get("group_rules");
        if (rulesObj instanceof Map<?, ?> rules) {
            Object deps = ((Map<?, ?>) rules).get("依赖");
            if (deps instanceof List<?> depList) {
                Set<String> configRoles = new LinkedHashSet<>();
                for (Map<String, Object> m : members) {
                    configRoles.add(str(m.get("role")));
                }
                for (Object dep : depList) {
                    if (dep instanceof Map<?, ?> depMap
                            && "OPTIONAL_DEPEND".equals(String.valueOf(depMap.get("type")))) {
                        String member = String.valueOf(depMap.get("member"));
                        if (configRoles.contains(member)) {
                            Map<String, Object> v = new LinkedHashMap<>();
                            v.put("item", "group:" + member);
                            v.put("level", "warning");
                            v.put("desc", "成员\"" + member + "\"与主卡套餐为可选依赖关系（OPTIONAL_DEPEND），已按成员内配置加载");
                            v.put("suggest", "按省内配置口径确认是否同步开通");
                            violations.add(v);
                        }
                    }
                }
            }
        }
        // ④ 跨成员价格照搬检测（warning）
        Map<String, String> mainPrices = priceValuesOf(req.get("main_offer"));
        Map<String, Map<String, String>> memberPrices = new LinkedHashMap<>();
        for (Map<String, Object> m : members) {
            memberPrices.put(str(m.get("role")), priceValuesOf(m));
        }
        for (Map.Entry<String, String> e : mainPrices.entrySet()) {
            for (Map.Entry<String, Map<String, String>> me : memberPrices.entrySet()) {
                String mv = me.getValue().get(e.getKey());
                if (mv != null && mv.equals(e.getValue())
                        && !"待补充".equals(mv) && !"省内自定".equals(mv) && !mv.isBlank()) {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("item", "group:" + me.getKey());
                    v.put("level", "warning");
                    v.put("desc", "成员\"" + me.getKey() + "\"的价格字段\"" + e.getKey() + "\"与主卡套餐值相同（" + mv
                            + "），疑似跨成员照搬");
                    v.put("suggest", "核对成员价格（主套餐档位与各成员月功能费互相独立）");
                    violations.add(v);
                }
            }
        }
        Map<String, Object> body = ok();
        body.put("group_violations", violations);
        return body;
    }

    /** member_offers 入参解析：fields 可为推理后 fields_json 串（先 reason 回传）或数组，取 field/value 对 */
    private List<Map<String, Object>> castMemberOffers(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        m.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    result.add(m);
                }
            }
        }
        return result;
    }

    /** 单个成员（或主商品）的价格字段值提取：fields_json 串或 fields 数组 → field->value（价格类字段） */
    private Map<String, String> priceValuesOf(Object memberObj) {
        Map<String, String> prices = new LinkedHashMap<>();
        if (!(memberObj instanceof Map<?, ?> map)) {
            return prices;
        }
        Object fieldsObj = ((Map<?, ?>) map).get("fields");
        List<Map<String, Object>> fields = new ArrayList<>();
        if (fieldsObj instanceof String s) {
            try {
                JsonNode node = MAPPER.readTree(s);
                if (node.isArray()) {
                    for (JsonNode n : node) {
                        Map<String, Object> f = new LinkedHashMap<>();
                        f.put("field", n.path("field").asText(""));
                        f.put("value", n.path("value").asText(""));
                        fields.add(f);
                    }
                }
            } catch (Exception ignore) {
                return prices;
            }
        } else if (fieldsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> f) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("field", String.valueOf(f.get("field")));
                    m.put("value", String.valueOf(f.get("value")));
                    fields.add(m);
                }
            }
        }
        for (Map<String, Object> f : fields) {
            String field = str(f.get("field"));
            if (field.contains("功能费") || "套餐档位".equals(field)) {
                prices.put(field, str(f.get("value")));
            }
        }
        return prices;
    }

    /* ---------------- 推理核心 ---------------- */

    /** 单字段本体合法性推理：枚举→格式，返回 null 表示合法 */
    private String checkValue(FieldSpec spec, String value) {
        if (spec.enums != null) {
            if (spec.multi) {
                for (String part : value.split("[、,，]")) {
                    if (!spec.enums.contains(part.trim())) {
                        return "非法枚举值\"" + part.trim() + "\"，" + spec.rule;
                    }
                }
                return null;
            }
            if (!spec.enums.contains(value)) {
                return "非法枚举值\"" + value + "\"，" + spec.rule;
            }
            return null;
        }
        if (spec.format != null && !spec.format.matcher(value).matches()) {
            return "格式不符合本体定义，" + spec.rule;
        }
        return null;
    }

    /**
     * fields_json 解析：兼容三种输入形态，逐层下钻到字段数组：
     * <ol>
     *   <li>纯数组：[{"field":"..","value":".."}]；</li>
     *   <li>plan_output 包裹串（V2.1 工作流节点31 实际传参形态，带任意前缀如 "plan_output: "）：
     *       定位首个 '{' 提取 JSON 体，下钻 plan_json.fields；plan_json 缺失时兜底顶层 fields；</li>
     *   <li>直接含 fields 键的对象：{"fields":[...]}。</li>
     * </ol>
     * JSON 体提取失败或无字段数组时返回空列表（不抛异常，保持引擎幂等）。
     */
    private List<Map<String, Object>> parseFields(String fieldsJson) {
        List<Map<String, Object>> res = new ArrayList<>();
        if (fieldsJson == null || fieldsJson.isBlank()) {
            return res;
        }
        JsonNode root = null;
        try {
            root = MAPPER.readTree(fieldsJson);
        } catch (Exception e) {
            // 非 JSON 开头（如 "plan_output: {...}"）：定位首个 '{' 提取 JSON 体重试
            int brace = fieldsJson.indexOf('{');
            if (brace > 0) {
                try {
                    root = MAPPER.readTree(fieldsJson.substring(brace));
                } catch (Exception e2) {
                    log.warn("[FieldOntologyService] fields_json 解析失败: {}", e2.getMessage());
                }
            } else {
                log.warn("[FieldOntologyService] fields_json 解析失败: {}", e.getMessage());
            }
        }
        JsonNode arr = resolveFieldsArray(root);
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("field", n.path("field").asText(""));
                m.put("value", n.path("value").asText(""));
                m.put("source", n.path("source").asText(""));
                // V2.9：用户语义备注透传（如"11月1日生效=商品发布时间，非生效方式"），供 reason 消歧
                JsonNode remark = n.path("remark");
                if (!remark.isMissingNode() && !remark.isNull()) {
                    m.put("remark", remark.asText(""));
                }
                res.add(m);
            }
        }
        return res;
    }

    /** 下钻字段数组：纯数组 / plan_json.fields / 顶层 fields，均未命中返回 null */
    private JsonNode resolveFieldsArray(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        JsonNode arr = root.path("plan_json").path("fields");
        if (arr.isArray()) {
            return arr;
        }
        arr = root.path("fields");
        return arr.isArray() ? arr : null;
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * V2.9 排他性备注识别：备注中出现"非/不属于/不是"紧跟语义归属说明时，
     * 视为用户对该字段值的语义排除声明（值不属于本字段口径），跳过本体校验。
     */
    private boolean isExclusionRemark(String remark) {
        return remark.matches(".*(非|不属于|不是)[^，。;；]{0,20}(时间|字段|口径|方式|语义|范畴).*");
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Map<String, Object> ok() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("msg", "success");
        return body;
    }
}
