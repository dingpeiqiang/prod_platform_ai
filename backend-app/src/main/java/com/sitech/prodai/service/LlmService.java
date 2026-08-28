package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.dto.ChatCompletionRequest;
import com.sitech.prodai.util.TokenCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.util.StringUtils;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "prodai.llm.enabled", havingValue = "true", matchIfMissing = false)
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final ProdAiProperties properties;
    private final Optional<ModelRouter> modelRouter;
    private final Optional<TokenCounter> tokenCounter;
    private final ConcurrentHashMap<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    /** 未显式配置输出预算时的默认值（DeepSeek 推理模型单次输出缺省会返回空 choices） */
    private static final int DEFAULT_MAX_TOKENS = 4096;

    /** 网关瞬时 403 权限错误的有限重试次数 */
    private static final int TRANSIENT_403_RETRIES = 2;
    /** 第一次重试的基础退避毫秒（按尝试次数线性放大） */
    private static final long TRANSIENT_403_BACKOFF_MS = 500L;

    public LlmService(ProdAiProperties properties,
                      Optional<ModelRouter> modelRouter, Optional<TokenCounter> tokenCounter) {
        this.properties = properties;
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

        // 网关对合法客户端可能偶发返回 403「当前租户已禁止该客户端访问」等瞬时权限错误
        // （同一 URL/Key/模型下时 200 时 403），此处做有限次退避重试，避免整条流直接失败。
        int attempts = TRANSIENT_403_RETRIES + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
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
            } catch (Exception e) {
                if (isTransientPermissionError(e) && attempt < attempts) {
                    log.warn("[LlmService] 网关瞬时 403 权限错误，第 {} 次重试: {}", attempt, e.getMessage());
                    sleepQuietly(TRANSIENT_403_BACKOFF_MS * attempt);
                    continue;
                }
                throw e;
            }
        }
        // 不可达：上面 for 循环内必然 return 或 throw
        throw new IllegalStateException("unreachable");
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
        appendUserMessage(chatMessages, userMessage);
        req.setMessages(chatMessages);
        Map<String, Object> result = complete(req);
        return String.valueOf(result.getOrDefault("content", ""));
    }

    /**
     * 追加当前用户消息，避免与历史末条重复。
     * 调用方约定：历史 history 不应包含当前用户消息（由本方法单独追加）；
     * 若调用方误把当前消息写入了历史（如 AgentOrchestrator 先 addHistoryEntry 再调用），
     * 这里做防御性去重，防止同一问题连续发送两次导致大模型返回空 choices。
     */
    private void appendUserMessage(List<ChatCompletionRequest.ChatMessage> chatMessages, String userMessage) {
        if (!chatMessages.isEmpty()) {
            ChatCompletionRequest.ChatMessage last = chatMessages.get(chatMessages.size() - 1);
            if ("user".equalsIgnoreCase(last.getRole())
                    && userMessage != null
                    && userMessage.equals(last.getContent())) {
                return;
            }
        }
        ChatCompletionRequest.ChatMessage last = new ChatCompletionRequest.ChatMessage();
        last.setRole("user");
        last.setContent(userMessage);
        chatMessages.add(last);
    }

    public Flux<Map<String, Object>> streamEvents(ChatCompletionRequest request) {
        ensureEnabled();
        Map<String, Object> effectiveConfig = getEffectiveConfig(request.getModelConfig());
        boolean streamEnabled = parseBoolean(effectiveConfig.get("stream_enabled"));

        // 模型配置关闭流式时，退化为一次性非流式调用，规避不支持 SSE 的中转网关报错
        if (!streamEnabled) {
            return Flux.concat(
                    Flux.just(event("text_start", null)),
                    Flux.fromIterable(nonStreamText(request)),
                    Flux.just(event("text_end", null), doneEvent())
            ).onErrorResume(ex -> Flux.just(
                    event("text_start", null),
                    event("text", "LLM 调用失败: " + ex.getMessage()),
                    event("text_end", null),
                    doneEvent()
            ));
        }

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

    /** 非流式调用：将完整结果拆分为单个 text 事件。 */
    private List<Map<String, Object>> nonStreamText(ChatCompletionRequest request) {
        Map<String, Object> body = complete(request);
        Object content = body.get("content");
        String text = content == null ? "" : String.valueOf(content);
        if (text.isEmpty()) {
            text = "LLM 未返回内容";
        }
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(event("text", text));
        return events;
    }

    private ChatClient getChatClient(Map<String, Object> modelConfig) {
        Map<String, Object> effectiveConfig = getEffectiveConfig(modelConfig);

        String baseUrl = getStringFromConfig(effectiveConfig, "base_url", "baseUrl",
                System.getenv().getOrDefault("LLM_BASE_URL", "https://api.openai.com"));
        String apiKey = getStringFromConfig(effectiveConfig, "api_key", "apiKey",
                System.getenv().getOrDefault("LLM_API_KEY", "sk-placeholder"));
        Boolean isFullUrl = effectiveConfig != null && parseBoolean(effectiveConfig.get("is_full_url"));

        // 鉴权方式：bearer（默认，Authorization: Bearer）| custom（auth_header 指定请求头，如网关要求的 token）。
        String authType = getStringFromConfig(effectiveConfig, "auth_type", "authType", "bearer");
        String authHeaderName = getStringFromConfig(effectiveConfig, "auth_header", "authHeader", "");
        boolean customAuth = "custom".equalsIgnoreCase(authType);

        String normalizedBaseUrl = normalizeBaseUrl(baseUrl, isFullUrl);

        String cacheKey = normalizedBaseUrl + "|" + apiKey + "|" + authType + "|" + authHeaderName;
        return clientCache.computeIfAbsent(cacheKey, key -> {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(30_000);
            requestFactory.setReadTimeout(120_000);

            RestClient.Builder restClientBuilder = RestClient.builder()
                    .requestFactory(requestFactory)
                    .requestInterceptor(new ClientHttpRequestInterceptor() {
                        @Override
                        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                                            ClientHttpRequestExecution execution) throws java.io.IOException {
                            String auth = request.getHeaders().getFirst("Authorization");
                            String masked = auth == null ? "<none>" : (auth.length() > 12
                                    ? auth.substring(0, 10) + "..." + auth.substring(auth.length() - 4) : "<hidden>");
                            String modelTag = extractModel(body);
                            log.info("[LlmService] 出站LLM请求 url={} auth={} model={} bodyLen={}",
                                    request.getURI(), masked, modelTag, body == null ? 0 : body.length);
                            ClientHttpResponse resp = execution.execute(request, body);
                            log.info("[LlmService] 出站LLM响应 status={} url={}",
                                    resp.getStatusCode(), request.getURI());
                            return resp;
                        }
                    });

            OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                    .baseUrl(normalizedBaseUrl)
                    .restClientBuilder(restClientBuilder);

            if (customAuth) {
                // 自定义检定头：通过 RestClient 默认头发送 token 等，且不附带 Bearer。
                if (StringUtils.hasText(authHeaderName)) {
                    restClientBuilder.defaultHeader(authHeaderName, apiKey);
                }
                apiBuilder.apiKey("");
            } else {
                apiBuilder.apiKey(apiKey);
            }

            OpenAiApi api = apiBuilder.build();
            org.springframework.ai.openai.OpenAiChatModel model = org.springframework.ai.openai.OpenAiChatModel.builder()
                    .openAiApi(api)
                    .build();
            return ChatClient.create(model);
        });
    }

    private String normalizeBaseUrl(String baseUrl, boolean isFullUrl) {
        baseUrl = baseUrl == null ? "" : baseUrl;
        baseUrl = baseUrl.replaceAll("/v1/chat/completions$", "");
        baseUrl = baseUrl.replaceAll("/v1/completions$", "");
        // Spring AI 的 OpenAI 兼容客户端会在 baseUrl 之后自动拼接 /v1/chat/completions，
        // 因此这里去掉末尾 /v1，避免生成 …/v1/v1/chat/completions 这类重复路径。
        if (!isFullUrl) {
            baseUrl = baseUrl.replaceAll("/v1$", "");
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        return baseUrl;
    }

    private Map<String, Object> getEffectiveConfig(Map<String, Object> modelConfig) {
        Map<String, Object> effective = new LinkedHashMap<>();

        // 请求显式命中了已配置的模型（按 name/model/id）→ 使用该模型的独立连接
        Map<String, Object> requested = resolveRequestedModel(modelConfig);
        if (requested != null) {
            effective.putAll(requested);
            // 已由 name 解析出真实模型与连接，仅应用 modelConfig 的非 model 覆盖参数，
            // 避免用配置别名（如 deepseek-reasoner）覆盖真实模型 ID。
            if (modelConfig != null) {
                for (Map.Entry<String, Object> e : modelConfig.entrySet()) {
                    if ("model".equals(e.getKey())) {
                        continue;
                    }
                    effective.put(e.getKey(), e.getValue());
                }
            }
            return effective;
        }

        // 否则使用 default-model 指向的默认生效模型
        effective.putAll(resolveDefaultModelConfig());

        // 显式传入的 modelConfig 仍是最高优先级（独立连接场景：模型名 + 完整连接参数）
        if (modelConfig != null) {
            effective.putAll(modelConfig);
        }

        return effective;
    }

    /**
     * 解析请求命中的已配置模型。modelConfig 中携带的 {@code model} / {@code name} /
     * {@code id} 若匹配 prodai.llm.models 中某项的 name 或 model，则返回该模型的独立连接；
     * 否则返回 null（表示使用默认生效模型）。
     */
    private Map<String, Object> resolveRequestedModel(Map<String, Object> modelConfig) {
        if (modelConfig == null) {
            return null;
        }
        String ref = null;
        Object m = modelConfig.get("model");
        if (m != null && !String.valueOf(m).isBlank()) {
            ref = String.valueOf(m);
        }
        if (ref == null) {
            Object n = modelConfig.get("name");
            if (n != null && !String.valueOf(n).isBlank()) {
                ref = String.valueOf(n);
            }
        }
        if (ref == null) {
            Object id = modelConfig.get("id");
            if (id != null && !String.valueOf(id).isBlank()) {
                ref = String.valueOf(id);
            }
        }
        if (ref == null) {
            return null;
        }
        return resolveModelConfigByName(ref);
    }

    /** 按 name 或 model 在 prodai.llm.models 中查找并返回该模型的独立连接配置；未命中返回空 Map。 */
    private Map<String, Object> resolveModelConfigByName(String name) {
        if (name == null || name.isBlank()) {
            return new LinkedHashMap<>();
        }
        ProdAiProperties.Llm llm = properties.getLlm();
        if (llm.getModels() == null || llm.getModels().isEmpty()) {
            return new LinkedHashMap<>();
        }
        for (ProdAiProperties.LlmModelConfig c : llm.getModels()) {
            if (name.equals(c.getName()) || name.equals(c.getModel())) {
                return toModelConfigMap(c);
            }
        }
        return new LinkedHashMap<>();
    }

    /** 解析 default-model 指向的默认生效模型配置（未配置多模型时返回空 map）。 */
    private Map<String, Object> resolveDefaultModelConfig() {
        ProdAiProperties.Llm llm = properties.getLlm();
        if (llm.getModels() == null || llm.getModels().isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (StringUtils.hasText(llm.getDefaultModel())) {
            Map<String, Object> byName = resolveModelConfigByName(llm.getDefaultModel());
            if (!byName.isEmpty()) {
                return byName;
            }
        }
        // default-model 未指定或未命中时，取第一条作为默认
        return toModelConfigMap(llm.getModels().get(0));
    }

    /** 单条模型配置 → 连接参数字典（与 LlmService 消费的 key 对齐）。 */
    private Map<String, Object> toModelConfigMap(ProdAiProperties.LlmModelConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (StringUtils.hasText(c.getApiKey())) {
            m.put("api_key", c.getApiKey());
        }
        if (StringUtils.hasText(c.getBaseUrl())) {
            m.put("base_url", c.getBaseUrl());
        }
        m.put("model", c.getModel());
        m.put("is_full_url", c.isFullUrl());
        m.put("temperature", c.getTemperature());
        m.put("max_tokens", c.getMaxTokens());
        if (c.getMaxCompletionTokens() != null) {
            m.put("max_completion_tokens", c.getMaxCompletionTokens());
        }
        m.put("thinking", c.isThinking());
        m.put("stream_enabled", c.isStreamEnabled());
        m.put("auth_type", c.getAuthType());
        m.put("auth_header", c.getAuthHeader());
        return m;
    }

    /** 暴露给控制器等：返回可用的模型列表（含默认标记），供前端模型选择与工作流 LLM 节点使用。 */
    public List<Map<String, Object>> listAvailableModels() {
        ProdAiProperties.Llm llm = properties.getLlm();
        if (llm.getModels() == null || llm.getModels().isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        String defaultName = llm.getDefaultModel();
        for (ProdAiProperties.LlmModelConfig c : llm.getModels()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", StringUtils.hasText(c.getName()) ? c.getName() : c.getModel());
            row.put("name", c.getModel());
            row.put("providerName", StringUtils.hasText(c.getName()) ? c.getName() : c.getModel());
            row.put("isDefault", StringUtils.hasText(defaultName) && defaultName.equals(c.getName()));
            list.add(row);
        }
        return list;
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

        // 输出 token 预算：路由配置 > 用户配置 > 默认值
        Integer maxTokens = getIntFromConfig(routedConfig, "max_tokens", null);
        if (maxTokens == null) {
            maxTokens = getIntFromConfig(effectiveConfig, "max_tokens", null);
        }
        Integer maxCompletionTokens = getIntFromConfig(routedConfig, "max_completion_tokens", null);
        if (maxCompletionTokens == null) {
            maxCompletionTokens = getIntFromConfig(effectiveConfig, "max_completion_tokens", null);
        }

        OpenAiChatOptions options = new OpenAiChatOptions();
        options.setModel(model);
        options.setTemperature(temperature);

        // 是否推理模型：优先由模型配置的 thinking 开关决定（custom/自定义模型由用户配置），
        // 否则回退到模型名匹配（deepseek-reasoner 类）。推理模型必须用 max_completion_tokens，
        // 否则非流式调用会返回空 choices。
        boolean reasoning = isReasoningModel(model, effectiveConfig);
        if (reasoning) {
            int limit = maxCompletionTokens != null ? maxCompletionTokens
                    : (maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS);
            options.setMaxCompletionTokens(limit);
        } else {
            options.setMaxTokens(maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS);
        }
        return options;
    }

    /**
     * 推理模型判断：优先参考模型配置 {@code thinking} 开关（向 custom 等自定义模型开放，由用户配置驱动），
     * 其次回退到模型名匹配（deepseek-reasoner 类）。输出前需要单独推理 token 配额（max_completion_tokens）。
     */
    private boolean isReasoningModel(String model, Map<String, Object> effectiveConfig) {
        if (effectiveConfig != null && effectiveConfig.containsKey("thinking")) {
            Object thinking = effectiveConfig.get("thinking");
            if (thinking instanceof Boolean) {
                return (Boolean) thinking;
            }
            if (thinking != null) {
                return Boolean.parseBoolean(String.valueOf(thinking));
            }
        }
        if (model == null) {
            return false;
        }
        String m = model.toLowerCase(Locale.ROOT);
        return m.contains("reasoner") || m.contains("thinking");
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

    /**
     * 判定是否为网关瞬时 403 权限错误（「当前租户已禁止该客户端访问」类）。
     * 该错误在 API Key/模型合法且本机直接调用成功时仍会偶发出现，属于网关瞬时状态，
     * 需做有限重试；其余 4xx/5xx 不做透明重试。
     */
    private boolean isTransientPermissionError(Exception e) {
        String msg = e == null ? "" : String.valueOf(e.getMessage());
        return msg.contains("403") && (msg.contains("permission_error") || msg.contains("当前租户已禁止"));
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String extractModel(byte[] body) {
        if (body == null || body.length == 0) {
            return "<empty>";
        }
        try {
            String json = new String(body, java.nio.charset.StandardCharsets.UTF_8);
            int idx = json.indexOf("\"model\"");
            if (idx < 0) {
                return "<no-model-field>";
            }
            int colon = json.indexOf(':', idx);
            int q1 = json.indexOf('"', colon);
            int q2 = json.indexOf('"', q1 + 1);
            if (q1 >= 0 && q2 > q1) {
                return json.substring(q1 + 1, q2);
            }
            return "<unparsed>";
        } catch (Exception e) {
            return "<parse-error>";
        }
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

    private Integer getIntFromConfig(Map<String, Object> config, String key, Integer defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
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