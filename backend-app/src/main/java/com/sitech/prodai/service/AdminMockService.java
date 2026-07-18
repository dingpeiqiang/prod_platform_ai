package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Admin managers mock (scenes / prompts / ontologies).
 * Contract-aligned with frontend sceneApi / promptApi / ontologyApi.
 */
@Service
public class AdminMockService {

    private final ObjectMapper objectMapper;
    private final FormMockService formMockService;
    private final List<Map<String, Object>> scenes = new ArrayList<>();
    private final Map<String, Map<String, Object>> prompts = new ConcurrentHashMap<>();

    public AdminMockService(ObjectMapper objectMapper, FormMockService formMockService) {
        this.objectMapper = objectMapper;
        this.formMockService = formMockService;
        loadScenes();
        loadPrompts();
    }

    private void loadScenes() {
        try {
            Resource resource = new PathMatchingResourcePatternResolver()
                    .getResource("classpath:scenes/scene_mapping.json");
            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    Map<String, Object> root = objectMapper.readValue(in, new TypeReference<>() {});
                    Object mappings = root.get("sceneMappings");
                    if (mappings instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> m) {
                                Map<String, Object> row = new LinkedHashMap<>();
                                m.forEach((k, v) -> row.put(String.valueOf(k), v));
                                scenes.add(row);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load scene_mapping.json: " + e.getMessage(), e);
        }
        if (scenes.isEmpty()) {
            Map<String, Object> demo = new LinkedHashMap<>();
            demo.put("sceneCode", "offering_config_chat");
            demo.put("sceneName", "智聊·对话配置");
            demo.put("description", "产商品对话配置");
            demo.put("keywords", List.of("产商品配置"));
            demo.put("priority", 12);
            demo.put("isActive", true);
            demo.put("promptCode", "offering_config_prompt");
            demo.put("type", "scene");
            scenes.add(demo);
        }
    }

    private void loadPrompts() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:prompts/scenes/*.txt");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || filename.startsWith("_")) {
                    continue;
                }
                String code = filename.replace(".txt", "");
                String content;
                try (InputStream in = resource.getInputStream()) {
                    content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                Map<String, Object> prompt = new LinkedHashMap<>();
                prompt.put("id", prompts.size() + 1);
                prompt.put("code", code);
                prompt.put("name", code);
                prompt.put("description", "Loaded from classpath:prompts/scenes/" + filename);
                prompt.put("category", "general");
                prompt.put("content", content);
                prompt.put("variables", List.of());
                prompt.put("tools", List.of());
                prompt.put("is_template", false);
                prompt.put("version", 1);
                prompt.put("is_active", true);
                prompt.put("createdAt", Instant.now().toString());
                prompt.put("updatedAt", Instant.now().toString());
                prompts.put(code, prompt);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load prompts: " + e.getMessage(), e);
        }
        if (prompts.isEmpty()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", 1);
            p.put("code", "offering_config_prompt");
            p.put("name", "产商品配置提示词");
            p.put("description", "默认提示词");
            p.put("category", "form");
            p.put("content", "# offering config prompt");
            p.put("variables", List.of());
            p.put("tools", List.of());
            p.put("is_template", false);
            p.put("version", 1);
            p.put("is_active", true);
            prompts.put("offering_config_prompt", p);
        }
    }

    public Map<String, Object> listScenesTree(Boolean isActive) {
        List<Map<String, Object>> filtered = filterScenes(isActive);
        List<Map<String, Object>> tree = buildTree(filtered);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", filtered.size());
        body.put("data", tree);
        return body;
    }

    public Map<String, Object> listScenes(Boolean isActive) {
        List<Map<String, Object>> filtered = filterScenes(isActive);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", filtered.size());
        body.put("data", filtered.stream().map(LinkedHashMap::new).collect(Collectors.toList()));
        return body;
    }

    public Map<String, Object> getScene(String sceneCode) {
        Map<String, Object> scene = findScene(sceneCode);
        if (scene == null) {
            return fail("Scene " + sceneCode + " not found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", new LinkedHashMap<>(scene));
        return body;
    }

    public Map<String, Object> getSceneStats() {
        int total = scenes.size();
        int active = (int) scenes.stream().filter(s -> Boolean.TRUE.equals(s.get("isActive"))).count();
        int center = (int) scenes.stream().filter(s -> "center".equals(s.get("type"))).count();
        int business = (int) scenes.stream().filter(s -> "business".equals(s.get("type"))).count();
        int scene = (int) scenes.stream().filter(s -> "scene".equals(s.get("type"))).count();
        Map<String, Object> byType = new LinkedHashMap<>();
        byType.put("center", center);
        byType.put("business", business);
        byType.put("scene", scene);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("active", active);
        data.put("inactive", total - active);
        data.put("byType", byType);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }

    public Map<String, Object> testSceneRecognition(String userInput) {
        String text = userInput == null ? "" : userInput;
        Map<String, Object> matched = null;
        for (Map<String, Object> scene : scenes) {
            if (!Boolean.TRUE.equals(scene.get("isActive"))) {
                continue;
            }
            Object kw = scene.get("keywords");
            if (kw instanceof List<?> list) {
                for (Object k : list) {
                    if (k != null && text.contains(String.valueOf(k))) {
                        matched = scene;
                        break;
                    }
                }
            }
            if (matched != null) {
                break;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (matched != null) {
            data.put("sceneCode", matched.get("sceneCode"));
            data.put("sceneName", matched.get("sceneName"));
            data.put("matched", true);
            data.put("score", 0.9);
        } else {
            data.put("matched", false);
            data.put("sceneCode", null);
            data.put("score", 0.0);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }

    public Map<String, Object> unsupportedWrite(String action) {
        return fail("当前为可替换 Mock（文件/内存），暂不支持" + action + "；后续可替换为配置中心/DB");
    }

    public Map<String, Object> listPrompts(String category, Boolean isActive) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> p : prompts.values()) {
            if (category != null && !category.isBlank() && !Objects.equals(category, p.get("category"))) {
                continue;
            }
            if (isActive != null && !Objects.equals(isActive, p.get("is_active"))) {
                continue;
            }
            list.add(new LinkedHashMap<>(p));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", list);
        return body;
    }

    public Map<String, Object> getPrompt(String code) {
        Map<String, Object> p = prompts.get(code);
        if (p == null) {
            return fail("Prompt " + code + " not found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", new LinkedHashMap<>(p));
        return body;
    }

    public Map<String, Object> createPrompt(Map<String, Object> data) {
        String code = str(data.get("code"));
        if (code == null || code.isBlank()) {
            return fail("Prompt code is required");
        }
        if (prompts.containsKey(code)) {
            return fail("Prompt " + code + " already exists");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", prompts.size() + 1);
        p.put("code", code);
        p.put("name", data.getOrDefault("name", code));
        p.put("description", data.getOrDefault("description", ""));
        p.put("category", data.getOrDefault("category", "general"));
        p.put("content", data.getOrDefault("content", ""));
        p.put("variables", data.getOrDefault("variables", List.of()));
        p.put("tools", data.getOrDefault("tools", List.of()));
        p.put("is_template", data.getOrDefault("is_template", false));
        p.put("version", 1);
        p.put("is_active", data.getOrDefault("is_active", true));
        p.put("createdAt", Instant.now().toString());
        p.put("updatedAt", Instant.now().toString());
        prompts.put(code, p);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", new LinkedHashMap<>(p));
        body.put("message", "created");
        return body;
    }

    public Map<String, Object> updatePrompt(String code, Map<String, Object> data) {
        Map<String, Object> p = prompts.get(code);
        if (p == null) {
            return fail("Prompt " + code + " not found");
        }
        if (data.containsKey("name")) p.put("name", data.get("name"));
        if (data.containsKey("description")) p.put("description", data.get("description"));
        if (data.containsKey("category")) p.put("category", data.get("category"));
        if (data.containsKey("content")) p.put("content", data.get("content"));
        if (data.containsKey("variables")) p.put("variables", data.get("variables"));
        if (data.containsKey("tools")) p.put("tools", data.get("tools"));
        if (data.containsKey("is_active")) p.put("is_active", data.get("is_active"));
        Object ver = p.get("version");
        int version = ver instanceof Number n ? n.intValue() : 1;
        p.put("version", version + 1);
        p.put("updatedAt", Instant.now().toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", new LinkedHashMap<>(p));
        body.put("message", "updated");
        return body;
    }

    public Map<String, Object> deletePrompt(String code) {
        if (!prompts.containsKey(code)) {
            return fail("Prompt " + code + " not found");
        }
        prompts.remove(code);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "Prompt deleted successfully");
        return body;
    }

    public Map<String, Object> promptCategories() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", List.of(
                cat("general", "通用"),
                cat("form", "表单生成"),
                cat("qa", "问答"),
                cat("tool", "工具调用"),
                cat("analysis", "分析"),
                cat("writing", "写作")
        ));
        return body;
    }

    public Map<String, Object> promptVersions(String code) {
        Map<String, Object> p = prompts.get(code);
        if (p == null) {
            return fail("Prompt " + code + " not found");
        }
        Map<String, Object> ver = new LinkedHashMap<>();
        ver.put("version", p.get("version"));
        ver.put("content", p.get("content"));
        ver.put("createdAt", p.get("updatedAt"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", List.of(ver));
        return body;
    }

    public Map<String, Object> previewPrompt(String code, Map<String, Object> variables) {
        Map<String, Object> p = prompts.get(code);
        if (p == null) {
            return fail("Prompt " + code + " not found");
        }
        String content = String.valueOf(p.getOrDefault("content", ""));
        if (variables != null) {
            for (Map.Entry<String, Object> e : variables.entrySet()) {
                content = content.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue()));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", Map.of("content", content));
        return body;
    }

    public Map<String, Object> aiStub(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", Map.of("content", message + "\n\n（Mock）当前为可替换实现，未接通真实 LLM。"));
        body.put("message", "ok");
        return body;
    }

    public Map<String, Object> listOntologies(String category, Boolean isActive) {
        Map<String, Object> listed = formMockService.listOntologies();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ontologies = (List<Map<String, Object>>) listed.getOrDefault("ontologies", List.of());
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> o : ontologies) {
            if (category != null && !category.isBlank() && !Objects.equals(category, o.get("category"))) {
                continue;
            }
            if (isActive != null && !Objects.equals(isActive, o.get("isActive"))) {
                continue;
            }
            data.add(new LinkedHashMap<>(o));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }

    public Map<String, Object> getOntology(String code) {
        Map<String, Object> schema = formMockService.getFormSchema(code);
        if (!Boolean.TRUE.equals(schema.get("success"))) {
            return fail("本体 " + code + " 不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ontologyCode", schema.get("formCode"));
        data.put("ontologyName", schema.get("formName"));
        Object dataObj = schema.get("data");
        Map<?, ?> dataMap = dataObj instanceof Map<?, ?> m ? m : Map.of();
        Object category = dataMap.get("category");
        Object description = dataMap.get("description");
        data.put("category", category == null ? "general" : category);
        data.put("description", description == null ? "" : description);
        data.put("entities", schema.get("entities"));
        data.put("isActive", true);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }

    public Map<String, Object> ontologyCategories() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", List.of(
                cat("general", "通用表单"),
                cat("tariff", "资费备案"),
                cat("product", "产商品配置"),
                cat("customer", "客户信息"),
                cat("business", "业务办理")
        ));
        return body;
    }

    private List<Map<String, Object>> filterScenes(Boolean isActive) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> s : scenes) {
            if (isActive != null && !Objects.equals(isActive, s.get("isActive"))) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    private Map<String, Object> findScene(String code) {
        for (Map<String, Object> s : scenes) {
            if (Objects.equals(code, s.get("sceneCode"))) {
                return s;
            }
        }
        return null;
    }

    private List<Map<String, Object>> buildTree(List<Map<String, Object>> source) {
        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        List<Map<String, Object>> tree = new ArrayList<>();
        for (Map<String, Object> scene : source) {
            String code = str(scene.get("sceneCode"));
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", code);
            node.put("label", scene.getOrDefault("sceneName", code));
            node.put("type", scene.getOrDefault("type", "scene"));
            node.put("sceneCode", code);
            node.put("sceneName", scene.get("sceneName"));
            node.put("priority", scene.getOrDefault("priority", 1));
            node.put("isActive", scene.getOrDefault("isActive", true));
            node.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put(code, node);
        }
        for (Map<String, Object> scene : source) {
            String code = str(scene.get("sceneCode"));
            Map<String, Object> node = nodeMap.get(code);
            if (node == null) continue;
            Object parentId = scene.get("parentId");
            if (parentId == null || String.valueOf(parentId).isBlank()) {
                tree.add(node);
            } else if (parentId instanceof String && nodeMap.containsKey(parentId)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get(parentId).get("children");
                children.add(node);
            } else {
                // numeric parentId or missing parent -> attach as root for mock visibility
                tree.add(node);
            }
        }
        return tree;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }

    private Map<String, Object> cat(String code, String name) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("code", code);
        c.put("name", name);
        return c;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}