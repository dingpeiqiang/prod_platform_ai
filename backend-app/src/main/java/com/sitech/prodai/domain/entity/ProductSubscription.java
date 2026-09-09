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
 * 商品变更订阅（方案 §6-C3，对应缺口 C5）：用户对单个商品的变更订阅登记。
 * <p>登记入口：product_change_alert 工具 subscribe 动作（服务端从登录态取用户，
 * 不接受前端透传订阅人）；变更检测命中订阅商品时由 ChangeDetectService 产生提醒。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_product_subscriptions")
public class ProductSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订阅人登录名（服务端登录态，LLM/前端不可透传） */
    @TableField("subscriber")
    private String subscriber;

    /** 订阅商品编码 */
    @TableField("offering_id")
    private String offeringId;

    /** 订阅时商品名称快照（提醒文案直接可读） */
    @TableField("offering_name")
    private String offeringName;

    /** 状态：active / cancelled */
    @TableField("status")
    private String status = "active";

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
