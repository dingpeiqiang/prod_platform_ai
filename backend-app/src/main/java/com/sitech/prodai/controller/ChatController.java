package com.sitech.prodai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.dto.ChatCompletionRequest;
import com.sitech.prodai.service.LlmService;
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

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final ProdAiProperties properties;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicReference<Map<String, Object>> activeModel = new AtomicReference<>(defaultModel());

    public ChatController(LlmService llmService, ObjectMapper objectMapper, ProdAiProperties properties) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostMapping("/completion")
    public Map<String, Object> completion(@RequestBody ChatCompletionRequest request) {
        return llmService.complete(request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatCompletionRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        Flux<Map<String, Object>> events = llmService.streamEvents(request);

        executor.execute(() -> {
            try {
                events.toStream().forEach(event -> {
                    try {
                        String json = objectMapper.writeValueAsString(event);
                        emitter.send(SseEmitter.event().data(json));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.complete();
            } catch (Exception ex) {
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
                provider("openai", "OpenAI Compatible", true),
                provider("azure", "Azure OpenAI", false),
                provider("local", "Local / Mock", !properties.getLlm().isEnabled())
        ));
        body.put("active", activeModel.get());
        body.put("llmEnabled", properties.getLlm().isEnabled());
        return body;
    }

    /** Replaceable mock: switch active model config (in-memory). */
    @PostMapping("/model/switch")
    public Map<String, Object> switchModel(@RequestBody Map<String, Object> modelConfig) {
        if (modelConfig == null || modelConfig.isEmpty()) {
            throw new IllegalArgumentException("modelConfig is required");
        }
        String provider = String.valueOf(modelConfig.getOrDefault("provider", "openai"));
        if (!List.of("openai", "azure", "local").contains(provider)) {
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

    private static Map<String, Object> provider(String name, String label, boolean available) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("label", label);
        row.put("available", available);
        return row;
    }

    private static Map<String, Object> defaultModel() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", "openai");
        m.put("model", System.getenv().getOrDefault("LLM_MODEL", "gpt-4o-mini"));
        return m;
    }
}
