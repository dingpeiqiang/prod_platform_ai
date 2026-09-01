package com.sitech.prodai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sitech.prodai.domain.entity.Prompt;
import com.sitech.prodai.domain.entity.PromptTemplate;
import com.sitech.prodai.domain.entity.PromptVersion;
import com.sitech.prodai.mapper.PromptMapper;
import com.sitech.prodai.mapper.PromptTemplateMapper;
import com.sitech.prodai.mapper.PromptVersionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词管理服务 —— 对齐 Python {@code app/services/prompt_service.py::PromptService}。
 *
 * <p>提供提示词 CRUD、版本管理、变量替换预览、模板库、AI 生成辅助（mock 实现）。
 */
@Service
public class PromptService {

    private static final Logger log = LoggerFactory.getLogger(PromptService.class);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\s*(\\w+)\\s*\\}");

    private final PromptMapper promptMapper;
    private final PromptVersionMapper versionMapper;
    private final PromptTemplateMapper templateMapper;

    public PromptService(PromptMapper promptMapper,
                         PromptVersionMapper versionMapper,
                         PromptTemplateMapper templateMapper) {
        this.promptMapper = promptMapper;
        this.versionMapper = versionMapper;
        this.templateMapper = templateMapper;
    }

    // ==================== 提示词 CRUD ====================

    /** 对齐 Python list_prompts */
    public Map<String, Object> listPrompts(String category, Boolean isActive) {
        try {
            LambdaQueryWrapper<Prompt> wrapper = new LambdaQueryWrapper<Prompt>()
                    .eq(category != null && !category.isEmpty(), Prompt::getCategory, category)
                    .eq(isActive != null, Prompt::getIsActive, isActive)
                    .orderByDesc(Prompt::getCreatedAt);
            List<Prompt> prompts = promptMapper.selectList(wrapper);
            List<Map<String, Object>> data = new ArrayList<>();
            for (Prompt p : prompts) {
                data.add(promptToDict(p));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return body;
        } catch (Exception e) {
            log.error("[PromptService] list_prompts 失败", e);
            return fail(str(e));
        }
    }

    /** 对齐 Python get_prompt */
    public Map<String, Object> getPrompt(String code) {
        try {
            Optional<Prompt> opt = findByCode(code);
            if (opt.isEmpty()) {
                return fail("Prompt " + code + " not found");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", promptToDict(opt.get()));
            return body;
        } catch (Exception e) {
            log.error("[PromptService] get_prompt {} 失败", code, e);
            return fail(str(e));
        }
    }

    /** 对齐 Python create_prompt */
    @Transactional
    public Map<String, Object> createPrompt(Map<String, Object> promptData, String user) {
        try {
            String code = str(promptData.get("code"));
            if (code.isEmpty()) {
                return fail("Prompt code is required");
            }
            if (existsByCode(code)) {
                return fail("Prompt " + code + " already exists");
            }
            Prompt prompt = new Prompt();
            prompt.setCode(code);
            prompt.setName(firstNonBlank(str(promptData.get("name")), code));
            prompt.setDescription(str(promptData.get("description")));
            prompt.setCategory(firstNonBlank(str(promptData.get("category")), "general"));
            prompt.setContent(str(promptData.get("content")));
            prompt.setVariables(toList(promptData.get("variables")));
            prompt.setTools(toList(promptData.get("tools")));
            prompt.setIsTemplate(asBool(promptData.get("is_template"), false));
            prompt.setVersion(1);
            prompt.setIsActive(true);
            prompt.setCreatedBy(user);
            promptMapper.insert(prompt);

            createVersion(prompt, "Initial version", user);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", promptToDict(prompt));
            body.put("message", "Prompt created successfully");
            return body;
        } catch (Exception e) {
            log.error("[PromptService] create_prompt 失败", e);
            return fail(str(e));
        }
    }

    /** 对齐 Python update_prompt */
    @Transactional
    public Map<String, Object> updatePrompt(String code, Map<String, Object> promptData, String user) {
        try {
            Optional<Prompt> opt = findByCode(code);
            if (opt.isEmpty()) {
                return fail("Prompt " + code + " not found");
            }
            Prompt prompt = opt.get();
            String changeNote = str(promptData.getOrDefault("changeNote", "Update prompt"));

            boolean hasChanges = false;
            if (promptData.containsKey("name")) {
                String v = str(promptData.get("name"));
                if (!str(prompt.getName()).equals(v)) {
                    prompt.setName(v.isEmpty() ? prompt.getName() : v);
                    hasChanges = true;
                }
            }
            if (promptData.containsKey("description")) {
                String v = str(promptData.get("description"));
                if (!str(prompt.getDescription()).equals(v)) {
                    prompt.setDescription(v);
                    hasChanges = true;
                }
            }
            if (promptData.containsKey("category")) {
                String v = str(promptData.get("category"));
                if (!str(prompt.getCategory()).equals(v)) {
                    prompt.setCategory(v.isEmpty() ? "general" : v);
                    hasChanges = true;
                }
            }
            if (promptData.containsKey("content")) {
                String v = str(promptData.get("content"));
                if (!str(prompt.getContent()).equals(v)) {
                    prompt.setContent(v);
                    hasChanges = true;
                }
            }
            if (promptData.containsKey("variables")) {
                prompt.setVariables(toList(promptData.get("variables")));
                hasChanges = true;
            }
            if (promptData.containsKey("tools")) {
                prompt.setTools(toList(promptData.get("tools")));
                hasChanges = true;
            }
            if (promptData.containsKey("is_template")) {
                prompt.setIsTemplate(asBool(promptData.get("is_template"), false));
            }
            if (promptData.containsKey("is_active")) {
                prompt.setIsActive(asBool(promptData.get("is_active"), true));
            }

            if (hasChanges) {
                prompt.setVersion((prompt.getVersion() == null ? 1 : prompt.getVersion()) + 1);
                prompt.setUpdatedBy(user);
                promptMapper.updateById(prompt);
                createVersion(prompt, changeNote, user);
            } else {
                promptMapper.updateById(prompt);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", promptToDict(prompt));
            body.put("message", "Prompt updated successfully");
            return body;
        } catch (Exception e) {
            log.error("[PromptService] update_prompt {} 失败", code, e);
            return fail(str(e));
        }
    }

    /** 对齐 Python get_versions */
    public Map<String, Object> getVersions(String code) {
        try {
            Optional<Prompt> opt = findByCode(code);
            if (opt.isEmpty()) {
                return fail("Prompt " + code + " not found");
            }
            List<PromptVersion> versions = versionMapper.selectList(
                    new LambdaQueryWrapper<PromptVersion>()
                            .eq(PromptVersion::getPromptId, opt.get().getId())
                            .orderByDesc(PromptVersion::getVersion));
            List<Map<String, Object>> data = new ArrayList<>();
            for (PromptVersion v : versions) {
                data.add(versionToDict(v));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return body;
        } catch (Exception e) {
            log.error("[PromptService] get_versions 失败", e);
            return fail(str(e));
        }
    }

    /** 对齐 Python preview_prompt —— 变量替换预览 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> previewPrompt(String code, Map<String, Object> variables) {
        try {
            Optional<Prompt> opt = findByCode(code);
            if (opt.isEmpty()) {
                return fail("Prompt " + code + " not found");
            }
            Prompt prompt = opt.get();
            String content = prompt.getContent() == null ? "" : prompt.getContent();
            Map<String, Object> variablesDict = variables == null ? Map.of() : variables;
            for (Object varObj : prompt.getVariables() == null ? List.of() : prompt.getVariables()) {
                if (!(varObj instanceof Map<?, ?> varDef)) {
                    continue;
                }
                String varName = str(varDef.get("name"));
                if (varName.isEmpty()) {
                    continue;
                }
                Object varValue = variablesDict.containsKey(varName)
                        ? variablesDict.get(varName)
                        : ((Map<String, Object>) varDef).getOrDefault("default", "");
                String strValue = varValue == null ? "" : String.valueOf(varValue);
                content = content.replace("{{" + varName + "}}", strValue);
                content = content.replace("{{ " + varName + " }}", strValue);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("content", content);
            data.put("variables", prompt.getVariables());
            data.put("tools", prompt.getTools());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return body;
        } catch (Exception e) {
            log.error("[PromptService] preview_prompt 失败", e);
            return fail(str(e));
        }
    }

    /** 对齐 Python delete_prompt */
    @Transactional
    public Map<String, Object> deletePrompt(String code) {
        try {
            Optional<Prompt> opt = findByCode(code);
            if (opt.isEmpty()) {
                return fail("Prompt " + code + " not found");
            }
            promptMapper.deleteById(opt.get().getId());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("message", "Prompt deleted successfully");
            return body;
        } catch (Exception e) {
            log.error("[PromptService] delete_prompt {} 失败", code, e);
            return fail(str(e));
        }
    }

    // ==================== 模板库 ====================

    /** 对齐 Python list_templates */
    public Map<String, Object> listTemplates(String category) {
        try {
            LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<PromptTemplate>()
                    .eq(category != null && !category.isEmpty(), PromptTemplate::getCategory, category)
                    .eq(PromptTemplate::getIsActive, true);
            List<PromptTemplate> templates = templateMapper.selectList(wrapper);
            List<Map<String, Object>> data = new ArrayList<>();
            for (PromptTemplate t : templates) {
                data.add(templateToDict(t));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return body;
        } catch (Exception e) {
            log.error("[PromptService] list_templates 失败", e);
            return fail(str(e));
        }
    }

    // ==================== AI 辅助（mock 实现） ====================

    /** 对齐 Python generate_with_ai —— mock 实现，待接入 LlmService */
    public Map<String, Object> generateWithAi(Map<String, Object> requestData) {
        try {
            String requirement = str(requestData.get("requirement"));
            String category = firstNonBlank(str(requestData.get("category")), "general");
            List<Object> useTools = toList(requestData.get("useTools"));
            String generatedContent = generateMockPrompt(requirement, category, useTools);
            List<Map<String, Object>> variables = extractVariables(generatedContent);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("content", generatedContent);
            data.put("variables", variables);
            data.put("tools", useTools);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return body;
        } catch (Exception e) {
            log.error("[PromptService] generate_with_ai 失败", e);
            return fail(str(e));
        }
    }

    /** 对齐 Python optimize_prompt —— mock 实现 */
    public Map<String, Object> optimizePrompt(Map<String, Object> requestData) {
        try {
            String original = str(requestData.get("content"));
            String optimized = original.isEmpty() ? original : original + "\n\n---\n> 提示：此版本经过优化，建议先测试效果";
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("original", original);
            data.put("optimized", optimized);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return body;
        } catch (Exception e) {
            log.error("[PromptService] optimize_prompt 失败", e);
            return fail(str(e));
        }
    }

    /** 对齐 Python get_categories */
    public Map<String, Object> getCategories() {
        List<Map<String, String>> categories = List.of(
                cat("general", "通用"),
                cat("form", "表单生成"),
                cat("qa", "问答"),
                cat("tool", "工具调用"),
                cat("analysis", "分析"),
                cat("writing", "写作")
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", categories);
        return body;
    }

    // ==================== 内部方法 ====================

    private Optional<Prompt> findByCode(String code) {
        return Optional.ofNullable(promptMapper.selectOne(
                new LambdaQueryWrapper<Prompt>().eq(Prompt::getCode, code)));
    }

    private boolean existsByCode(String code) {
        return promptMapper.selectCount(new LambdaQueryWrapper<Prompt>().eq(Prompt::getCode, code)) > 0;
    }

    private void createVersion(Prompt prompt, String changeNote, String user) {
        PromptVersion v = new PromptVersion();
        v.setPromptId(prompt.getId());
        v.setVersion(prompt.getVersion());
        v.setContent(prompt.getContent());
        v.setVariables(prompt.getVariables());
        v.setTools(prompt.getTools());
        v.setChangeNote(changeNote);
        v.setCreatedBy(user);
        versionMapper.insert(v);
    }

    private String generateMockPrompt(String requirement, String category, List<Object> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 提示词\n\n");
        sb.append("## Role\n你是一个专业的AI助手，负责处理").append(category).append("相关的任务。\n\n");
        sb.append("## Task\n").append(requirement).append("\n\n");
        sb.append("## Format\n请按照以下格式输出结果：\n```json\n{\n  \"result\": \"...\"\n}\n```\n\n");
        sb.append("## Constraints\n- 确保输出质量\n- 遵循输入要求\n- 高效完成任务\n\n");
        if (tools != null && !tools.isEmpty()) {
            sb.append("## Tools\n你可以使用以下工具：\n");
            for (Object tool : tools) {
                if (tool instanceof Map<?, ?> tm) {
                    String name = firstNonBlank(str(tm.get("name")), str(tm.get("code")), "tool");
                    String desc = str(tm.get("description"));
                    sb.append("- ").append(name).append(": ").append(desc).append("\n");
                } else {
                    sb.append("- ").append(tool).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /** 对齐 Python _extract_variables —— 提取 {{varName}} 格式的变量 */
    private List<Map<String, Object>> extractVariables(String content) {
        List<Map<String, Object>> variables = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        if (content == null || content.isEmpty()) {
            return variables;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (seen.add(varName)) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("name", varName);
                v.put("description", varName + " 的值");
                v.put("default", "");
                variables.add(v);
            }
        }
        return variables;
    }

    private Map<String, Object> promptToDict(Prompt p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("code", p.getCode());
        m.put("name", p.getName());
        m.put("description", p.getDescription());
        m.put("category", p.getCategory());
        m.put("content", p.getContent());
        m.put("variables", p.getVariables() == null ? List.of() : p.getVariables());
        m.put("tools", p.getTools() == null ? List.of() : p.getTools());
        m.put("is_template", p.getIsTemplate());
        m.put("version", p.getVersion());
        m.put("is_active", p.getIsActive());
        m.put("created_by", p.getCreatedBy());
        m.put("updated_by", p.getUpdatedBy());
        m.put("created_at", p.getCreatedAt());
        m.put("updated_at", p.getUpdatedAt());
        return m;
    }

    private Map<String, Object> versionToDict(PromptVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("prompt_id", v.getPromptId());
        m.put("version", v.getVersion());
        m.put("content", v.getContent());
        m.put("variables", v.getVariables() == null ? List.of() : v.getVariables());
        m.put("tools", v.getTools() == null ? List.of() : v.getTools());
        m.put("change_note", v.getChangeNote());
        m.put("created_by", v.getCreatedBy());
        m.put("created_at", v.getCreatedAt());
        return m;
    }

    private Map<String, Object> templateToDict(PromptTemplate t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("description", t.getDescription());
        m.put("category", t.getCategory());
        m.put("content", t.getContent());
        m.put("variables", t.getVariables() == null ? List.of() : t.getVariables());
        m.put("tools", t.getTools() == null ? List.of() : t.getTools());
        m.put("tags", t.getTags() == null ? List.of() : t.getTags());
        m.put("is_builtin", t.getIsBuiltin());
        m.put("is_active", t.getIsActive());
        m.put("created_at", t.getCreatedAt());
        return m;
    }

    private Map<String, String> cat(String code, String name) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        return m;
    }

    private List<Object> toList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private boolean asBool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    private Map<String, Object> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }
}
