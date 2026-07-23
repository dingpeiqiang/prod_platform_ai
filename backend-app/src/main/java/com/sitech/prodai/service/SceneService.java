package com.sitech.prodai.service;

import com.sitech.prodai.config.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景服务 —— 纯文件数据源，对齐 Python app/services/scene_service.py::SceneService。
 *
 * <p>从 {@link ConfigLoader} 读取场景文件数据源（scene_mapping.json），
 * 提供场景树构建、场景识别、统计、自动提示词生成等能力。
 *
 * <p>场景数据完全由文件管理，不支持数据库 CRUD。
 */
@Service
public class SceneService {

    private static final Logger log = LoggerFactory.getLogger(SceneService.class);

    private final ConfigLoader configLoader;

    public SceneService(ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    // ==================== 查询方法 ====================

    public Map<String, Object> listScenesTree(Boolean isActive) {
        try {
            List<Map<String, Object>> all = filterScenesByActive(configLoader.getAllScenes(), isActive);
            List<Map<String, Object>> tree = buildTree(all);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("total", all.size());
            body.put("data", tree);
            return body;
        } catch (Exception e) {
            log.error("[SceneService] listScenesTree 失败", e);
            return fail(str(e));
        }
    }

    public Map<String, Object> listScenes(Boolean isActive) {
        try {
            List<Map<String, Object>> scenes = filterScenesByActive(configLoader.getAllScenes(), isActive);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("total", scenes.size());
            body.put("data", scenes);
            return body;
        } catch (Exception e) {
            log.error("[SceneService] listScenes 失败", e);
            return fail(str(e));
        }
    }

    public Map<String, Object> getScene(String sceneCode) {
        try {
            Map<String, Object> scene = configLoader.getSceneByCode(sceneCode);
            if (scene == null) {
                return fail("Scene " + sceneCode + " not found");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", scene);
            return body;
        } catch (Exception e) {
            log.error("[SceneService] getScene {} 失败", sceneCode, e);
            return fail(str(e));
        }
    }

    public Map<String, Object> getScenePrompt(String sceneCode) {
        try {
            Map<String, Object> scene = configLoader.getSceneByCode(sceneCode);
            if (scene == null) {
                return fail("场景 " + sceneCode + " 不存在");
            }
            if (!Boolean.TRUE.equals(scene.getOrDefault("isActive", true))) {
                return fail("场景 " + sceneCode + " 已禁用");
            }

            Object promptCodeObj = scene.get("promptCode");
            String promptCode = promptCodeObj == null ? null : String.valueOf(promptCodeObj);

            String userPromptContent = null;
            if (promptCode != null && !promptCode.isEmpty()) {
                userPromptContent = configLoader.getPrompt(stripExt(promptCode));
                if (userPromptContent != null) {
                    log.info("[SceneService] 获取用户提示词 sceneCode={} len={}", sceneCode, userPromptContent.length());
                }
            }

            String autoPrompt = generateAutoPrompt(scene);
            String promptContent = "";
            if (autoPrompt != null && !autoPrompt.isEmpty()) {
                promptContent = autoPrompt;
            }
            if (userPromptContent != null && !userPromptContent.isEmpty()) {
                promptContent = promptContent.isEmpty()
                        ? userPromptContent
                        : promptContent + "\n\n" + userPromptContent;
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("scene", scene);
            body.put("prompt_code", promptCode);
            body.put("prompt_content", promptContent);
            body.put("message", "获取成功");
            return body;
        } catch (Exception e) {
            log.error("[SceneService] getScenePrompt {} 失败", sceneCode, e);
            return fail(str(e));
        }
    }

    public Map<String, Object> testSceneRecognition(String userInput) {
        try {
            List<Map<String, Object>> all = new ArrayList<>();
            for (Map<String, Object> s : configLoader.getAllScenes()) {
                if (Boolean.TRUE.equals(s.getOrDefault("isActive", true))) {
                    all.add(s);
                }
            }

            String userInputLower = userInput == null ? "" : userInput.toLowerCase();
            List<Map<String, Object>> matchedScenes = matchScenes(all, userInputLower);
            Map<String, Object> bestMatch = matchedScenes.isEmpty() ? null : matchedScenes.get(0);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("bestMatch", bestMatch);
            body.put("allMatches", matchedScenes);
            body.put("totalScanned", all.size());
            return body;
        } catch (Exception e) {
            log.error("[SceneService] testSceneRecognition 失败", e);
            return fail(str(e));
        }
    }

    public Map<String, Object> getSceneStats() {
        try {
            List<Map<String, Object>> all = configLoader.getAllScenes();
            int total = all.size();
            int active = 0;
            for (Map<String, Object> s : all) {
                if (Boolean.TRUE.equals(s.getOrDefault("isActive", true))) {
                    active++;
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", total);
            data.put("active", active);
            data.put("inactive", total - active);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return body;
        } catch (Exception e) {
            log.error("[SceneService] getSceneStats 失败", e);
            return fail(str(e));
        }
    }

    // ==================== 内部方法 ====================

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> scenes) {
        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (Map<String, Object> scene : scenes) {
            String sceneCode = str(scene.get("sceneCode"));
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", sceneCode);
            node.put("label", firstNonBlank(str(scene.get("sceneName")), sceneCode));
            node.put("type", str(scene.getOrDefault("type", "scene"), "scene"));
            node.put("sceneCode", sceneCode);
            node.put("sceneName", scene.get("sceneName"));
            node.put("priority", scene.getOrDefault("priority", 1));
            node.put("isActive", scene.getOrDefault("isActive", true));
            node.put("keywords", scene.getOrDefault("keywords", List.of()));
            node.put("description", scene.get("description"));
            node.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put(sceneCode, node);
        }

        List<Map<String, Object>> tree = new ArrayList<>();
        for (Map<String, Object> scene : scenes) {
            String sceneCode = str(scene.get("sceneCode"));
            Map<String, Object> node = nodeMap.get(sceneCode);
            if (node == null) {
                continue;
            }
            Object parentIdObj = scene.get("parentId");
            String type = str(scene.get("type"));
            if (parentIdObj == null && "center".equals(type)) {
                tree.add(node);
            } else if (parentIdObj instanceof String parentId && nodeMap.containsKey(parentId)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get(parentId).get("children");
                children.add(node);
            } else {
                tree.add(node);
            }
        }
        return tree;
    }

    @SuppressWarnings("unchecked")
    private String generateAutoPrompt(Map<String, Object> sceneData) {
        Object configObj = sceneData.get("config");
        if (!(configObj instanceof Map<?, ?> configRaw)) {
            return "";
        }
        Object workflowsObj = configRaw.get("workflows");
        if (!(workflowsObj instanceof List<?> workflows) || workflows.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        Object sceneNameObj = sceneData.get("sceneName");
        String sceneName = sceneNameObj == null ? "" : String.valueOf(sceneNameObj);
        if (!sceneName.isEmpty()) {
            parts.add("你是专业的" + sceneName + "场景助手。");
            parts.add("场景名称：" + sceneName);
        }
        parts.add("\n## 可用工作流");
        parts.add("当前场景可用以下工作流：");
        Map<String, Object> defaultWorkflow = null;
        for (Object wfObj : workflows) {
            if (!(wfObj instanceof Map<?, ?> wf)) {
                continue;
            }
            String wfCode = str(wf.get("code"));
            String wfName = firstNonBlank(str(wf.get("name")), wfCode);
            String wfDesc = str(wf.get("description"));
            boolean isDefault = Boolean.TRUE.equals(wf.get("isDefault"));
            parts.add("- " + wfCode + (isDefault ? " (默认)" : "") + ": " + wfName);
            if (!wfDesc.isEmpty()) {
                parts.add("  - " + wfDesc);
            }
            if (defaultWorkflow == null || isDefault) {
                defaultWorkflow = (Map<String, Object>) wf;
            }
        }
        if (defaultWorkflow != null) {
            parts.add(buildWorkflowPrompt(str(defaultWorkflow.get("code"))));
        }
        return String.join("\n", parts);
    }

    private String buildWorkflowPrompt(String workflowCode) {
        return """
                ## 工作流调用指令

                识别到当前场景需要执行工作流，请直接输出调用工作流的 JSON：

                ```json
                {
                  "action": "call_tool",
                  "tool_name": "execute_workflow",
                  "tool_args": {
                    "workflow_code": "%s",
                    "inputs": {
                      "user_input": "<<用户原始输入>>"
                    }
                  },
                  "message": "正在执行工作流..."
                }
                ```

                **替换说明：**
                - 将 `<<用户原始输入>>` 替换为实际的用户输入内容

                **注意：**
                - 不要添加任何解释性文字
                - 直接输出 JSON 格式
                - 确保 JSON 格式正确""".formatted(workflowCode);
    }

    private String matchKeyword(Map<String, Object> scene, String userInputLower) {
        Object keywordsObj = scene.get("keywords");
        if (!(keywordsObj instanceof List<?> keywords)) {
            return null;
        }
        for (Object kw : keywords) {
            if (kw == null) {
                continue;
            }
            String keyword = String.valueOf(kw).toLowerCase();
            if (userInputLower.contains(keyword)) {
                return String.valueOf(kw);
            }
        }
        return null;
    }

    private List<Map<String, Object>> matchScenes(List<Map<String, Object>> targetScenes, String userInputLower) {
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> scene : targetScenes) {
            String kw = matchKeyword(scene, userInputLower);
            if (kw == null) {
                continue;
            }
            int priority = toInt(scene.get("priority"), 1);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sceneCode", scene.get("sceneCode"));
            m.put("sceneName", scene.get("sceneName"));
            m.put("type", str(scene.getOrDefault("type", "scene"), "scene"));
            m.put("priority", priority);
            m.put("confidence", 0.8 + (priority / 100.0));
            m.put("method", "keyword");
            m.put("matchedKeyword", kw);
            matched.add(m);
        }
        matched.sort(Comparator.comparingInt((Map<String, Object> m) -> toInt(m.get("priority"), 1)).reversed());
        return matched;
    }

    private List<Map<String, Object>> filterScenesByActive(List<Map<String, Object>> scenes, Boolean isActive) {
        if (isActive == null) {
            return new ArrayList<>(scenes);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> s : scenes) {
            if (isActive.equals(s.getOrDefault("isActive", true))) {
                result.add(s);
            }
        }
        return result;
    }

    private String stripExt(String name) {
        if (name == null) {
            return null;
        }
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
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

    private Map<String, Object> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }
}
