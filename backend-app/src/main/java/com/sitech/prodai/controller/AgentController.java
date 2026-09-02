package com.sitech.prodai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.agent.AgentOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 翻译层 API 入口。
 * <p>
 * 统一入口：POST /api/v1/agent/chat
 * 流式入口：POST /api/v1/agent/chat/stream（SSE）
 * 所有自然语言查询通过翻译层处理。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public AgentController(AgentOrchestrator orchestrator, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    /**
     * 翻译层统一入口。
     * <p>
     * 任何自然语言查询 → 翻译层 → 知识库/推理引擎 → 结果 → 翻译层 → 自然语言回答。
     *
     * @param request 请求体：{ "question": "...", "session_id": "..." }
     * @return 翻译结果：{ session_id, report, evidence, conclusion, suggested_follow_ups, ... }
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, Object> request) {
        long startTime = System.currentTimeMillis();

        // 参数校验
        String question = request != null ? String.valueOf(request.getOrDefault("question", "")) : "";
        String sessionId = request != null ? String.valueOf(request.getOrDefault("session_id", "")) : "";
        Map<String, Object> params = extractParams(request);
        String scene = request != null && request.get("scene") != null
                ? String.valueOf(request.get("scene")) : null;

        if (question == null || question.isBlank() || "null".equals(question)) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", "question is required");
            return error;
        }

        log.info("[AgentController] 收到翻译请求: question={}, sessionId={}, scene={}", question, sessionId, scene);

        // 处理翻译流程
        Map<String, Object> result = orchestrator.process(question, sessionId, params, scene);

        long elapsed = System.currentTimeMillis() - startTime;
        result.put("success", true);
        result.put("elapsed_ms", elapsed);

        log.info("[AgentController] 翻译完成: sessionId={}, elapsed={}ms", result.get("session_id"), elapsed);

        return result;
    }

    /**
     * 流式翻译入口（SSE，设计文档 5.2 节）。
     * <p>
     * 事件按执行阶段实时推送：thinking → tool（每工具 running/done）→ text* → text_done → done。
     * 与一次性 /chat 共用同一 AgentOrchestrator 编排，编排层边执行边回调本入口即时下发，
     * 客户端可在工具仍在执行时便看到思考过程（真流式，非攒齐后突发发送）。
     *
     * @param request 请求体：{ "question": "...", "session_id": "..." }
     * @return SSE 事件流（Accept: text/event-stream）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.completeWithError(e));
        // emitter 状态守卫：complete/completeWithError 后再 send 会抛
        // "ResponseBodyEmitter has already completed"，导致 error 事件发不出去、前端无感知
        final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean(false);

        String question = request != null ? String.valueOf(request.getOrDefault("question", "")) : "";
        String sessionId = request != null ? String.valueOf(request.getOrDefault("session_id", "")) : "";
        Map<String, Object> params = extractParams(request);
        String scene = request != null && request.get("scene") != null
                ? String.valueOf(request.get("scene")) : null;

        if (question == null || question.isBlank() || "null".equals(question)) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(toJson(Map.of("error", "question is required"))));
            } catch (Exception ignored) {
                // 连接已断开
            }
            emitter.complete();
            return emitter;
        }

        log.info("[AgentController] 收到流式翻译请求: question={}, sessionId={}, scene={}", question, sessionId, scene);

        Executors.newCachedThreadPool().execute(() -> {
            try {
                orchestrator.processStream(question, sessionId, params, scene, (name, data) -> {
                    try {
                        emitter.send(SseEmitter.event().name(name).data(toJson(data)));
                    } catch (Exception e) {
                        // 客户端断开等发送失败 → 置关闭标记并中止流水线，触发外层收尾
                        closed.set(true);
                        throw new IllegalStateException("SSE 发送失败: " + e.getMessage(), e);
                    }
                });
                if (closed.compareAndSet(false, true)) {
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("[AgentController] 流式翻译失败", e);
                if (!closed.get()) {
                    try {
                        emitter.send(SseEmitter.event().name("error")
                                .data(toJson(Map.of(
                                        "error", e.getMessage() == null ? "服务异常" : e.getMessage(),
                                        "request_id", currentRequestId()))));
                    } catch (Exception ignored) {
                        // 连接已断开
                    }
                }
                if (closed.compareAndSet(false, true)) {
                    emitter.complete();
                }
            }
        });

        return emitter;
    }

    /** 关联 RequestLoggingFilter 写入 MDC 的 requestId，便于前端报错时与后端日志对账 */
    private String currentRequestId() {
        String id = MDC.get("requestId");
        return id != null ? id : "";
    }

    /**
     * 提取请求体中的结构化补参（CLARIFY 澄清回传）。仅接受简单 KV，
     * 拒绝 question/session_id 等保留键，防止覆盖会话元数据。
     */
    private Map<String, Object> extractParams(Map<String, Object> request) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (request == null) {
            return out;
        }
        Object raw = request.get("params");
        if (!(raw instanceof Map<?, ?> map)) {
            return out;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null) continue;
            String key = String.valueOf(e.getKey());
            if ("question".equals(key) || "session_id".equals(key) || "sessionId".equals(key)) {
                continue;
            }
            out.put(key, e.getValue());
        }
        return out;
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }
}