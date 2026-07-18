package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.dto.ChatCompletionRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    private final ChatClient chatClient;
    private final ProdAiProperties properties;

    public LlmService(ChatClient.Builder chatClientBuilder, ProdAiProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
    }

    public Map<String, Object> complete(ChatCompletionRequest request) {
        ensureEnabled();
        String content = chatClient.prompt()
                .messages(toMessages(request))
                .call()
                .content();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("content", content == null ? "" : content);
        body.put("runtime", "spring-ai");
        return body;
    }

    public Flux<Map<String, Object>> streamEvents(ChatCompletionRequest request) {
        ensureEnabled();
        List<Message> messages = toMessages(request);

        Flux<Map<String, Object>> start = Flux.just(event("text_start", null));
        Flux<Map<String, Object>> chunks = chatClient.prompt()
                .messages(messages)
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

    private void ensureEnabled() {
        if (!properties.getLlm().isEnabled()) {
            throw new IllegalStateException(
                    "LLM is disabled. Set LLM_ENABLED=true and configure LLM_API_KEY / LLM_BASE_URL / LLM_MODEL.");
        }
        String key = System.getenv().getOrDefault("LLM_API_KEY", "");
        if (key.isBlank() || "sk-placeholder".equals(System.getProperty("spring.ai.openai.api-key", ""))) {
            // soft check: allow placeholder when enabled=true for local wiring tests against real env
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
