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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 场景版本历史表 —— 对齐 Python {@code app/models/scene.py::SceneHistory}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scene_history", indexes = {
        @Index(name = "idx_sh_scene_id", columnList = "scene_id"),
        @Index(name = "idx_sh_scene_code", columnList = "scene_code")
})
@EntityListeners(AuditingEntityListener.class)
public class SceneHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "scene_id", nullable = false)
    private Integer sceneId;

    @ManyToOne
    @JoinColumn(name = "scene_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Scene scene;

    @Column(name = "scene_code", nullable = false, length = 100)
    private String sceneCode;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "scene_name", nullable = false, length = 200)
    private String sceneName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "keywords", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonListConverter.class)
    private List<Object> keywords;

    @Column(name = "priority")
    private Integer priority = 10;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "prompt_code", length = 100)
    private String promptCode;

    @Column(name = "type", nullable = false, length = 20)
    private String type = "scene";

    @Column(name = "parent_id")
    private Integer parentId;

    @Column(name = "config", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    private Map<String, Object> config;

    @Column(name = "change_note", columnDefinition = "TEXT")
    private String changeNote;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
