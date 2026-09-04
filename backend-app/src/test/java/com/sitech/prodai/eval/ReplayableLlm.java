package com.sitech.prodai.eval;

import com.sitech.prodai.exception.LlmConfigException;
import com.sitech.prodai.service.LlmService;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.Map;

/**
 * 录制-回放 LLM 替身：Mockito mock LlmService，
 * {@code completeMessages}/{@code completePrompt} 路由到 {@link EvalRecordingStore}。
 * <ul>
 *   <li>回放命中 → 返回录制响应（零网络调用）</li>
 *   <li>未命中 → live 模式真实执行（录制落盘）；回放模式抛 {@link ReplayMissException}
 *       （评测运行器据此跳过用例，CI 保持绿色）</li>
 * </ul>
 */
public final class ReplayableLlm {

    /** 回放未命中信号（评测运行器捕获后 skip 该用例）。 */
    public static class ReplayMissException extends RuntimeException {
        public ReplayMissException(String message) {
            super(message);
        }
    }

    private ReplayableLlm() {
    }

    /**
     * 构造评测用 LlmService 替身。
     *
     * @param store   录制回放存储
     * @param live    true=真实调用并录制；false=仅回放
     * @param caseId  当前用例 id（录制文件按用例归档）
     */
    public static LlmService create(EvalRecordingStore store, boolean live, String caseId) {
        LlmService mock = Mockito.mock(LlmService.class);
        Answer<String> messagesAnswer = invocation -> {
            String systemPrompt = invocation.getArgument(0);
            String userMessage = invocation.getArgument(2);
            String cached = store.replay(caseId, systemPrompt, userMessage);
            if (cached != null) {
                return cached;
            }
            if (!live) {
                throw new ReplayMissException("无录制且非 live: " + caseId);
            }
            LlmService real = createRealService();
            try {
                String response = real.completeMessages(systemPrompt, invocation.getArgument(1), userMessage);
                store.record(caseId, systemPrompt, userMessage, response);
                return response;
            } catch (LlmConfigException e) {
                throw new ReplayMissException("LLM 配置不可用: " + e.getMessage());
            } catch (RuntimeException e) {
                throw new ReplayMissException("LLM 调用失败: " + e.getMessage());
            }
        };
        Mockito.when(mock.completeMessages(Mockito.anyString(),
                        Mockito.<List<Map<String, String>>>any(), Mockito.anyString()))
                .thenAnswer(messagesAnswer);
        Mockito.when(mock.completePrompt(Mockito.anyString()))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(0);
                    String cached = store.replay(caseId, prompt, "");
                    if (cached != null) {
                        return cached;
                    }
                    if (!live) {
                        throw new ReplayMissException("无录制且非 live: " + caseId);
                    }
                    LlmService real = createRealService();
                    try {
                        String response = real.completePrompt(prompt);
                        store.record(caseId, prompt, "", response);
                        return response;
                    } catch (RuntimeException e) {
                        throw new ReplayMissException("LLM 调用失败: " + e.getMessage());
                    }
                });
        return mock;
    }

    /** live 模式真实服务构造（网关配置缺失时抛异常由上层转 skip）。 */
    private static LlmService createRealService() {
        var properties = new com.sitech.prodai.config.ProdAiProperties();
        properties.getLlm().setEnabled(true);
        return new LlmService(properties, java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty());
    }
}
