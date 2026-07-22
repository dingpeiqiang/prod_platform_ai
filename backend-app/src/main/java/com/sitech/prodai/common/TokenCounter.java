package com.sitech.prodai.common;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token 计数器 —— 基于 jtokkit (tiktoken Java 实现) 精确计算 token 数。
 *
 * <p>支持的编码格式：
 * <ul>
 *   <li>{@code cl100k_base} - GPT-4, GPT-3.5-Turbo, Embeddings</li>
 *   <li>{@code p50k_base} - Codex, text-davinci-002, text-davinci-003</li>
 *   <li>{@code r50k_base} - GPT-3 models</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 * // 精确计算
 * int tokens = TokenCounter.count("Hello, world!");
 *
 * // 指定编码格式
 * int tokens = TokenCounter.count("Hello, world!", "cl100k_base");
 *
 * // 估算（快速，基于字符数）
 * int tokens = TokenCounter.estimate("Hello, world!");
 * </pre>
 */
public class TokenCounter {

    private static final Logger log = LoggerFactory.getLogger(TokenCounter.class);

    private static final EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();

    // 缓存常用编码
    private static final Encoding cl100kBase = registry.getEncoding(EncodingType.CL100K_BASE);
    private static final Encoding p50kBase = registry.getEncoding(EncodingType.P50K_BASE);
    private static final Encoding r50kBase = registry.getEncoding(EncodingType.R50K_BASE);

    private TokenCounter() {
        // 工具类，禁止实例化
    }

    /**
     * 使用 cl100k_base 编码计算 token 数（推荐用于 GPT-4/GPT-3.5）。
     *
     * @param text 输入文本
     * @return token 数量
     */
    public static int count(String text) {
        return count(text, "cl100k_base");
    }

    /**
     * 使用指定编码格式计算 token 数。
     *
     * @param text     输入文本
     * @param encoding 编码格式名称（cl100k_base, p50k_base, r50k_base）
     * @return token 数量
     */
    public static int count(String text, String encoding) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        try {
            Encoding enc = getEncoding(encoding);
            IntArrayList tokens = enc.encode(text);
            return tokens.size();
        } catch (Exception e) {
            log.warn("[TokenCounter] 编码失败，降级到估算: {}", e.getMessage());
            return estimate(text);
        }
    }

    /**
     * 快速估算 token 数（基于字符统计，无需编码）。
     *
     * <p>估算规则：
     * <ul>
     *   <li>中文字符：1 字 ≈ 2 token</li>
     *   <li>ASCII 字符：1 字符 ≈ 0.35 token</li>
     *   <li>其他字符：1 字符 ≈ 1 token</li>
     * </ul>
     *
     * @param text 输入文本
     * @return 估算的 token 数量
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chinese = 0;
        int ascii = 0;
        int other = 0;

        for (char c : text.toCharArray()) {
            if (c > 0x4e00 && c < 0x9fff) {
                chinese++;
            } else if (c > 32 && c < 127) {
                ascii++;
            } else if (c > 32) {
                other++;
            }
        }

        return (int) Math.ceil(chinese * 2.0 + ascii * 0.35 + other * 1.0);
    }

    /**
     * 批量计算 token 数。
     *
     * @param texts 文本列表
     * @return token 总数
     */
    public static int countBatch(java.util.List<String> texts) {
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
     * 获取编码实例。
     */
    private static Encoding getEncoding(String encoding) {
        return switch (encoding.toLowerCase()) {
            case "p50k_base", "p50k" -> p50kBase;
            case "r50k_base", "r50k" -> r50kBase;
            default -> cl100kBase;
        };
    }

    /**
     * 获取支持的编码格式列表。
     *
     * @return 编码格式数组
     */
    public static String[] supportedEncodings() {
        return new String[]{"cl100k_base", "p50k_base", "r50k_base"};
    }

    /**
     * 获取默认编码格式名称。
     *
     * @return "cl100k_base"
     */
    public static String defaultEncoding() {
        return "cl100k_base";
    }
}
