package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.dto.ChatCompletionRequest;
import com.sitech.prodai.domain.entity.LlmUserConfig;
import com.sitech.prodai.util.TokenCounter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "prodai.llm.enabled", havingValue = "true", matchIfMissing = false)
public class LlmService {

    private final ProdAiProperties properties;
    private final LlmConfigService configService;
    private final Optional<ModelRouter> modelRouter;
    private final Optional<TokenCounter> tokenCounter;
    private final ConcurrentHashMap<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    public LlmService(ProdAiProperties properties, LlmConfigService configService,
                      Optional<ModelRouter> modelRouter, Optional<TokenCounter> tokenCounter) {
        this.properties = properties;
        this.configService = configService;
        this.modelRouter = modelRouter;
        this.tokenCounter = tokenCounter;
    }

    /**
     * 获取 Token 计数器（供外部使用）。
     */
    public Optional<TokenCounter> getTokenCounter() {
        return tokenCounter;
    }

    public Map<String, Object> complete(ChatCompletionRequest request) {
        ensureEnabled();
        ChatClient client = getChatClient(request.getModelConfig());
        List<Message> messages = toMessages(request);
        OpenAiChatOptions options = buildOptions(request.getModelConfig());
        String content = client.prompt()
                .messages(messages)
                .options(options)
                .call()
                .content();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("content", content == null ? "" : content);
        body.put("runtime", "spring-ai");
        return body;
    }

    public String completePrompt(String prompt) {
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setPrompt(prompt);
        Map<String, Object> result = complete(req);
        return String.valueOf(result.getOrDefault("content", ""));
    }

    /**
     * 基于结构化消息列表完成 LLM 调用（支持多轮对话上下文）
     */
    public String completeMessages(String systemPrompt, List<Map<String, String>> history, String userMessage) {
        ensureEnabled();
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setSystemPrompt(systemPrompt);
        List<ChatCompletionRequest.ChatMessage> chatMessages = new ArrayList<>();
        if (history != null) {
            for (Map<String, String> h : history) {
                ChatCompletionRequest.ChatMessage m = new ChatCompletionRequest.ChatMessage();
                m.setRole(h.getOrDefault("role", "user"));
                m.setContent(h.getOrDefault("content", ""));
                chatMessages.add(m);
            }
        }
        ChatCompletionRequest.ChatMessage last = new ChatCompletionRequest.ChatMessage();
        last.setRole("user");
        last.setContent(userMessage);
        chatMessages.add(last);
        req.setMessages(chatMessages);
        Map<String, Object> result = complete(req);
        return String.valueOf(result.getOrDefault("content", ""));
    }

    /**
     * 流式输出：基于结构化消息列表（支持多轮对话上下文）
     */
    public Flux<String> streamChatText(String prompt) {
        ensureEnabled();
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setPrompt(prompt);
        ChatClient client = getChatClient(req.getModelConfig());
        List<Message> messages = toMessages(req);
        OpenAiChatOptions options = buildOptions(req.getModelConfig());
        return client.prompt()
                .messages(messages)
                .options(options)
                .stream()
                .content()
                .filter(text -> text != null && !text.isEmpty());
    }

    /**
     * 流式输出：基于系统提示 + 历史消息 + 用户消息（多轮对话）
     */
    public Flux<String> streamWithMessages(String systemPrompt, List<Map<String, String>> history, String userMessage) {
        ensureEnabled();
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setSystemPrompt(systemPrompt);
        List<ChatCompletionRequest.ChatMessage> chatMessages = new ArrayList<>();
        if (history != null) {
            for (Map<String, String> h : history) {
                ChatCompletionRequest.ChatMessage m = new ChatCompletionRequest.ChatMessage();
                m.setRole(h.getOrDefault("role", "user"));
                m.setContent(h.getOrDefault("content", ""));
                chatMessages.add(m);
            }
        }
        ChatCompletionRequest.ChatMessage last = new ChatCompletionRequest.ChatMessage();
        last.setRole("user");
        last.setContent(userMessage);
        chatMessages.add(last);
        req.setMessages(chatMessages);

        ChatClient client = getChatClient(req.getModelConfig());
        List<Message> messages = toMessages(req);
        OpenAiChatOptions options = buildOptions(req.getModelConfig());
        return client.prompt()
                .messages(messages)
                .options(options)
                .stream()
                .content()
                .filter(text -> text != null && !text.isEmpty());
    }

