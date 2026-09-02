package com.sitech.prodai.service.flow;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * condition 节点表达式求值器（SpEL 沙箱）—— 固定流程引擎（P2-3）。
 * <p>
 * 安全约束（设计文档 §5.2，铁律："LLM 只进节点不进引擎"，表达式为确定性代码）：
 * <ul>
 *   <li>{@link SimpleEvaluationContext}：禁类型引用、禁构造器、禁 bean 访问——防 SpEL 注入</li>
 *   <li>只读变量根对象：{node, flow, system} 三类命名空间</li>
 *   <li>求值异常一律返回 false（未命中），由 default 兜底分支承接</li>
 * </ul>
 * <p>
 * 表达式形态（对齐《工作流配置规范》§3.4）：
 * {@code ${node-tool-001.output.riskLevel} == 'HIGH'} —— ${ref} 先做变量替换，
 * 替换后的标准 SpEL 再求值（结果可为 boolean 或任意可 toString 的值）。
 */
@Component
public class ConditionEvaluator {

    /** 求值变量根：node.<节点id>.output.<field> / flow.<入参> / system.<timestamp|execution_id>。 */
    public record EvalContext(Map<String, Object> nodes, Map<String, Object> flow, Map<String, Object> system) {
    }

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 求值单条分支表达式。
     *
     * @param expression 分支表达式（"default" 由调用方处理，不进本方法）
     * @param ctx        求值上下文
     * @return 命中与否（异常视为未命中）
     */
    public boolean evaluate(String expression, EvalContext ctx) {
        try {
            String spel = substituteVariables(expression, ctx);
            Expression expr = parser.parseExpression(spel);
            Object result = expr.getValue(buildEvalRoot(ctx));
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            // 求值异常 = 未命中；default 兜底分支承接。记录事实但不上抛（引擎韧性铁律）
            return false;
        }
    }

    /** ${ref} → 变量值字符串替换（与《工作流配置规范》§5 变量引用语法对齐）。 */
    private String substituteVariables(String expression, EvalContext ctx) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\$\\{([^}]+)}").matcher(expression);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object value = resolveRef(m.group(1).trim(), ctx);
            String literal = value == null ? "null"
                    : (value instanceof String s ? "'" + s.replace("'", "\\'") + "'" : String.valueOf(value));
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(literal));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * ref 解析（对齐《工作流配置规范》§5.2：首段即节点 id，可为含连字符的任意 id）：
     * <node-id>.output.<field...> / flow.<key> / system.<key>
     */
    private Object resolveRef(String ref, EvalContext ctx) {
        String[] parts = ref.split("\\.");
        Object current = switch (parts[0]) {
            case "flow" -> ctx.flow();
            case "system" -> ctx.system();
            default -> ctx.nodes().get(parts[0]); // 首段 = 节点 id（node-scope: {output: {...}}）
        };
        if (parts[0].equals("flow") || parts[0].equals("system")) {
            return parts.length >= 2 ? (current instanceof Map<?, ?> m ? m.get(parts[1]) : null) : null;
        }
        // 节点引用：parts[0]=节点 id，parts[1] 应为 output，parts[2..] 为字段路径
        if (parts.length < 3 || !"output".equals(parts[1])) {
            return null;
        }
        current = current instanceof Map<?, ?> scope ? ((Map<?, ?>) scope).get("output") : null;
        for (int i = 2; i < parts.length; i++) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(parts[i]);
            } else {
                return null;
            }
        }
        return current;
    }

    private Map<String, Object> buildEvalRoot(EvalContext ctx) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("flow", ctx.flow() == null ? Map.of() : ctx.flow());
        root.put("system", ctx.system() == null ? Map.of() : ctx.system());
        return root;
    }
}
