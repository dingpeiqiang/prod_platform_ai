package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 通用消息表 —— 对齐 Python {@code app/models/chat_v2.py::ChatMessageV2}。
 * 核心字段，与业务解耦，业务扩展走 ChatMessageMetadata。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_chat_messages")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("message_id")
    private String messageId;

    @TableField("session_id")
    private String sessionId;

    /** user / assistant / system */
    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    /** text / markdown / json / form */
    @TableField("content_type")
    private String contentType = "text";

    @TableField("parent_id")
    private String parentId;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
