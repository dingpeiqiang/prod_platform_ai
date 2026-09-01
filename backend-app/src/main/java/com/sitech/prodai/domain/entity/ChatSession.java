package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sitech.prodai.common.JsonTypeHandlers;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 通用会话表 —— 对齐 Python {@code app/models/chat_v2.py::ChatSessionV2}。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_chat_sessions", autoResultMap = true)
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("session_id")
    private String sessionId;

    @TableField("user_id")
    private String userId;

    @TableField("title")
    private String title;

    @TableField(value = "context_tags", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> contextTags;

    @TableField(value = "session_metadata", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> sessionMetadata;

    /** active / archived */
    @TableField("status")
    private String status = "active";

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
