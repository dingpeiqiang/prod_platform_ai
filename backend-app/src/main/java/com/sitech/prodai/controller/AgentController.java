package com.sitech.prodai.controller;

import com.sitech.prodai.service.agent.AgentOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 翻译层 API 入口。
 * <p>
 * 统一入口：POST /api/v1/agent/chat
 * 所有自然语言查询通过翻译层处理。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentOrchestrator orchestrator;

    public AgentController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
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

        if (question == null || question.isBlank() || "null".equals(question)) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", "question is required");
            return error;
        }

        log.info("[AgentController] 收到翻译请求: question={}, sessionId={}", question, sessionId);

        // 处理翻译流程
        Map<String, Object> result = orchestrator.process(question, sessionId);

        long elapsed = System.currentTimeMillis() - startTime;
        result.put("success", true);
        result.put("elapsed_ms", elapsed);

        log.info("[AgentController] 翻译完成: sessionId={}, elapsed={}ms", result.get("session_id"), elapsed);

        return result;
    }
}