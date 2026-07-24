package com.sitech.prodai.service;

import com.sitech.prodai.domain.entity.SwrlRule;
import com.sitech.prodai.repository.SwrlRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 伪条件 DSL 引擎（字符串 AND/比较表达式），<b>不是</b> OWL/SWRL。
 * <p>产商品运营归因请使用 {@link OpsSwrlReasoner}（Openllet）+ {@link OpsRulesService}。
 * 本类仅保留营销策略遗留路径兼容。
 *
 * @deprecated 使用 {@link OpsSwrlReasoner} / {@link OpsRulesService}；本类计划在后续迭代移除。
 */
@Deprecated(since = "2026-07", forRemoval = false)
@Service
public class SwrlRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(SwrlRuleEngine.class);

    private final Optional<SwrlRuleRepository> ruleRepository;

    public SwrlRuleEngine(@Autowired(required = false) Optional<SwrlRuleRepository> ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public record SwrlRuleResult(
            String ruleId,
            String ruleName,
            boolean triggered,
            String reason,
            long elapsedMs
    ) {}

    public List<SwrlRuleResult> executeAll(Map<String, Object> facts) {
        List<SwrlRule> rules = loadRules();
        List<SwrlRuleResult> results = new ArrayList<>();

        for (SwrlRule rule : rules) {
            long start = System.currentTimeMillis();
            try {
                boolean matched = evaluateCondition(rule.getConditionExpr(), facts);
                String reason = matched
                        ? "条件满足: " + rule.getConditionExpr()
                        : "条件不满足: " + rule.getConditionExpr();
                long elapsed = System.currentTimeMillis() - start;
                results.add(new SwrlRuleResult(rule.getRuleId(), rule.getRuleName(), matched, reason, elapsed));
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                log.warn("[SwrlRuleEngine] 规则 {} 执行异常: {}", rule.getRuleId(), e.getMessage());
                results.add(new SwrlRuleResult(rule.getRuleId(), rule.getRuleName(), false, "执行异常: " + e.getMessage(), elapsed));
            }
        }

        return results;
    }

    private List<SwrlRule> loadRules() {
        List<SwrlRule> rules = new ArrayList<>();

        ruleRepository.ifPresent(repo -> {
            try {
                rules.addAll(repo.findByEnabledTrue());
            } catch (Exception e) {
                log.warn("[SwrlRuleEngine] 从数据库加载规则失败: {}", e.getMessage());
            }
        });

        if (rules.isEmpty()) {
            rules.addAll(builtinRules());
        }

        return rules;
    }

    private List<SwrlRule> builtinRules() {
        List<SwrlRule> rules = new ArrayList<>();

        SwrlRule r1 = new SwrlRule();
        r1.setRuleId("COND_001");
        r1.setRuleName("高消费推导升级资格");
        r1.setModule("marketing_rules");
        r1.setDescription("条件 DSL（非 OWL SWRL）：年消费 >= 50000 且会员等级为 Gold/Platinum");
        r1.setConditionExpr("annualSpend >= 50000 AND vipLevel IN (Gold, Platinum)");
        r1.setEnabled(true);
        rules.add(r1);

        SwrlRule r2 = new SwrlRule();
        r2.setRuleId("COND_002");
        r2.setRuleName("信用分推导额度调整");
        r2.setModule("marketing_rules");
        r2.setDescription("条件 DSL（非 OWL SWRL）：信用分 >= 700");
        r2.setConditionExpr("creditScore >= 700");
        r2.setEnabled(true);
        rules.add(r2);

        return rules;
    }

    boolean evaluateCondition(String conditionExpr, Map<String, Object> facts) {
        if (conditionExpr == null || conditionExpr.isBlank()) return true;

        String[] andParts = conditionExpr.split("\\s+AND\\s+");
        for (String part : andParts) {
            String trimmed = part.trim();

            int inIdx = trimmed.toUpperCase().indexOf(" IN (");
            if (inIdx > 0) {
                String field = trimmed.substring(0, inIdx).trim();
                String valuesPart = trimmed.substring(inIdx + 5).replaceAll("[)]$", "").trim();
                Object factVal = facts.get(field);
                if (factVal == null) return false;
                String factStr = String.valueOf(factVal);
                for (String v : valuesPart.split(",")) {
                    if (factStr.equalsIgnoreCase(v.trim())) return true;
                }
                return false;
            }

            String[] ops = {">=", "<=", "!=", ">", "<", "="};
            boolean matched = false;
            for (String op : ops) {
                int opIdx = trimmed.indexOf(op);
                if (opIdx > 0) {
                    String field = trimmed.substring(0, opIdx).trim();
                    String valueStr = trimmed.substring(opIdx + op.length()).trim();
                    Object factVal = facts.get(field);
                    if (factVal == null) return false;

                    double factNum = toDouble(factVal);
                    double targetNum = toDouble(valueStr);

                    matched = switch (op) {
                        case ">=" -> factNum >= targetNum;
                        case "<=" -> factNum <= targetNum;
                        case "!=" -> !String.valueOf(factVal).equalsIgnoreCase(valueStr);
                        case ">"  -> factNum > targetNum;
                        case "<"  -> factNum < targetNum;
                        case "="  -> String.valueOf(factVal).equalsIgnoreCase(valueStr);
                        default -> false;
                    };
                    break;
                }
            }

            if (!matched) return false;
        }

        return true;
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return 0; }
    }
}
