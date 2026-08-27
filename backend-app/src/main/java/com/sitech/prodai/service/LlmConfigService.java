package com.sitech.prodai.service;

import com.sitech.prodai.config.ConfigLoader;
import com.sitech.prodai.domain.entity.LlmUserConfig;
import com.sitech.prodai.domain.entity.ModelProvider;
import com.sitech.prodai.repository.LlmUserConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LLM 配置管理服务 —— 对齐 Python {@code app/services/llm_service.py::LLMService} 的配置管理部分。
 *
 * <p>Python 版本同时承担配置管理和 LLM 调用，Java 端职责拆分：
 * <ul>
 *   <li>本类负责配置加载、刷新、缓存（从数据库读取 LlmUserConfig）</li>
 *   <li>LLM 调用通过 Spring AI 由 {@link LlmService} 处理</li>
 * </ul>
 *
 * <p>配置优先级：数据库激活记录 > app_config.json。
 */
@Service
public class LlmConfigService {

    private static final Logger log = LoggerFactory.getLogger(LlmConfigService.class);

    private final ConfigLoader configLoader;
    private final LlmUserConfigRepository configRepository;

    /** 内存缓存的 LLM 配置（从数据库激活记录加载） */
    private volatile Map<String, Object> cachedConfig;
    /** 内存缓存的数据库原始记录 ID（用于刷新判断） */
    private volatile Integer cachedConfigId;

    public LlmConfigService(ConfigLoader configLoader, LlmUserConfigRepository configRepository) {
        this.configLoader = configLoader;
        this.configRepository = configRepository;
        refreshConfig();
    }

    /** 对齐 Python get_cached_config —— 获取缓存配置（默认不包含 api_key） */
    public Map<String, Object> getCachedConfig(boolean includeApiKey) {
        Map<String, Object> config = buildConfigMap();
        if (includeApiKey) {
            Object apiKey = config.get("api_key");
            if (apiKey == null) {
                config.put("api_key", "");
            }
        } else {
            config.remove("api_key");
        }
        return config;
    }

    /** 对齐 Python refresh_config —— 从数据库刷新配置缓存 */
    public boolean refreshConfig() {
        log.info("[LlmConfigService] 刷新配置...");
        try {
            List<LlmUserConfig> all = configRepository.findAll();
            LlmUserConfig active = null;
            for (LlmUserConfig c : all) {
                if (Boolean.TRUE.equals(c.getIsActive())) {
                    active = c;
                    break;
                }
            }
            if (active == null) {
                log.info("[LlmConfigService] 数据库中未找到激活的配置，使用配置文件");
                cachedConfig = null;
                cachedConfigId = null;
                return false;
            }
            cachedConfig = toConfigMap(active);
            cachedConfigId = active.getId();
            log.info("[LlmConfigService] 从数据库加载配置: model={} baseUrl={}",
                    active.getModel(), active.getBaseUrl() == null ? "(empty)" : "(set)");
            return true;
        } catch (Exception e) {
            log.error("[LlmConfigService] 刷新配置失败", e);
            return false;
        }
    }

    /** 获取当前激活的 LlmUserConfig 实体（数据库查询） */
    public Optional<LlmUserConfig> getActiveConfig() {
        try {
            List<LlmUserConfig> all = configRepository.findAll();
            for (LlmUserConfig c : all) {
                if (Boolean.TRUE.equals(c.getIsActive())) {
                    return Optional.of(c);
                }
            }
        } catch (Exception e) {
            log.error("[LlmConfigService] 查询激活配置失败", e);
        }
        return Optional.empty();
    }

    /** 获取所有 LLM 配置列表 */
    public List<LlmUserConfig> listAllConfigs() {
        return configRepository.findAll();
    }

    /** 获取缓存的配置 ID（用于判断是否需要刷新） */
    public Integer getCachedConfigId() {
        return cachedConfigId;
    }

    // ==================== 内部方法 ====================

    private Map<String, Object> buildConfigMap() {
        if (cachedConfig != null) {
            return new LinkedHashMap<>(cachedConfig);
        }
        // 回退到 app_config.json 中的 llm 配置
        Map<String, Object> appConfig = configLoader.getAppConfig();
        Object llmObj = appConfig.get("llm");
        if (llmObj instanceof Map<?, ?> llmMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            llmMap.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        // 默认空配置
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("provider", ModelProvider.CUSTOM.getValue());
        empty.put("model", "");
        empty.put("base_url", "");
        empty.put("temperature", 0.3);
        empty.put("max_tokens", 2048);
        empty.put("thinking", false);
        empty.put("stream_enabled", true);
        empty.put("enabled", false);
        return empty;
    }

    private Map<String, Object> toConfigMap(LlmUserConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", c.getProvider() == null ? ModelProvider.CUSTOM.getValue() : c.getProvider());
        m.put("model", c.getModel() == null ? "" : c.getModel());
        m.put("base_url", c.getBaseUrl() == null ? "" : c.getBaseUrl());
        m.put("api_key", c.getApiKey() == null ? "" : c.getApiKey().trim().replaceAll("`", ""));
        m.put("auth_type", c.getAuthType() == null ? "bearer" : c.getAuthType());
        m.put("auth_header", c.getAuthHeader());
        m.put("api_format", c.getApiFormat() == null ? "openai" : c.getApiFormat());
        m.put("is_full_url", c.getIsFullUrl());
        m.put("temperature", c.getTemperature() == null ? 0.3 : c.getTemperature());
        m.put("max_tokens", c.getMaxTokens() == null ? 2048 : c.getMaxTokens());
        m.put("thinking", c.getThinking());
        m.put("stream_enabled", c.getStreamEnabled() == null ? true : c.getStreamEnabled());
        m.put("max_input_tokens", c.getMaxInputTokens() == null ? 180000 : c.getMaxInputTokens());
        m.put("enabled", true);
        m.put("config_id", c.getId());
        m.put("config_name", c.getConfigName());
        return m;
    }
}
