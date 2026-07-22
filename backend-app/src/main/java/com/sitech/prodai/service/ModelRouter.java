package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 多模型路由器 —— 根据场景/意图自动选择最优模型配置。
 *
 * <p>支持三种路由策略：
 * <ul>
 *   <li><b>场景路由</b>：根据 scene 参数选择模型（如 ops → deepseek, rd → gpt-4o）</li>
 *   <li><b>意图路由</b>：根据 intentType 选择模型（如 product_ops_policy → 强推理模型）</li>
 *   <li><b>复杂度路由</b>：根据输入长度/复杂度选择模型（长文本 → 大上下文模型）</li>
 * </ul>
 *
 * <p>配置文件：{@code classpath:model_routing.json}
 */
@Service
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private Map<String, Object> routingConfig = new LinkedHashMap<>();

    @Value("${prodai.model-routing.enabled:false}")
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("[ModelRouter] 多模型路由已禁用");
            return;
        }
        try {
            ClassPathResource resource = new ClassPathResource("model_routing.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    routingConfig = mapper.readValue(is, Map.class);
                    log.info("[ModelRouter] 加载路由配置成功，共 {} 条规则", routingConfig.size());
                }
            } else {
                log.info("[ModelRouter] 未找到 model_routing.json，使用默认配置");
                routingConfig = buildDefaultConfig();
            }
        } catch (Exception e) {
            log.warn("[ModelRouter] 加载路由配置失败: {}，使用默认配置", e.getMessage());
            routingConfig = buildDefaultConfig();
        }
    }

    /**
     * 根据场景和意图类型选择模型配置。
     *
     * @param scene      场景标识（如 ops, rd）
     * @param intentType 意图类型（如 product_ops_policy, chat）
     * @param inputLength 输入文本长度
     * @return 模型配置（包含 model, temperature, max_tokens 等）
     */
    public Map<String, Object> route(String scene, String intentType, int inputLength) {
        if (!enabled) {
            return Map.of();
        }

        // 优先级：场景 > 意图 > 复杂度 > 默认
        Map<String, Object> config = getSceneConfig(scene);
        if (config != null) {
            log.debug("[ModelRouter] 场景路由: {} -> {}", scene, config.get("model"));
            return config;
        }

        config = getIntentConfig(intentType);
        if (config != null) {
            log.debug("[ModelRouter] 意图路由: {} -> {}", intentType, config.get("model"));
            return config;
        }

        config = getComplexityConfig(inputLength);
        if (config != null) {
            log.debug("[ModelRouter] 复杂度路由: inputLength={} -> {}", inputLength, config.get("model"));
            return config;
        }

        log.debug("[ModelRouter] 使用默认模型配置");
        return getDefaultConfig();
    }

    /**
     * 根据场景选择模型配置。
     */
    private Map<String, Object> getSceneConfig(String scene) {
        if (scene == null || scene.isBlank()) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> sceneRules = (Map<String, Object>) routingConfig.get("scene_rules");
        if (sceneRules == null) return null;
        return (Map<String, Object>) sceneRules.get(scene);
    }

    /**
     * 根据意图类型选择模型配置。
     */
    private Map<String, Object> getIntentConfig(String intentType) {
        if (intentType == null || intentType.isBlank()) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> intentRules = (Map<String, Object>) routingConfig.get("intent_rules");
        if (intentRules == null) return null;
        return (Map<String, Object>) intentRules.get(intentType);
    }

    /**
     * 根据输入复杂度选择模型配置。
     */
    private Map<String, Object> getComplexityConfig(int inputLength) {
        @SuppressWarnings("unchecked")
        Map<String, Object> complexityRules = (Map<String, Object>) routingConfig.get("complexity_rules");
        if (complexityRules == null) return null;

        // 长文本 → 大上下文模型
        if (inputLength > 8000) {
            return (Map<String, Object>) complexityRules.get("long_context");
        }
        // 中等文本 → 标准模型
        if (inputLength > 2000) {
            return (Map<String, Object>) complexityRules.get("medium");
        }
        return null;
    }

    /**
     * 获取默认模型配置。
     */
    private Map<String, Object> getDefaultConfig() {
        @SuppressWarnings("unchecked")
        Map<String, Object> defaultConfig = (Map<String, Object>) routingConfig.get("default");
        return defaultConfig != null ? defaultConfig : Map.of();
    }

    /**
     * 构建默认路由配置。
     */
    private Map<String, Object> buildDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();

        // 场景路由
        Map<String, Object> sceneRules = new LinkedHashMap<>();
        sceneRules.put("ops", Map.of(
                "model", "deepseek-chat",
                "temperature", 0.3,
                "max_tokens", 4096,
                "description", "运营场景：使用 DeepSeek 进行数据分析"
        ));
        sceneRules.put("rd", Map.of(
                "model", "gpt-4o",
                "temperature", 0.5,
                "max_tokens", 8192,
                "description", "研发场景：使用 GPT-4o 进行代码生成"
        ));
        config.put("scene_rules", sceneRules);

        // 意图路由
        Map<String, Object> intentRules = new LinkedHashMap<>();
        intentRules.put("product_ops_policy", Map.of(
                "model", "deepseek-reasoner",
                "temperature", 0.1,
                "max_tokens", 4096,
                "description", "立项研判：使用 DeepSeek Reasoner 进行强推理"
        ));
        intentRules.put("product_ops_reason", Map.of(
                "model", "deepseek-reasoner",
                "temperature", 0.1,
                "max_tokens", 4096,
                "description", "异动归因：使用 DeepSeek Reasoner 进行根因分析"
        ));
        intentRules.put("form", Map.of(
                "model", "gpt-4o-mini",
                "temperature", 0.3,
                "max_tokens", 2048,
                "description", "表单生成：使用轻量模型进行字段推断"
        ));
        intentRules.put("validate", Map.of(
                "model", "gpt-4o-mini",
                "temperature", 0.1,
                "max_tokens", 2048,
                "description", "表单校验：使用轻量模型进行规则校验"
        ));
        config.put("intent_rules", intentRules);

        // 复杂度路由
        Map<String, Object> complexityRules = new LinkedHashMap<>();
        complexityRules.put("long_context", Map.of(
                "model", "gpt-4o",
                "temperature", 0.5,
                "max_tokens", 16384,
                "description", "长文本：使用大上下文模型"
        ));
        complexityRules.put("medium", Map.of(
                "model", "deepseek-chat",
                "temperature", 0.5,
                "max_tokens", 4096,
                "description", "中等文本：使用标准模型"
        ));
        config.put("complexity_rules", complexityRules);

        // 默认配置
        config.put("default", Map.of(
                "model", "gpt-4o-mini",
                "temperature", 0.5,
                "max_tokens", 4096,
                "description", "默认配置"
        ));

        return config;
    }

    /**
     * 获取路由配置（供调试使用）。
     */
    public Map<String, Object> getConfig() {
        return routingConfig;
    }

    /**
     * 检查是否启用。
     */
    public boolean isEnabled() {
        return enabled;
    }
}
