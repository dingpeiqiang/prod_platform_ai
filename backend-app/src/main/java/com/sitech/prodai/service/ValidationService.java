package com.sitech.prodai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 表单校验引擎 —— 对齐 Python {@code app/services/validation_service.py::ValidationEngine}。
 *
 * <p>支持以下规则类型：
 * <ul>
 *   <li>minLength / maxLength：长度范围</li>
 *   <li>min / max / minimum / maximum：数值范围</li>
 *   <li>pattern：正则匹配</li>
 *   <li>email / phone / idCard / url：内置格式校验</li>
 *   <li>enum：枚举值</li>
 *   <li>dateMin / dateMax：日期范围（yyyy-MM-dd）</li>
 *   <li>custom：自定义脚本（Java 端只支持 simple 类型，lambda 类型返回 true）</li>
 * </ul>
 *
 * <p>注意：Python 版本支持 eval / lambda 自定义脚本，Java 出于安全考虑
 * 不支持任意脚本执行，仅对 simple 类型做有限的支持（基于 condition 字符串的简单比较）。
 */
@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$");
    private static final Pattern URL_PATTERN =
            Pattern.compile("^https?://[^\\s/$.?#].[^\\s]*$");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 校验单个字段 —— 对齐 Python validate_field。
     *
     * @param fieldValue 字段值
     * @param rules      规则列表，每项 {rule_type, rule_value, message}
     * @return 校验结果 {valid, errors}
     */
    public ValidationResult validateField(Object fieldValue, List<Map<String, Object>> rules) {
        List<String> errors = new ArrayList<>();
        if (rules == null) {
            return new ValidationResult(true, errors);
        }
        for (Map<String, Object> rule : rules) {
            String ruleType = str(rule.get("rule_type"));
            if (ruleType == null || ruleType.isEmpty()) {
                continue;
            }
            Object ruleValue = rule.get("rule_value");
            String message = str(rule.get("message"));
            if (!validateSingleRule(fieldValue, ruleType, ruleValue)) {
                errors.add(message);
            }
        }
        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * 校验整张表单 —— 对齐 Python validate_form。
     *
     * @param formData 表单数据 {field_code: value}
     * @param fields   字段定义列表，每项含 {fieldCode, fieldName, required, rules}
     * @return 校验结果 {valid, errors}
     */
    public ValidationResult validateForm(Map<String, Object> formData, List<Map<String, Object>> fields) {
        List<String> allErrors = new ArrayList<>();
        if (fields == null) {
            return new ValidationResult(true, allErrors);
        }
        for (Map<String, Object> field : fields) {
            String fieldCode = str(field.get("fieldCode"));
            String fieldName = firstNonBlank(str(field.get("fieldName")), fieldCode);
            boolean required = Boolean.TRUE.equals(field.get("required"));
            Object fieldValue = formData == null ? null : formData.get(fieldCode);

            if (required && isEmptyValue(fieldValue)) {
                allErrors.add(fieldName + " 不能为空");
                continue;
            }
            if (!isEmptyValue(fieldValue)) {
                Object rulesObj = field.get("rules");
                if (rulesObj instanceof List<?> rawRules) {
                    List<Map<String, Object>> rules = new ArrayList<>();
                    for (Object r : rawRules) {
                        if (r instanceof Map<?, ?> rm) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> cast = (Map<String, Object>) rm;
                            rules.add(cast);
                        }
                    }
                    ValidationResult r = validateField(fieldValue, rules);
                    allErrors.addAll(r.errors);
                }
            }
        }
        return new ValidationResult(allErrors.isEmpty(), allErrors);
    }

    // ==================== 单条规则校验 ====================

    @SuppressWarnings("unchecked")
    private boolean validateSingleRule(Object fieldValue, String ruleType, Object ruleValue) {
        switch (ruleType) {
            case "minLength":
                return validateMinLength(fieldValue, toInt(ruleValue, 0));
            case "maxLength":
                return validateMaxLength(fieldValue, toInt(ruleValue, Integer.MAX_VALUE));
            case "min", "minimum":
                return validateMin(fieldValue, toDouble(ruleValue, Double.NEGATIVE_INFINITY));
            case "max", "maximum":
                return validateMax(fieldValue, toDouble(ruleValue, Double.POSITIVE_INFINITY));
            case "pattern":
                return validatePattern(fieldValue, str(ruleValue));
            case "email":
                return validateEmail(fieldValue);
            case "phone":
                return validatePhone(fieldValue);
            case "idCard":
                return validateIdCard(fieldValue);
            case "url":
                return validateUrl(fieldValue);
            case "enum":
                return validateEnum(fieldValue, ruleValue instanceof List<?> list ? list : List.of());
            case "dateMin":
                return validateDateMin(fieldValue, str(ruleValue));
            case "dateMax":
                return validateDateMax(fieldValue, str(ruleValue));
            case "custom":
                if (ruleValue instanceof Map<?, ?> scriptConfig) {
                    return validateCustomScript(fieldValue, (Map<String, Object>) scriptConfig);
                }
                return true;
            default:
                return true;
        }
    }

    private boolean validateMinLength(Object value, int minLen) {
        if (value == null) {
            return true;
        }
        return String.valueOf(value).length() >= minLen;
    }

    private boolean validateMaxLength(Object value, int maxLen) {
        if (value == null) {
            return true;
        }
        return String.valueOf(value).length() <= maxLen;
    }

    private boolean validateMin(Object value, double minVal) {
        if (isEmptyValue(value)) {
            return true;
        }
        try {
            return Double.parseDouble(String.valueOf(value)) >= minVal;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean validateMax(Object value, double maxVal) {
        if (isEmptyValue(value)) {
            return true;
        }
        try {
            return Double.parseDouble(String.valueOf(value)) <= maxVal;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean validatePattern(Object value, String pattern) {
        if (isEmptyValue(value) || pattern == null || pattern.isEmpty()) {
            return true;
        }
        try {
            return Pattern.matches(pattern, String.valueOf(value));
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private boolean validateEmail(Object value) {
        if (isEmptyValue(value)) {
            return true;
        }
        return EMAIL_PATTERN.matcher(String.valueOf(value)).matches();
    }

    private boolean validatePhone(Object value) {
        if (isEmptyValue(value)) {
            return true;
        }
        return PHONE_PATTERN.matcher(String.valueOf(value)).matches();
    }

    private boolean validateIdCard(Object value) {
        if (isEmptyValue(value)) {
            return true;
        }
        return ID_CARD_PATTERN.matcher(String.valueOf(value)).matches();
    }

    private boolean validateUrl(Object value) {
        if (isEmptyValue(value)) {
            return true;
        }
        return URL_PATTERN.matcher(String.valueOf(value)).matches();
    }

    private boolean validateEnum(Object value, List<?> enumValues) {
        if (isEmptyValue(value)) {
            return true;
        }
        String strValue = String.valueOf(value);
        for (Object v : enumValues) {
            if (v != null && strValue.equals(String.valueOf(v))) {
                return true;
            }
        }
        return false;
    }

    private boolean validateDateMin(Object value, String minDate) {
        if (isEmptyValue(value) || minDate == null || minDate.isEmpty()) {
            return true;
        }
        try {
            LocalDate dateVal = LocalDate.parse(String.valueOf(value), DATE_FMT);
            LocalDate min = LocalDate.parse(minDate, DATE_FMT);
            return !dateVal.isBefore(min);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean validateDateMax(Object value, String maxDate) {
        if (isEmptyValue(value) || maxDate == null || maxDate.isEmpty()) {
            return true;
        }
        try {
            LocalDate dateVal = LocalDate.parse(String.valueOf(value), DATE_FMT);
            LocalDate max = LocalDate.parse(maxDate, DATE_FMT);
            return !dateVal.isAfter(max);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * 自定义脚本校验 —— 对齐 Python _validate_custom_script。
     *
     * <p>Java 端出于安全考虑不支持任意脚本执行，仅对 simple 类型做有限支持：
     * <ul>
     *   <li>支持 condition 形如 "value == 'foo'" / "value != 'bar'" / "value > 10" 等简单比较</li>
     *   <li>lambda 类型直接返回 true（不执行）</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private boolean validateCustomScript(Object value, Map<String, Object> scriptConfig) {
        if (isEmptyValue(value)) {
            return true;
        }
        String scriptType = str(scriptConfig.getOrDefault("type", "simple"), "simple");
        if ("simple".equals(scriptType)) {
            String condition = str(scriptConfig.get("condition"));
            return evaluateSimpleCondition(value, condition);
        }
        // lambda 类型在 Java 端不支持，直接返回 true
        return true;
    }

    /**
     * 简单条件评估 —— 仅支持 value == X / value != X / value > X / value < X / value >= X / value <= X
     * value 字符串本身不能包含引号以防止注入。
     */
    private boolean evaluateSimpleCondition(Object value, String condition) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        try {
            String cond = condition.trim();
            // 仅支持以 "value" 开头的简单比较
            if (!cond.startsWith("value")) {
                log.warn("[ValidationService] 不支持的 condition: {}", cond);
                return false;
            }
            String rest = cond.substring(5).trim();
            String op = null;
            String operand = null;
            String[] ops = {"==", "!=", ">=", "<=", ">", "<"};
            for (String candidate : ops) {
                if (rest.startsWith(candidate)) {
                    op = candidate;
                    operand = rest.substring(candidate.length()).trim();
                    break;
                }
            }
            if (op == null || operand == null) {
                return false;
            }
            // 去掉引号
            if ((operand.startsWith("'") && operand.endsWith("'"))
                    || (operand.startsWith("\"") && operand.endsWith("\""))) {
                operand = operand.substring(1, operand.length() - 1);
            }
            String strValue = String.valueOf(value);
            switch (op) {
                case "==":
                    return strValue.equals(operand);
                case "!=":
                    return !strValue.equals(operand);
                case ">":
                    return Double.parseDouble(strValue) > Double.parseDouble(operand);
                case "<":
                    return Double.parseDouble(strValue) < Double.parseDouble(operand);
                case ">=":
                    return Double.parseDouble(strValue) >= Double.parseDouble(operand);
                case "<=":
                    return Double.parseDouble(strValue) <= Double.parseDouble(operand);
                default:
                    return false;
            }
        } catch (Exception e) {
            log.warn("[ValidationService] 简单条件评估失败 condition={} err={}", condition, e.getMessage());
            return false;
        }
    }

    // ==================== 工具方法 ====================

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String str(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = String.valueOf(value);
        return s.isEmpty() ? defaultValue : s;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /**
     * 校验结果 —— 对齐 Python ValidationResult。
     */
    public static class ValidationResult {
        public final boolean valid;
        public final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors == null ? List.of() : errors;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("valid", valid);
            body.put("errors", errors);
            return body;
        }
    }
}
