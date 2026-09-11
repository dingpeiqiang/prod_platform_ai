package com.sitech.prodai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "对话补全请求体：prompt（单轮）与 messages（多轮）二选一，messages 优先")
public class ChatCompletionRequest {

    @Schema(description = "多轮对话消息列表（与 messages 二选一，多轮对话优先）")
    private List<ChatMessage> messages;

    @Schema(description = "单轮对话文本（简单场景直接传 prompt）", example = "帮我查询上月滞销商品")
    private String prompt;

    @Schema(description = "系统提示词（可选）：注入角色设定与输出约束")
    private String systemPrompt;

    @Schema(description = "模型配置（可选）：provider/model/api_key/base_url/temperature 等；不传时使用后台「模型管理」激活配置")
    private Map<String, Object> modelConfig;

    @Schema(description = "业务场景标识（可选）：rd 研发 / ops 运营 / query 查询")
    private String scene;

    @Schema(description = "意图类型标识（可选）：由上层意图识别注入，用于路由与埋点")
    private String intentType;

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public Map<String, Object> getModelConfig() {
        return modelConfig;
    }

    public void setModelConfig(Map<String, Object> modelConfig) {
        this.modelConfig = modelConfig;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getIntentType() {
        return intentType;
    }

    public void setIntentType(String intentType) {
        this.intentType = intentType;
    }

    @Schema(description = "对话消息")
    public static class ChatMessage {

        @Schema(description = "角色：system / user / assistant", example = "user")
        private String role;

        @Schema(description = "消息内容")
        private String content;

        public ChatMessage() {}
        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
