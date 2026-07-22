package com.sitech.prodai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 意图识别 Prompt 模板管理器。
 *
 * <p>从 classpath:prompts/ 加载模板文件，支持热替换（重新加载）。
 * 模板使用 {{variable}} 占位符，由 render() 方法填充。
 */
@Service
public class IntentPromptManager {

    private static final Logger log = LoggerFactory.getLogger(IntentPromptManager.class);
    private static final String PROMPT_DIR = "prompts/";
    private static final String INTENT_PROMPT_FILE = "intent_recognition_prompt.txt";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadPrompt(INTENT_PROMPT_FILE);
    }

    /**
     * 渲染意图识别 prompt。
     *
     * @param messagesText   对话上下文
     * @param lastUserMessage 用户最新消息
     * @param ontologiesInfo  本体信息
     * @param scene           场景标识
     * @return 渲染后的 prompt
     */
    public String renderIntentPrompt(String messagesText, String lastUserMessage,
                                      String ontologiesInfo, String scene) {
        String template = getPrompt(INTENT_PROMPT_FILE);
        String sceneHint = scene == null || scene.isBlank() ? "" : "（当前前端场景：" + scene + "）";
        String historyBlock = "";
        if (messagesText != null && !messagesText.isBlank()) {
            historyBlock = "\n\n对话上下文（按时间顺序，最新在最后）：\n" + messagesText;
        }

        return template.replace("{{sceneHint}}", sceneHint)
                .replace("{{historyBlock}}", historyBlock)
                .replace("{{lastUserMessage}}", lastUserMessage != null ? lastUserMessage : "");
    }

    /**
     * 获取指定 prompt 模板内容。
     */
    public String getPrompt(String fileName) {
        return cache.computeIfAbsent(fileName, this::loadPrompt);
    }

    /**
     * 重新加载所有模板（可用于热替换）。
     */
    public void reload() {
        cache.clear();
        loadPrompt(INTENT_PROMPT_FILE);
        log.info("[IntentPromptManager] 所有 prompt 模板已重新加载");
    }

    private String loadPrompt(String fileName) {
        String path = PROMPT_DIR + fileName;
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.warn("[IntentPromptManager] prompt 模板不存在: {}", path);
                return "";
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String content = reader.lines().collect(Collectors.joining("\n"));
                log.debug("[IntentPromptManager] 加载 prompt 模板: {} ({} chars)", path, content.length());
                return content;
            }
        } catch (Exception e) {
            log.error("[IntentPromptManager] 加载 prompt 模板失败: {}", path, e);
            return "";
        }
    }
}