    public Flux<Map<String, Object>> streamEvents(ChatCompletionRequest request) {
        ensureEnabled();
        ChatClient client = getChatClient(request.getModelConfig());
        List<Message> messages = toMessages(request);
        OpenAiChatOptions options = buildOptions(request.getModelConfig());

        Flux<Map<String, Object>> start = Flux.just(event("text_start", null));
        Flux<Map<String, Object>> chunks = client.prompt()
                .messages(messages)
                .options(options)
                .stream()
                .content()
                .filter(text -> text != null && !text.isEmpty())
                .map(text -> event("text", text));
        Flux<Map<String, Object>> end = Flux.just(
                event("text_end", null),
                doneEvent()
        );

        return Flux.concat(start, chunks, end)
                .onErrorResume(ex -> Flux.just(
                        event("text_start", null),
                        event("text", "LLM 调用失败: " + ex.getMessage()),
                        event("text_end", null),
                        doneEvent()
                ));
    }

    private ChatClient getChatClient(Map<String, Object> modelConfig) {
        Map<String, Object> effectiveConfig = getEffectiveConfig(modelConfig);

        String baseUrl = getStringFromConfig(effectiveConfig, "base_url", "baseUrl",
                System.getenv().getOrDefault("LLM_BASE_URL", "https://api.openai.com"));
        String apiKey = getStringFromConfig(effectiveConfig, "api_key", "apiKey",
                System.getenv().getOrDefault("LLM_API_KEY", "sk-placeholder"));
        Boolean isFullUrl = effectiveConfig != null && parseBoolean(effectiveConfig.get("is_full_url"));

        String normalizedBaseUrl = normalizeBaseUrl(baseUrl, isFullUrl);

        String cacheKey = normalizedBaseUrl + "|" + apiKey;
        return clientCache.computeIfAbsent(cacheKey, key -> {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(30_000);
            requestFactory.setReadTimeout(120_000);

            RestClient.Builder restClientBuilder = RestClient.builder()
                    .requestFactory(requestFactory);

            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(normalizedBaseUrl)
                    .apiKey(apiKey)
                    .restClientBuilder(restClientBuilder)
                    .build();
            org.springframework.ai.openai.OpenAiChatModel model = org.springframework.ai.openai.OpenAiChatModel.builder()
                    .openAiApi(api)
                    .build();
            return ChatClient.create(model);
        });
    }

    private String normalizeBaseUrl(String baseUrl, boolean isFullUrl) {
        baseUrl = baseUrl.replaceAll("/v1/chat/completions$", "");
        baseUrl = baseUrl.replaceAll("/v1/completions$", "");
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        return baseUrl;
    }

    private Map<String, Object> getEffectiveConfig(Map<String, Object> modelConfig) {
        Map<String, Object> effective = new LinkedHashMap<>();

        if (modelConfig != null) {
            effective.putAll(modelConfig);
        }

        String apiKey = getStringFromConfig(effective, "api_key", "apiKey", null);
        String baseUrl = getStringFromConfig(effective, "base_url", "baseUrl", null);

        if ((apiKey == null || apiKey.isBlank()) || (baseUrl == null || baseUrl.isBlank())) {
            Optional<LlmUserConfig> activeDbConfig = configService.getActiveConfig();
            if (activeDbConfig.isPresent()) {
                LlmUserConfig config = activeDbConfig.get();
                if (apiKey == null || apiKey.isBlank()) {
                    effective.put("api_key", config.getApiKey());
                }
                if (baseUrl == null || baseUrl.isBlank()) {
                    effective.put("base_url", config.getBaseUrl());
                }
                if (!effective.containsKey("model")) {
                    effective.put("model", config.getModel());
                }
                if (!effective.containsKey("is_full_url")) {
                    effective.put("is_full_url", config.getIsFullUrl());
                }
                if (!effective.containsKey("temperature")) {
                    effective.put("temperature", config.getTemperature());
                }
                if (!effective.containsKey("max_tokens")) {
                    effective.put("max_tokens", config.getMaxTokens());
                }
            }
        }

        return effective;
    }

