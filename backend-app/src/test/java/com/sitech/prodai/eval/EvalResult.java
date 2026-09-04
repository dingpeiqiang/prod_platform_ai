package com.sitech.prodai.eval;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单条用例的评测结果：断言得分明细 + 失败原因。
 */
public class EvalResult {

    private final String caseId;
    private final String input;

    /** 各断言维度结果：intent / tools / params / forbid / multi，值为 true/false/skip。 */
    private final Map<String, String> checks = new LinkedHashMap<>();

    /** 失败原因明细（维度 → 说明）。 */
    private final Map<String, String> failures = new LinkedHashMap<>();

    /** 实际理解输出摘要（意图 / 工具 / 参数），供报告展示。 */
    private String actualIntent;
    private String actualTools;

    public EvalResult(String caseId, String input) {
        this.caseId = caseId;
        this.input = input;
    }

    public void mark(String dimension, boolean passed, String failReason) {
        checks.put(dimension, passed ? "true" : "false");
        if (!passed && failReason != null) {
            failures.put(dimension, failReason);
        }
    }

    public void skip(String dimension, String reason) {
        checks.put(dimension, "skip");
        if (reason != null) {
            failures.put(dimension, reason);
        }
    }

    public boolean passed() {
        return checks.values().stream().noneMatch("false"::equals);
    }

    public String getCaseId() {
        return caseId;
    }

    public String getInput() {
        return input;
    }

    public Map<String, String> getChecks() {
        return checks;
    }

    public Map<String, String> getFailures() {
        return failures;
    }

    public String getActualIntent() {
        return actualIntent;
    }

    public void setActualIntent(String actualIntent) {
        this.actualIntent = actualIntent;
    }

    public String getActualTools() {
        return actualTools;
    }

    public void setActualTools(String actualTools) {
        this.actualTools = actualTools;
    }

    /** 单行报告文本（JSONL 风格，便于 CI diff 查看）。 */
    public String toReportLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(passed() ? "PASS " : "FAIL ").append(caseId)
                .append(" intent=").append(actualIntent)
                .append(" tools=").append(actualTools);
        if (!failures.isEmpty()) {
            sb.append(" failures=").append(failures);
        }
        return sb.toString();
    }
}
