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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产商品运营处置工单（稽核/归因闭环回写）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "pd_ai_ops_work_orders", autoResultMap = true)
public class OpsWorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("work_order_id")
    private String workOrderId;

    @TableField("title")
    private String title;

    @TableField("offering_id")
    private String offeringId;

    @TableField("offering_name")
    private String offeringName;

    @TableField("summary")
    private String summary;

    @TableField(value = "actions", typeHandler = JsonTypeHandlers.JsonListTypeHandler.class)
    private List<Object> actions = new ArrayList<>();

    @TableField("status")
    private String status = "open";

    @TableField("source")
    private String source;

    /** 来源会话 ID：用于研发助手消息窗口按会话聚合展示商品配置工单 */
    @TableField("session_id")
    private String sessionId;

    @TableField("hypo_mode")
    private String hypoMode;

    @TableField(value = "payload", typeHandler = JsonTypeHandlers.JsonMapTypeHandler.class)
    private Map<String, Object> payload = new LinkedHashMap<>();

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
