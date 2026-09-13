package com.sitech.prodai.service.appstore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 产销品加载 AI 应用 · 字段本体推理服务（V2.1，工具14 field_ontology_reason 后端）。
 * <p>
 * 设计模式：规则引擎（策略+责任链组合）——四类18字段的本体定义（枚举/格式/默认值/兜底口径）
 * 以代码常量建模为字段本体注册表，推理按「枚举校验→格式校验→默认值推理→兜底口径」逐字段执行：
 * <ol>
 *   <li>validate：LLM 补全结果逐字段做本体合法性推理，非法值返回 reason（期望规则）供 LLM 重填；</li>
 *   <li>complete：缺失字段按本体默认值推理补全（default_value 非空的字段），兜底口径字段（套餐固定费/三类资源）不补全返回待补充。</li>
 * </ol>
 * 本体定义内聚于本服务（单一事实源），知识侧不再维护 K6 文档（V2.1 整体替换为推理引擎方案）。
 */
@Service
public class FieldOntologyService {

    private static final Logger log = LoggerFactory.getLogger(FieldOntologyService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 字段本体定义：枚举/格式/默认值/兜底口径（与《产销品加载AI应用开发方案》四类18字段一一对应） */
    private static final Map<String, FieldSpec> SPECS = new LinkedHashMap<>();

    /** 渠道类型同义词映射表：[变体关键词, 本体枚举值]，供多选归一先行命中 */
    private static final String[][] CHANNEL_SYNONYMS = {
            {"营业厅", "实体渠道"}, {"门店", "实体渠道"}, {"实体", "实体渠道"},
            {"APP", "电子渠道"}, {"app", "电子渠道"}, {"网厅", "电子渠道"},
            {"线上", "电子渠道"}, {"电子", "电子渠道"},
            {"直销", "直销渠道"}, {"客户经理", "直销渠道"}, {"政企", "直销渠道"},
    };

    static {
        // A. 基础信息
        spec("产品名称", "A.基础信息", null, Pattern.compile("^5G-A(融合|单品)套餐\\d+元$|^\\d+元权益随心选\\S+版$"),
                "须符合 K1 命名模板：5G-A+[融合/单品]套餐+[档位]元 或 [档位]元权益随心选[版本]版",
                null, false);
        spec("产品属性", "A.基础信息", Arrays.asList("基础", "可选", "增值"), null,
                "枚举：基础/可选/增值", "基础", false);
        spec("产品编码", "A.基础信息", null, Pattern.compile("^\\d{9}$|^由智能配置生成$"),
                "9位数字或\"由智能配置生成\"（不做AI补全，由智能配置环节落地后生成）",
                "由智能配置生成", false);
        spec("生效日期", "A.基础信息", null, Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$|^立即生效$|^次月1日$"),
                "yyyy-MM-dd 或 立即生效/次月1日", null, false);
        spec("退订规则", "A.基础信息", null, null,
                "文本；默认口径：允许退订，次月生效，当月费用不退还",
                "允许退订，次月生效，当月费用不退还", false);
        // B. 资源配置（兜底口径：三类资源全部未提取到时待补充，不默认补全）
        spec("流量资源", "B.资源配置", null, Pattern.compile("^\\d+(\\.\\d+)?GB$|^无$|^待补充$"),
                "数值+单位（GB），如 120GB", null, true);
        spec("语音资源", "B.资源配置", null, Pattern.compile("^\\d+分钟$|^无$|^待补充$"),
                "数值+单位（分钟），如 1000分钟", null, true);
        spec("短信资源", "B.资源配置", null, Pattern.compile("^\\d+条$|^无$|^待补充$"),
                "数值+单位（条），如 10条；无短信填\"无\"", null, true);
        // C. 营销资源（套餐固定费为兜底口径）
        spec("套餐固定费", "C.营销资源", null, Pattern.compile("^\\d+(\\.\\d+)?元/月$|^待补充$"),
                "数值+单位（元/月），如 199元/月", null, true);
        spec("收费方式", "C.营销资源", Arrays.asList("按月", "按量", "一次性"), null,
                "枚举：按月/按量/一次性（月付/包月归一为按月）", "按月", false);
        spec("优惠条件", "C.营销资源", null, null,
                "文本；无优惠填\"无\"", "无", false);
        spec("优惠期", "C.营销资源", null, null,
                "文本；无优惠填\"无\"", "无", false);
        // D. 销售规则
        spec("渠道类型", "D.销售规则", Arrays.asList("实体渠道", "电子渠道", "直销渠道"), null,
                "枚举（可多选，顿号分隔）：实体渠道/电子渠道/直销渠道；无参照默认三者全选",
                "实体渠道、电子渠道、直销渠道", false);
        spec("适用地区", "D.销售规则", Arrays.asList("全国（不含港澳台）", "指定省份"), null,
                "枚举：全国（不含港澳台）/指定省份；默认全国", "全国（不含港澳台）", false);
        spec("订购限制", "D.销售规则", null, null,
                "文本；无限制填\"无\"", "无", false);
        spec("副卡规则", "D.销售规则", null, null,
                "文本；无参照填\"不允许办理副卡\"", "不允许办理副卡", false);
        spec("计费周期", "D.销售规则", Arrays.asList("自然月"), null,
                "枚举：自然月", "自然月", false);
        spec("销售品状态", "D.销售规则", Arrays.asList("在售", "待上线"), null,
                "枚举：在售/待上线；新需求一律填\"待上线\"", "待上线", false);
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
            this.multi = enums != null && "渠道类型".equals(field);
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
     * 兜底口径字段（fallback=true）不补全、标记 fallback=true 交上游判待补充。
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
            if (spec != null && value.isEmpty()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("field", field);
                if (spec.defaultValue != null && !spec.fallback) {
                    value = spec.defaultValue;
                    c.put("value", value);
                    c.put("defaulted", "1");
                    c.put("reason", spec.rule);
                    completed.add(c);
                } else if (spec.fallback) {
                    c.put("value", "待补充");
                    c.put("defaulted", "0");
                    c.put("reason", "兜底口径字段，不默认补全，待上游判待补充：" + spec.rule);
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
     *   <li>value 为空 → 默认值推理补全（default_value 非空且非兜底），兜底口径字段置"待补充"；</li>
     *   <li>value 非空 → 本体校验；非法值直接按本体规则**修正回写**（枚举归一：月付/包月→按月等；
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
            FieldSpec spec = SPECS.get(field);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", field);
            item.put("value", value);
            item.put("source", source);
            if (spec == null) {
                out.add(item); // 未注册字段透传
                continue;
            }
            // ① 缺失 → 默认值推理补全 / 兜底置待补充
            if (value.isEmpty()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("field", field);
                if (spec.defaultValue != null && !spec.fallback) {
                    value = spec.defaultValue;
                    source = "AI补全";
                    c.put("value", value);
                    c.put("action", "defaulted");
                    c.put("reason", spec.rule);
                } else if (spec.fallback) {
                    value = "待补充";
                    c.put("value", value);
                    c.put("action", "fallback");
                    c.put("reason", "兜底口径字段，不默认补全：" + spec.rule);
                } else {
                    c.put("value", "");
                    c.put("action", "none");
                    c.put("reason", "本体无默认值，维持留空");
                }
                fixed.add(c);
            } else if (!"待补充".equals(value) && !"由智能配置生成".equals(value)) {
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
        // 套餐固定费归一："199元"、"199"→"199元/月"（缺单位/缺周期口径补全）
        if ("套餐固定费".equals(spec.field)) {
            java.util.regex.Matcher m = Pattern.compile("^(\\d+(\\.\\d+)?)元?$").matcher(v);
            if (m.matches()) {
                return m.group(1) + "元/月";
            }
            return null;
        }
        if (spec.enums == null) {
            return null;
        }
        // 收费方式归一：月付/包月→按月；按次→一次性
        if ("收费方式".equals(spec.field)) {
            if (v.contains("月付") || v.contains("包月") || v.contains("按月")) {
                return "按月";
            }
            if (v.contains("一次性") || v.contains("按次")) {
                return "一次性";
            }
            if (v.contains("按量")) {
                return "按量";
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
        // 多选字段（渠道类型）：逐项归一（先查同义词映射表，再枚举包含匹配）
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
