package com.sitech.prodai.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置加载器 —— 对齐 Python {@code app/core/config_loader.py::ConfigLoader}。
 *
 * <p>从 classpath 加载本体 / 场景 / 提示词 / 应用配置 / 推荐模板，
 * 对应 Python 从 {@code backend/config/} 目录加载的文件数据源。
 *
 * <p>Python 资源目录 → Java classpath 映射：
 * <ul>
 *   <li>backend/config/ontologies/*.json        → classpath:ontologies/*.json</li>
 *   <li>backend/config/scenes/scene_mapping.json→ classpath:scenes/scene_mapping.json</li>
 *   <li>backend/config/prompts/*.txt            → classpath:prompts/*.txt</li>
 *   <li>backend/config/prompts/scenes/*.txt     → classpath:prompts/scenes/*.txt</li>
 *   <li>backend/config/app_config.json          → classpath:app_config.json</li>
 *   <li>backend/config/templates/recommendations.json → classpath:templates/recommendations.json</li>
 * </ul>
 */
@Component
public class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /** 本体定义：ontologyCode → 原始 JSON */
    private final Map<String, Map<String, Object>> ontologies = new ConcurrentHashMap<>();
    /** 场景列表 */
    private final List<Map<String, Object>> scenes = new ArrayList<>();
    /** 通用提示词：promptName → 文本 */
    private final Map<String, String> prompts = new ConcurrentHashMap<>();
    /** 场景提示词：promptName → 文本 */
    private final Map<String, String> scenePrompts = new ConcurrentHashMap<>();
    /** 应用配置（app_config.json 全量） */
    private final Map<String, Object> appConfig = new ConcurrentHashMap<>();
    /** 系统配置（app_config 中提取的子集） */
    private final Map<String, Object> systemConfig = new ConcurrentHashMap<>();
    /** 推荐模板：formCode → {fieldCode → [values]} */
    private final Map<String, Map<String, Object>> recommendations = new ConcurrentHashMap<>();

    public ConfigLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        reloadAll();
    }

    /** 全量重新加载（对齐 Python reload_config(None)） */
    public synchronized void reloadAll() {
        loadAppConfig();
        loadRecommendations();
        loadPrompts();
        loadOntologies();
        loadScenes();
        log.info("[ConfigLoader] 配置加载完成 ontologies={}, scenes={}, prompts={}, scenePrompts={}",
                ontologies.size(), scenes.size(), prompts.size(), scenePrompts.size());
    }

    // ==================== 加载方法 ====================

    private void loadAppConfig() {
        appConfig.clear();
        systemConfig.clear();
        try {
            Resource resource = resolver.getResource("classpath:app_config.json");
            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    Map<String, Object> raw = objectMapper.readValue(in, new TypeReference<>() {
                    });
                    appConfig.putAll(raw);
                    // 提取系统配置子集（对齐 Python _load_app_config）
                    for (String key : new String[]{"recommendation", "smartRecommend", "sceneRecognition", "fieldExtraction"}) {
                        if (raw.containsKey(key)) {
                            systemConfig.put(key, raw.get(key));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ConfigLoader] 加载 app_config.json 失败: {}", e.getMessage());
        }
    }

    private void loadRecommendations() {
        recommendations.clear();
        try {
            Resource resource = resolver.getResource("classpath:templates/recommendations.json");
            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    Map<String, Object> raw = objectMapper.readValue(in, new TypeReference<>() {
                    });
                    Object recs = raw.get("recommendations");
                    if (recs instanceof Map<?, ?> map) {
                        for (Map.Entry<?, ?> e : map.entrySet()) {
                            if (e.getKey() instanceof String k && e.getValue() instanceof Map<?, ?> v) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> cast = (Map<String, Object>) v;
                                recommendations.put(k, cast);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ConfigLoader] 加载 recommendations.json 失败: {}", e.getMessage());
        }
    }

    private void loadPrompts() {
        prompts.clear();
        scenePrompts.clear();
        loadTextDir("classpath*:prompts/*.txt", prompts);
        loadTextDir("classpath*:prompts/scenes/*.txt", scenePrompts);
    }

    private void loadTextDir(String pattern, Map<String, String> target) {
        try {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".txt")) {
                    continue;
                }
                String name = filename.substring(0, filename.length() - 4);
                try (InputStream in = resource.getInputStream()) {
                    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    target.put(name, text);
                }
            }
        } catch (Exception e) {
            log.warn("[ConfigLoader] 加载提示词目录失败 pattern={} err={}", pattern, e.getMessage());
        }
    }

    private void loadOntologies() {
        ontologies.clear();
        try {
            Resource[] resources = resolver.getResources("classpath*:ontologies/*.json");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                String code = filename.substring(0, filename.length() - 5);
                try (InputStream in = resource.getInputStream()) {
                    Map<String, Object> data = objectMapper.readValue(in, new TypeReference<>() {
                    });
                    ontologies.put(code, data);
                }
            }
        } catch (Exception e) {
            log.warn("[ConfigLoader] 加载本体失败: {}", e.getMessage());
        }
        log.info("[ConfigLoader] 从文件加载本体 count={}", ontologies.size());
    }

    @SuppressWarnings("unchecked")
    private void loadScenes() {
        scenes.clear();
        try {
            Resource resource = resolver.getResource("classpath:scenes/scene_mapping.json");
            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    Map<String, Object> data = objectMapper.readValue(in, new TypeReference<>() {
                    });
                    Object mappings = data.get("sceneMappings");
                    if (mappings instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> m) {
                                scenes.add((Map<String, Object>) m);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ConfigLoader] 加载场景映射失败: {}", e.getMessage());
        }
        log.info("[ConfigLoader] 从文件加载场景 count={}", scenes.size());
    }

    // ==================== 查询方法（对齐 Python ConfigLoader） ====================

    public Map<String, Object> getAppConfig() {
        return appConfig;
    }

    public List<Map<String, Object>> getSceneMappings() {
        return scenes;
    }

    public Map<String, Object> getSceneByCode(String sceneCode) {
        for (Map<String, Object> scene : scenes) {
            if (sceneCode.equals(scene.get("sceneCode"))) {
                return scene;
            }
        }
        return null;
    }

    public List<Map<String, Object>> getAllScenes() {
        return scenes;
    }

    public String getScenePrompt(String sceneCode) {
        Map<String, Object> scene = getSceneByCode(sceneCode);
        if (scene == null) {
            return null;
        }
        Object actionPrompt = scene.get("actionPrompt");
        if (actionPrompt == null) {
            actionPrompt = scene.get("promptCode");
        }
        if (actionPrompt == null) {
            return null;
        }
        String promptName = String.valueOf(actionPrompt);
        int dot = promptName.indexOf('.');
        if (dot > 0) {
            promptName = promptName.substring(0, dot);
        }
        return scenePrompts.getOrDefault(promptName, prompts.get(promptName));
    }

    public Map<String, Object> getOntology(String formCode) {
        return ontologies.get(formCode);
    }

    public Map<String, Map<String, Object>> getAllOntologies() {
        return ontologies;
    }

    @SuppressWarnings("unchecked")
    public List<String> getRecommendations(String formCode, String fieldCode) {
        Map<String, Object> formRecs = recommendations.get(formCode);
        if (formRecs == null) {
            return Collections.emptyList();
        }
        Object values = formRecs.get(fieldCode);
        if (values instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object v : list) {
                result.add(v == null ? null : String.valueOf(v));
            }
            return result;
        }
        return Collections.emptyList();
    }

    public String getPrompt(String promptName) {
        if (prompts.containsKey(promptName)) {
            return prompts.get(promptName);
        }
        return scenePrompts.get(promptName);
    }

    public Map<String, Object> getSystemConfig() {
        return systemConfig;
    }

    public Map<String, Object> getRecommendationConfig() {
        Object rec = systemConfig.get("recommendation");
        if (rec instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        return Collections.emptyMap();
    }

    public Map<String, Object> getSceneRecognitionConfig() {
        Object cfg = systemConfig.get("sceneRecognition");
        if (cfg instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        return Collections.emptyMap();
    }

    public Map<String, Object> getFieldExtractionConfig() {
        Object cfg = systemConfig.get("fieldExtraction");
        if (cfg instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        return Collections.emptyMap();
    }

    public void reloadConfig(String configType) {
        if (configType == null || "all".equals(configType)) {
            reloadAll();
        } else {
            switch (configType) {
                case "system_config", "app_config" -> loadAppConfig();
                case "ontologies" -> loadOntologies();
                case "recommendations" -> loadRecommendations();
                case "prompts" -> loadPrompts();
                case "scenes" -> loadScenes();
                default -> log.warn("[ConfigLoader] 未知配置类型: {}", configType);
            }
        }
    }

    /** 占位：数据库数据源切换（对齐 Python switch_data_source），后续阶段实现 */
    public String getCurrentDataSourceType() {
        return "file";
    }
}
