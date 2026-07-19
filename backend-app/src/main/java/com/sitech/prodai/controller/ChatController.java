package com.sitech.prodai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.domain.entity.LlmUserConfig;
import com.sitech.prodai.domain.entity.ModelProvider;
import com.sitech.prodai.dto.ChatCompletionRequest;
import com.sitech.prodai.repository.LlmUserConfigRepository;
import com.sitech.prodai.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final Optional<LlmService> llmService;
    private final ObjectMapper objectMapper;
    private final ProdAiProperties properties;
    private final LlmUserConfigRepository llmUserConfigRepository;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicReference<Map<String, Object>> activeModel = new AtomicReference<>(defaultModel());

    public ChatController(Optional<LlmService> llmService, ObjectMapper objectMapper, ProdAiProperties properties,
                          LlmUserConfigRepository llmUserConfigRepository) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.llmUserConfigRepository = llmUserConfigRepository;
        log.info("[ChatController] initialized, llmEnabled={}", properties.getLlm().isEnabled());
    }

    @PostMapping("/completion")
    public Map<String, Object> completion(@RequestBody ChatCompletionRequest request) {
        log.info("[ChatController] completion called, prompt_length={}, llmEnabled={}",
                request.getPrompt() != null ? request.getPrompt().length() : 0, properties.getLlm().isEnabled());
        try {
            return llmService
                    .map(s -> s.complete(request))
                    .orElseGet(() -> Map.of(
                            "success", false,
                            "error_code", "service_unavailable",
                            "message", "LLM service is not enabled. Set LLM_ENABLED=true and configure API key."
                    ));
        } catch (Exception e) {
            log.error("[ChatController] completion failed", e);
            throw e;
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatCompletionRequest request) {
        log.info("[ChatController] stream called, prompt_length={}",
                request.getPrompt() != null ? request.getPrompt().length() : 0);
        SseEmitter emitter = new SseEmitter(300_000L);
        Flux<Map<String, Object>> events = llmService
                .map(s -> s.streamEvents(request))
                .orElseGet(() -> Flux.just(
                        Map.of("type", "text_start"),
                        Map.of("type", "text", "content", "LLM service is not enabled"),
                        Map.of("type", "text_end"),
                        Map.of("type", "done", "intentType", "chat", "isForm", false)
                ));

        executor.execute(() -> {
            try {
                events.toStream().forEach(event -> {
                    try {
                        String json = objectMapper.writeValueAsString(event);
                        emitter.send(SseEmitter.event().data(json));
                    } catch (IOException e) {
                        log.error("[ChatController] stream send failed", e);
                        throw new RuntimeException(e);
                    }
                });
                emitter.complete();
                log.debug("[ChatController] stream completed");
            } catch (Exception ex) {
                log.error("[ChatController] stream error", ex);
                try {
                    String err = objectMapper.writeValueAsString(Map.of(
                            "type", "text",
                            "content", "流式输出失败: " + ex.getMessage()
                    ));
                    emitter.send(SseEmitter.event().data(err));
                    emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(
                            Map.of("type", "done", "intentType", "chat", "isForm", false))));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    /** Replaceable mock: lists supported LLM providers for frontend model picker. */
    @GetMapping("/model/providers")
    public Map<String, Object> providers() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("providers", List.of(
                provider(ModelProvider.OPENAI.getValue(), ModelProvider.OPENAI.getLabel(), true),
                provider(ModelProvider.AZURE.getValue(), ModelProvider.AZURE.getLabel(), false),
                provider(ModelProvider.CUSTOM.getValue(), ModelProvider.CUSTOM.getLabel(), true),
                provider(ModelProvider.LOCAL.getValue(), ModelProvider.LOCAL.getLabel(), !properties.getLlm().isEnabled())
        ));
        body.put("active", activeModel.get());
        body.put("llmEnabled", properties.getLlm().isEnabled());
        return body;
    }

    /** System default model — flat fields + nested config for frontend compatibility. */
    @GetMapping("/model/default")
    public Map<String, Object> defaultModelConfig() {
        Map<String, Object> config = buildDefaultConfig();
        Map<String, Object> body = new LinkedHashMap<>(config);
        body.put("success", true);
        body.put("config", config);
        return body;
    }

    /** Available models for workflow LLM nodes / model pickers — only from DB. */
    @GetMapping("/model/available")
    public Map<String, Object> availableModels() {
        List<Map<String, Object>> models = llmUserConfigRepository.findAll().stream()
                .sorted((a, b) -> {
                    boolean aActive = Boolean.TRUE.equals(a.getIsActive());
                    boolean bActive = Boolean.TRUE.equals(b.getIsActive());
                    if (aActive != bActive) {
                        return aActive ? -1 : 1;
                    }
                    if (a.getUpdatedAt() == null && b.getUpdatedAt() == null) return 0;
                    if (a.getUpdatedAt() == null) return 1;
                    if (b.getUpdatedAt() == null) return -1;
                    return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                })
                .map(cfg -> toModelRow(
                        cfg,
                        Boolean.TRUE.equals(cfg.getIsActive()),
                        Boolean.TRUE.equals(cfg.getIsActive()) ? "当前激活" : "历史配置"
                ))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("models", models);
        return body;
    }

    /** Connectivity test — real ping when LLM enabled, otherwise field-level mock. */
    @PostMapping("/model/test")
    public Map<String, Object> testModel(@RequestBody Map<String, Object> modelConfig) {
        if (modelConfig == null || modelConfig.isEmpty()) {
            return Map.of("success", false, "message", "modelConfig is required");
        }
        Object model = modelConfig.get("model");
        if (model == null || String.valueOf(model).isBlank()) {
            return Map.of("success", false, "message", "请填写模型名称");
        }

        String provider = String.valueOf(modelConfig.getOrDefault("provider", "custom"));
        if (properties.getLlm().isEnabled()) {
            try {
                ChatCompletionRequest req = new ChatCompletionRequest();
                req.setPrompt("Hello, this is a test message.");
                Map<String, Object> result = llmService.orElseThrow(() ->
                        new IllegalStateException("LLM is enabled but LlmService is not available"))
                        .complete(req);
                String content = String.valueOf(result.getOrDefault("content", ""));
                String preview = content.length() > 100 ? content.substring(0, 100) : content;
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("message", "模型连接测试成功");
                body.put("provider", provider);
                body.put("model", model);
                body.put("response_preview", preview);
                return body;
            } catch (Exception e) {
                Map<String, Object> fail = new LinkedHashMap<>();
                fail.put("success", false);
                fail.put("message", "连接失败: " + e.getMessage());
                fail.put("detail", e.getMessage());
                fail.put("suggestion", "请检查 LLM_API_KEY / LLM_BASE_URL / LLM_MODEL 是否正确");
                return fail;
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "配置格式校验通过（当前 LLM 未启用，跳过真实连通性测试）");
        body.put("provider", provider);
        body.put("model", model);
        body.put("response_preview", "mock: LLM_ENABLED=false");
        body.put("suggestion", "设置 LLM_ENABLED=true 后可进行真实连通性测试");
        return body;
    }

    /** Replaceable mock: switch active model config (in-memory). */
    @PostMapping("/model/switch")
    public Map<String, Object> switchModel(@RequestBody Map<String, Object> modelConfig) {
        if (modelConfig == null || modelConfig.isEmpty()) {
            throw new IllegalArgumentException("modelConfig is required");
        }
        String provider = String.valueOf(modelConfig.getOrDefault("provider", "openai"));
        if (!ModelProvider.isValid(provider)) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "unsupported provider: " + provider);
            return fail;
        }
        Map<String, Object> next = new LinkedHashMap<>(modelConfig);
        next.putIfAbsent("provider", provider);
        activeModel.set(next);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "model switched");
        body.put("modelConfig", next);
        body.put("llmEnabled", properties.getLlm().isEnabled());
        return body;
    }

    private Map<String, Object> buildDefaultConfig() {
        Map<String, Object> m = new LinkedHashMap<>(defaultModel());
        m.putIfAbsent("baseUrl", System.getenv().getOrDefault("LLM_BASE_URL", ""));
        m.putIfAbsent("temperature", 0.3);
        m.putIfAbsent("maxTokens", 2048);
        m.putIfAbsent("thinking", false);
        return m;
    }

    private Map<String, Object> toModelRow(LlmUserConfig config, boolean isDefault, String providerName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", config.getProvider() + "-" + config.getModel());
        row.put("provider", config.getProvider());
        row.put("providerName", providerName);
        row.put("name", config.getModel());
        row.put("isDefault", isDefault);
        return row;
    }

    private static Map<String, Object> provider(String name, String label, boolean available) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("label", label);
        row.put("available", available);
        return row;
    }

    private static Map<String, Object> defaultModel() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", ModelProvider.CUSTOM.getValue());
        m.put("model", System.getenv().getOrDefault("LLM_MODEL", "gpt-4o-mini"));
        return m;
    }
}
