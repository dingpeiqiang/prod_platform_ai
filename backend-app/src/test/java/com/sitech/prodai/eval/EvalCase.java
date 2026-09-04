package com.sitech.prodai.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 黄金评测用例模型（JSONL 单行反序列化目标）。
 * <p>
 * 字段与 {@code eval/golden/*.jsonl} 对齐（snake_case 传输规范）：
 * <ul>
 *   <li>case_id：用例唯一标识</li>
 *   <li>scene：对话场景（rd / ops）</li>
 *   <li>input：用户话术</li>
 *   <li>expect：期望断言集合（intent_type / tools / params_contain / forbid_tools / multi_intent）</li>
 *   <li>note：用例说明（不参与断言）</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvalCase {

    @JsonProperty("case_id")
    private String caseId;
    private String scene;
    private String input;
    private Map<String, Object> expect;
    private String note;

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public Map<String, Object> getExpect() {
        return expect;
    }

    public void setExpect(Map<String, Object> expect) {
        this.expect = expect;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** 期望的意图类型（intent_type），可能缺省。 */
    public String expectedIntent() {
        return expect == null ? null : (String) expect.get("intent_type");
    }

    @SuppressWarnings("unchecked")
    public List<String> expectedTools() {
        if (expect == null || expect.get("tools") == null) {
            return List.of();
        }
        return (List<String>) expect.get("tools");
    }

    @SuppressWarnings("unchecked")
    public List<String> forbiddenTools() {
        if (expect == null || expect.get("forbid_tools") == null) {
            return List.of();
        }
        return (List<String>) expect.get("forbid_tools");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> expectedParams() {
        if (expect == null || expect.get("params_contain") == null) {
            return Map.of();
        }
        return (Map<String, Object>) expect.get("params_contain");
    }

    public boolean expectMultiIntent() {
        return expect != null && Boolean.TRUE.equals(expect.get("multi_intent"));
    }

    /** 期望意图为「非 CLARIFY 的具体业务意图」时返回 true（用于 CLARIFY 断言方向）。 */
    public boolean expectsClarify() {
        return "CLARIFY".equalsIgnoreCase(expectedIntent());
    }
}
