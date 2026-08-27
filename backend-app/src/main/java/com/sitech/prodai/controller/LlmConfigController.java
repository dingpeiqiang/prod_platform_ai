package com.sitech.prodai.controller;

import com.sitech.prodai.domain.entity.LlmUserConfig;
import com.sitech.prodai.domain.entity.ModelProvider;
import com.sitech.prodai.dto.ChatCompletionRequest;
import com.sitech.prodai.repository.LlmUserConfigRepository;
import com.sitech.prodai.service.LlmService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * LLM 用户配置接口，持久化到数据库。
 */
@RestController
@RequestMapping("/api/v1/llm-config")
public class LlmConfigController {

    private final LlmUserConfigRepository repository;
    private final Optional<LlmService> llmService;

    public LlmConfigController(LlmUserConfigRepository repository, Optional<LlmService> llmService) {
        this.repository = repository;
        this.llmService = llmService;
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody Map<String, Object> request) {
        String userId = str(request.get("user_identifier"));
        if (userId == null || userId.isBlank()) {
            return fail("user_identifier is required");
        }
        String model = str(request.get("model"));
        if (model == null || model.isBlank()) {
            return fail("model is required");
        }

        repository.findByUserIdentifier(userId).forEach(item -> {
            item.setIsActive(false);
            repository.save(item);
        });

        LlmUserConfig config = new LlmUserConfig();
        config.setUserIdentifier(userId);
        config.setProvider(strOrDefault(request.get("provider"), ModelProvider.CUSTOM.getValue()));
        config.setModel(model);
        config.setApiKey(str(request.get("api_key")));
        config.setBaseUrl(str(request.get("base_url")));
        config.setTemperature(doubleOrDefault(request.get("temperature"), 0.3));
        config.setMaxTokens(intOrDefault(request.get("max_tokens"), 2048));
        config.setThinking(boolOrDefault(request.get("thinking"), false));
        config.setStreamEnabled(boolOrDefault(request.get("stream_enabled"), true));
        config.setMaxInputTokens(intOrDefault(request.get("max_input_tokens"), 180000));
        config.setConfigName(str(request.get("config_name")));
        config.setIsActive(true);
        config.setLastUsedAt(LocalDateTime.now());

        LlmUserConfig saved = repository.save(config);
        return ok(saved, "配置保存成功");
    }

