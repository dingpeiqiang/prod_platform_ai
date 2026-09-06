package com.sitech.prodai.service.flow.event;

/**
 * 引擎节点级事件监听器（智聊重设计 W1-1）。
 * <p>
 * 实现方约定：
 * <ul>
 *   <li>引擎侧异步投递，实现方自身也应快速返回（如仅做内存入队），长耗时逻辑自行转线程；</li>
 *   <li>实现方抛出的任何异常会被引擎吞掉（log.warn），绝不影响状态机推进——
 *       引擎可靠性 &gt; 通知可靠性；</li>
 *   <li>事件在节点事务提交后发布，接收方看到的节点状态均为已落库状态。</li>
 * </ul>
 * <p>
 * 精细回调由实现方依据 {@link FlowNodeEvent#getType()} 分派
 * （{@code node_started / node_completed / node_failed / branch_taken / suspended /
 * execution_completed / execution_failed}），接口不按类型展开方法，
 * 便于后续新增事件类型零改动。
 */
@FunctionalInterface
public interface FlowEventListener {

    /**
     * 节点/执行级事件回调。
     *
     * @param event 引擎节点事件（含 execution_id 审计锚点，可用于反查 node_logs）
     */
    void onEvent(FlowNodeEvent event);
}
