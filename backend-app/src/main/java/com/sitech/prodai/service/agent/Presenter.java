package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.SessionContext;

import java.util.List;

/**
 * 表达层 - 翻译结果。
 * 将工具执行结果翻译为自然语言。
 */
public interface Presenter {

    /**
     * 将工具执行结果翻译为自然语言回答。
     *
     * @param question 原始问题
     * @param results  工具执行结果列表
     * @param context  会话上下文
     * @return 自然语言回答
     */
    String present(String question, List<ExecutionResult> results, SessionContext context);

    /**
     * 生成追问建议（任务链感知，方案 11.2 触点④：话术承接本场景白名单内的能力）。
     *
     * @param question 原始问题
     * @param results  工具执行结果列表
     * @param context  会话上下文（供场景分支守门；可为 null）
     * @return 追问建议列表
     */
    List<String> suggestFollowUps(String question, List<ExecutionResult> results, SessionContext context);
}