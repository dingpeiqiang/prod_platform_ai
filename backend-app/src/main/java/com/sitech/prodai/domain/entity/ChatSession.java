package com.sitech.prodai.domain.entity;

import com.sitech.prodai.common.JsonConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 通用会话表 —— 对齐 Python {@code app/models/chat_v2.py::ChatSessionV2}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pd_ai_chat_sessions", indexes = {
        @Index(name = "idx_cs_session_id", columnList = "session_id", unique = true),
        @Index(name = "idx_cs_user_id", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "session_id", unique = true, nullable = false, length = 64)
    private String sessionId;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "context_tags", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonListConverter.class)
    private List<Object> contextTags;

    @Column(name = "session_metadata", columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    private Map<String, Object> sessionMetadata;

    /** active / archived */
    @Column(name = "status", length = 20)
    private String status = "active";

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
