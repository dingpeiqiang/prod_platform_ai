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
     * 生成追问建议。
     *
     * @param question 原始问题
     * @param results  工具执行结果列表
     * @return 追问建议列表
     */
    List<String> suggestFollowUps(String question, List<ExecutionResult> results);
}