    private OpenAiChatOptions buildOptions(Map<String, Object> modelConfig) {
        Map<String, Object> effectiveConfig = getEffectiveConfig(modelConfig);

        // 检查是否有路由信息，使用 ModelRouter 选择最优模型
        String scene = modelConfig != null ? String.valueOf(modelConfig.get("_scene")) : null;
        String intentType = modelConfig != null ? String.valueOf(modelConfig.get("_intentType")) : null;
        int inputLength = modelConfig != null && modelConfig.containsKey("_inputLength")
                ? ((Number) modelConfig.get("_inputLength")).intValue() : 0;

        Map<String, Object> routedConfig = Map.of();
        if (modelRouter.isPresent() && (scene != null || intentType != null)) {
            routedConfig = modelRouter.get().route(scene, intentType, inputLength);
        }

        // 路由配置 > 用户配置 > 默认配置
        String model = getStringFromConfig(routedConfig, "model", "model", null);
        if (model == null || model.isBlank()) {
            model = getStringFromConfig(effectiveConfig, "model", "model",
                    System.getenv().getOrDefault("LLM_MODEL", "gpt-4o-mini"));
        }

        Double temperature = getDoubleFromConfig(routedConfig, "temperature", null);
        if (temperature == null) {
            temperature = getDoubleFromConfig(effectiveConfig, "temperature", 0.5);
        }

        OpenAiChatOptions options = new OpenAiChatOptions();
        options.setModel(model);
        options.setTemperature(temperature);
        return options;
    }

    private String getStringFromConfig(Map<String, Object> config, String keySnake, String keyCamel, String defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object value = config.get(keySnake);
        if (value == null || String.valueOf(value).isBlank()) {
            value = config.get(keyCamel);
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    private boolean parseBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim().toLowerCase());
    }

    private Double getDoubleFromConfig(Map<String, Object> config, String key, Double defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void ensureEnabled() {
        if (!properties.getLlm().isEnabled()) {
            throw new IllegalStateException(
                    "LLM is disabled. Set LLM_ENABLED=true and configure LLM_API_KEY / LLM_BASE_URL / LLM_MODEL.");
        }
    }

    private List<Message> toMessages(ChatCompletionRequest request) {
        List<Message> messages = new ArrayList<>();
        String system = request.getSystemPrompt();
        if (system == null || system.isBlank()) {
            system = properties.getLlm().getSystemPrompt();
        }
        if (system != null && !system.isBlank()) {
            messages.add(new SystemMessage(system));
        }

        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (ChatCompletionRequest.ChatMessage msg : request.getMessages()) {
                String role = msg.getRole() == null ? "user" : msg.getRole().toLowerCase();
                String content = msg.getContent() == null ? "" : msg.getContent();
                switch (role) {
                    case "system" -> messages.add(new SystemMessage(content));
                    case "assistant" -> messages.add(new AssistantMessage(content));
                    default -> messages.add(new UserMessage(content));
                }
            }
            return messages;
        }

        if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
            messages.add(new UserMessage(request.getPrompt()));
            return messages;
        }

        throw new IllegalArgumentException("messages or prompt is required");
    }

    private Map<String, Object> event(String type, String content) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        if (content != null) {
            event.put("content", content);
        }
        return event;
    }

    private Map<String, Object> doneEvent() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "done");
        event.put("intentType", "chat");
        event.put("isForm", false);
        return event;
    }
}