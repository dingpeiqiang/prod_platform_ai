package com.sitech.prodai.service.flow.event;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 引擎节点级事件（智聊重设计 W1-1）——固定流程引擎对外可观测的最小事件单元。
 * <p>
 * 由 {@code FlowEngineService} 在每个节点事务提交后发布（异步投递，失败仅告警，
 * 引擎可靠性 &gt; 通知可靠性）。对话侧经 {@code ChatFlowProgressBridge} 翻译为
 * SSE {@code flow_progress} 事件；运维侧可用于执行面板实时点亮。
 * <p>
 * 事件类型（type）与发布点：
 * <ul>
 *   <li>{@code NODE_STARTED}：节点开始执行（executeNode 调用前）</li>
 *   <li>{@code NODE_COMPLETED}：节点执行成功（node_log 落库后）</li>
 *   <li>{@code NODE_FAILED}：节点执行失败（含单次尝试失败与终态失败）</li>
 *   <li>{@code BRANCH_TAKEN}：condition 节点命中分支（branch 落库后）</li>
 *   <li>{@code SUSPENDED}：human 节点挂起（resume_token 生成后）</li>
 *   <li>{@code EXECUTION_COMPLETED} / {@code EXECUTION_FAILED}：执行实例终态</li>
 * </ul>
 */
public class FlowNodeEvent {

    /** 事件类型常量。 */
    public static final String NODE_STARTED = "node_started";
    public static final String NODE_COMPLETED = "node_completed";
    public static final String NODE_FAILED = "node_failed";
    public static final String BRANCH_TAKEN = "branch_taken";
    public static final String SUSPENDED = "suspended";
    public static final String EXECUTION_COMPLETED = "execution_completed";
    public static final String EXECUTION_FAILED = "execution_failed";

    private final String type;
    private final String executionId;
    private final String workflowCode;
    private final String nodeId;
    private final String nodeType;
    private final String nodeName;
    private final String status;
    private final int attempt;
    private final String branchTaken;
    private final String errorMessage;
    private final Map<String, Object> output;
    private final Map<String, Object> formSpec;
    private final LocalDateTime occurredAt;

    private FlowNodeEvent(Builder builder) {
        this.type = builder.type;
        this.executionId = builder.executionId;
        this.workflowCode = builder.workflowCode;
        this.nodeId = builder.nodeId;
        this.nodeType = builder.nodeType;
        this.nodeName = builder.nodeName;
        this.status = builder.status;
        this.attempt = builder.attempt;
        this.branchTaken = builder.branchTaken;
        this.errorMessage = builder.errorMessage;
        this.output = builder.output;
        this.formSpec = builder.formSpec;
        this.occurredAt = LocalDateTime.now();
    }

    public static Builder of(String type, String executionId, String workflowCode) {
        return new Builder(type, executionId, workflowCode);
    }

    public String getType() {
        return type;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getWorkflowCode() {
        return workflowCode;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempt() {
        return attempt;
    }

    public String getBranchTaken() {
        return branchTaken;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public Map<String, Object> getFormSpec() {
        return formSpec;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    /** 链式构建器（测试与引擎内部组装用）。 */
    public static final class Builder {
        private final String type;
        private final String executionId;
        private final String workflowCode;
        private String nodeId;
        private String nodeType;
        private String nodeName;
        private String status;
        private int attempt = 1;
        private String branchTaken;
        private String errorMessage;
        private Map<String, Object> output = Map.of();
        private Map<String, Object> formSpec;

        private Builder(String type, String executionId, String workflowCode) {
            this.type = type;
            this.executionId = executionId;
            this.workflowCode = workflowCode;
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder nodeType(String nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder nodeName(String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder attempt(int attempt) {
            this.attempt = attempt;
            return this;
        }

        public Builder branchTaken(String branchTaken) {
            this.branchTaken = branchTaken;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder output(Map<String, Object> output) {
            this.output = output == null ? Map.of() : output;
            return this;
        }

        public Builder formSpec(Map<String, Object> formSpec) {
            this.formSpec = formSpec;
            return this;
        }

        public FlowNodeEvent build() {
            return new FlowNodeEvent(this);
        }
    }
}
