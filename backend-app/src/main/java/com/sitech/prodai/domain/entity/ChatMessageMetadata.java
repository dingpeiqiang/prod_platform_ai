package com.sitech.prodai.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 消息 KV 扩展表 —— 对齐 Python {@code app/models/chat_v2.py::ChatMessageMetadata}。
 * 插入式业务字段，与消息核心字段解耦。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "chat_message_metadata", indexes = {
        @Index(name = "idx_cmm_message_id", columnList = "message_id"),
        @Index(name = "idx_cmm_meta_key", columnList = "meta_key")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_message_key", columnNames = {"message_id", "meta_key"})
})
@EntityListeners(AuditingEntityListener.class)
public class ChatMessageMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    @ManyToOne
    @JoinColumn(name = "message_id", referencedColumnName = "message_id", insertable = false, updatable = false)
    private ChatMessage message;

    @Column(name = "meta_key", nullable = false, length = 100)
    private String metaKey;

    @Column(name = "value", columnDefinition = "TEXT")
    private String value;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
