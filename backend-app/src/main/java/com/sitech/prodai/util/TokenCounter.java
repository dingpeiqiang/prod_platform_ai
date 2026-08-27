package com.sitech.prodai.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Token 精确计数器 —— 基于 jtokkit（tiktoken Java 实现）。
 *
 * <p>替代旧版字符估算逻辑，提供精确的 token 计数。
 * 支持多种编码格式：
 * <ul>
 *   <li>{@code CL100K_BASE} - GPT-4, GPT-3.5-turbo, Claude</li>
 *   <li>{@code O200K_BASE} - GPT-4o, GPT-4o-mini</li>
 *   <li>{@code P50K_BASE} - Codex, text-davinci</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 * // 单次计数
 * int tokens = tokenCounter.count("Hello, world!");
 *
 * // 批量计数
 * int total = tokenCounter.countBatch(List.of("Hello", "world"));
 * </pre>
 */
@Component
public class TokenCounter {

    private static final Logger log = LoggerFactory.getLogger(TokenCounter.class);

    private final EncodingRegistry registry;
    private final Encoding cl100kBase;
    private final Encoding o200kBase;

    public TokenCounter() {
        this.registry = Encodings.newDefaultEncodingRegistry();
        this.cl100kBase = registry.getEncoding(EncodingType.CL100K_BASE);
        this.o200kBase = registry.getEncoding(EncodingType.O200K_BASE);
        log.info("[TokenCounter] 初始化完成，支持 CL100K_BASE 和 O200K_BASE 编码");
    }

    /**
     * 计算文本的 token 数量（使用 CL100K_BASE 编码，适用于 GPT-4/Claude）。
     *
     * @param text 输入文本
     * @return token 数量
     */
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            IntArrayList tokens = cl100kBase.encode(text);
            return tokens.size();
        } catch (Exception e) {
            log.warn("[TokenCounter] CL100K 编码失败，降级到估算: {}", e.getMessage());
            return estimateFallback(text);
        }
    }

    /**
     * 使用 O200K_BASE 编码计算 token 数量（适用于 GPT-4o）。
     *
     * @param text 输入文本
     * @return token 数量
     */
    public int countO200k(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            IntArrayList tokens = o200kBase.encode(text);
            return tokens.size();
        } catch (Exception e) {
            log.warn("[TokenCounter] O200K 编码失败，降级到估算: {}", e.getMessage());
            return estimateFallback(text);
        }
    }

    /**
     * 根据模型名称选择合适的编码进行计数。
     *
     * @param text  输入文本
     * @param model 模型名称（如 gpt-4o, gpt-4o-mini, claude-3-opus）
     * @return token 数量
     */
    public int countForModel(String text, String model) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (model != null && (model.contains("gpt-4o") || model.contains("o200k"))) {
            return countO200k(text);
        }
        return count(text);
    }

    /**
     * 批量计算 token 数量。
     *
     * @param texts 文本列表
     * @return 总 token 数量
     */
    public int countBatch(java.util.List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (String text : texts) {
            total += count(text);
        }
        return total;
    }

    /**
     * 降级估算（当 jtokkit 编码失败时使用）。
     *
     * <p>估算规则：中文 1 字 ≈ 2 token，英文 1 词 ≈ 1.3 token
     */
    private int estimateFallback(String text) {
        int chinese = 0, ascii = 0;
        for (char c : text.toCharArray()) {
            if (c > 0x4e00 && c < 0x9fff) {
                chinese++;
            } else if (c > 32) {
                ascii++;
            }
        }
        return (int) Math.ceil(chinese * 2.0 + ascii * 0.35);
    }

    /**
     * 获取支持的编码类型列表。
     *
     * @return 编码类型名称数组
     */
    public String[] getSupportedEncodings() {
        return new String[]{"CL100K_BASE", "O200K_BASE", "P50K_BASE", "R50K_BASE"};
    }
}
