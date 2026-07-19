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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 通用消息表 —— 对齐 Python {@code app/models/chat_v2.py::ChatMessageV2}。
 * 核心字段，与业务解耦，业务扩展走 ChatMessageMetadata。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_cm_message_id", columnList = "message_id", unique = true),
        @Index(name = "idx_cm_session_id", columnList = "session_id")
})
@EntityListeners(AuditingEntityListener.class)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "message_id", unique = true, nullable = false, length = 64)
    private String messageId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @ManyToOne
    @JoinColumn(name = "session_id", referencedColumnName = "session_id", insertable = false, updatable = false)
    private ChatSession session;

    /** user / assistant / system */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** text / markdown / json / form */
    @Column(name = "content_type", length = 20)
    private String contentType = "text";

    @Column(name = "parent_id", length = 64)
    private String parentId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
