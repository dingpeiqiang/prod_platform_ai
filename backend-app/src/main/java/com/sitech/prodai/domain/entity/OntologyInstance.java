package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sitech.prodai.common.JsonTypeHandlers;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本体实例表（表单提交 / 配置草稿持久化）。
 * KV 数据以单 JSON TEXT 列 data_json 存储（语义等价于 KV 子表）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_ontology_instance", autoResultMap = true)
public class OntologyInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("ontology_code")
    private String ontologyCode;

    @TableField("user_id")
    private String userId;

    @TableField("session_id")
    private String sessionId;

    @TableField("status")
    private String status;

    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    /** KV 数据以 JSON TEXT 存储（原 pd_ai_ontology_instance_data 子表） */
    @TableField(value = "data_json", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, String> data = new LinkedHashMap<>();

    public void setData(Map<String, Object> input) {
        this.data = new LinkedHashMap<>();
        if (input != null) {
            input.forEach((k, v) -> this.data.put(k, v == null ? null : String.valueOf(v)));
        }
    }
}
