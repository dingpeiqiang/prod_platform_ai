package com.sitech.prodai.domain.entity;

import com.sitech.prodai.common.JsonConverters;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
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
 * 场景主表 —— 对齐 Python {@code app/models/scene.py::Scene}。
 *
 * <p>树形结构：center（中心） → business（业务） → scene（场景）。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scenes", indexes = {
        @Index(name = "idx_scene_code", columnList = "scene_code", unique = true),
        @Index(name = "idx_scene_type", columnList = "type"),
        @Index(name = "idx_scene_parent_id", columnList = "parent_id"),
        @Index(name = "idx_scene_prompt_code", columnList = "prompt_code")
})
@EntityListeners(AuditingEntityListener.class)
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "scene_code", unique = true, nullable = false, length = 100)
    private String sceneCode;

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

    /** center / business / scene */
    @Column(name = "type", nullable = false, length = 20)
    private String type = "scene";

    @Column(name = "parent_id")
    private Integer parentId;

    @ManyToOne
    @JoinColumn(name = "parent_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Scene parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<Scene> children;

    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL)
    private List<SceneHistory> history;

    /** 通用配置字段，存储中心域/业务域的特定信息 */
    @Column(name = "config", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = JsonConverters.JsonMapConverter.class)
    private Map<String, Object> config;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
