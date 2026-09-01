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
 * 消息 KV 扩展表 —— 对齐 Python {@code app/models/chat_v2.py::ChatMessageMetadata}。
 * 插入式业务字段，与消息核心字段解耦。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_chat_message_metadata")
public class ChatMessageMetadata {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("message_id")
    private String messageId;

    @TableField("meta_key")
    private String metaKey;

    @TableField("value")
    private String value;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
