package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;

/**
 * 理解层 - 听懂人话。
 * 将自然语言翻译为结构化查询计划。
 */
public interface Understander {

    /**
     * 理解用户问题，生成查询计划。
     *
     * @param question 用户自然语言问题
     * @param context  会话上下文（多轮对话时携带）
     * @return 查询计划（翻译层的"中间语言"）
     */
    QueryPlan understand(String question, SessionContext context);
}