    @GetMapping("/active/{userIdentifier}")
    public Map<String, Object> active(@PathVariable String userIdentifier) {
        // 不按账号过滤：返回全局激活配置
        return repository.findAll().stream()
                .filter(config -> Boolean.TRUE.equals(config.getIsActive()))
                .findFirst()
                .map(config -> {
                    config.setLastUsedAt(LocalDateTime.now());
                    LlmUserConfig saved = repository.save(config);
                    return ok(saved, "获取成功");
                })
                .orElseGet(() -> fail("未找到激活配置"));
    }

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody Map<String, Object> request) {
        String model = str(request.get("model"));
        String baseUrl = str(request.get("base_url"));
        String apiKey = str(request.get("api_key"));

        Map<String, Object> body = new LinkedHashMap<>();
        if (!StringUtils.hasText(model)) {
            body.put("success", false);
            body.put("message", "model is required");
            return body;
        }

        if (!StringUtils.hasText(baseUrl)) {
            body.put("success", false);
            body.put("message", "base_url is required");
            return body;
        }

        if (llmService.isEmpty()) {
            body.put("success", false);
            body.put("message", "LLM 服务未启用");
            return body;
        }

        Map<String, Object> modelConfig = new LinkedHashMap<>(request);
        modelConfig.remove("user_identifier");
        modelConfig.remove("config_name");

        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setPrompt("Hello, this is a connection test message.");
        req.setModelConfig(modelConfig);

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> result = llmService.get().complete(req);
            Object content = result.getOrDefault("content", "");
            String preview = content == null ? "" : String.valueOf(content);
            if (preview.length() > 100) {
                preview = preview.substring(0, 100);
            }
            body.put("success", true);
            body.put("message", "连接成功");
            body.put("provider", str(request.get("provider")));
            body.put("model", model);
            body.put("base_url", baseUrl);
            body.put("api_key_present", StringUtils.hasText(apiKey));
            body.put("response_preview", preview);
            body.put("latency_ms", System.currentTimeMillis() - start);
            return body;
        } catch (Exception e) {
            body.put("success", false);
            body.put("message", "连接失败: " + e.getMessage());
            body.put("detail", String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
            body.put("provider", str(request.get("provider")));
            body.put("model", model);
            body.put("base_url", baseUrl);
            body.put("api_key_present", StringUtils.hasText(apiKey));
            body.put("suggestion", "请检查 api_key / base_url / model 是否正确且匹配");
            body.put("latency_ms", System.currentTimeMillis() - start);
            return body;
        }
    }

    @GetMapping("/list/{userIdentifier}")
    public Map<String, Object> list(@PathVariable String userIdentifier) {
        // 不按账号过滤：返回全部配置
        List<LlmUserConfig> configs = repository.findAll();
        List<Map<String, Object>> configList = configs.stream()
                .map(this::toPublicMap)
                .collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "获取成功");
        body.put("data", configList);
        return body;
    }

    @DeleteMapping("/{configId}")
    public Map<String, Object> delete(@PathVariable Integer configId) {
        if (repository.existsById(configId)) {
            repository.deleteById(configId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("message", "删除成功");
            return body;
        } else {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "配置不存在");
            return body;
        }
    }

    @PostMapping("/activate")
    public Map<String, Object> activate(@RequestBody Map<String, Object> request) {
        String userId = str(request.get("user_identifier"));
        Integer configId = intOrDefault(request.get("config_id"), null);

        if (userId == null || userId.isBlank()) {
            return fail("user_identifier is required");
        }
        if (configId == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "config_id is required");
            return body;
        }

        return repository.findById(configId)
                .map(config -> {
                    // 不按账号过滤：全局置为仅一条激活
                    repository.findAll().forEach(item -> {
                        item.setIsActive(false);
                        repository.save(item);
                    });
                    config.setIsActive(true);
                    config.setLastUsedAt(LocalDateTime.now());
                    LlmUserConfig saved = repository.save(config);
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("success", true);
                    body.put("message", "激活成功");
                    body.put("config", toPublicMap(saved));
                    return body;
                })
                .orElseGet(() -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("success", false);
                    body.put("message", "配置不存在");
                    return body;
                });
    }

    private Map<String, Object> ok(LlmUserConfig config, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", message);
        body.put("config", toPublicMap(config));
        return body;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("config", null);
        return body;
    }

    private Map<String, Object> toPublicMap(LlmUserConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", config.getId());
        map.put("user_identifier", config.getUserIdentifier());
        map.put("provider", config.getProvider());
        map.put("model", config.getModel());
        map.put("base_url", config.getBaseUrl());
        map.put("temperature", config.getTemperature());
        map.put("max_tokens", config.getMaxTokens());
        map.put("thinking", config.getThinking());
        map.put("stream_enabled", config.getStreamEnabled());
        map.put("max_input_tokens", config.getMaxInputTokens());
        map.put("config_name", config.getConfigName());
        map.put("is_active", config.getIsActive());
        map.put("updated_at", config.getUpdatedAt() == null ? null : config.getUpdatedAt().toString());
        map.put("last_used_at", config.getLastUsedAt() == null ? null : config.getLastUsedAt().toString());
        return map;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String strOrDefault(Object value, String defaultValue) {
        String s = str(value);
        return StringUtils.hasText(s) ? s : defaultValue;
    }

    private static Integer intOrDefault(Object value, Integer defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static Double doubleOrDefault(Object value, Double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static Boolean boolOrDefault(Object value, Boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
