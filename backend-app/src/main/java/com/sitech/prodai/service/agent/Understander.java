package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;

import java.util.List;

/**
 * 理解层 - 听懂人话。
 * 将自然语言翻译为结构化查询计划。
 */
public interface Understander {

    /**
     * 理解用户问题，生成查询计划（单意图视角）。
     * <p>
     * 混合意图（intent 含 |）时返回首个可执行子计划，供单计划调用方（如非流式 process）
     * 保持兼容；多意图完整列表见 {@link #understandAll}。
     *
     * @param question 用户自然语言问题
     * @param context  会话上下文（多轮对话时携带）
     * @return 查询计划（翻译层的"中间语言"）
     */
    QueryPlan understand(String question, SessionContext context);

    /**
     * 理解用户问题，生成查询计划集合（多意图视角）。
     * <p>
     * 支持混合意图（LLM 用 {@code |} 拼接多意图/动作）：拆分成多个独立子计划，
     * 供编排层分别处理、分别作答，天然支持 N 个子意图且互不污染。
     *
     * @param question 用户自然语言问题
     * @param context  会话上下文（多轮对话时携带）
     * @return 查询计划列表（每个子意图一个）；空/无法解析时为空列表。
     */
    List<QueryPlan> understandAll(String question, SessionContext context);
}