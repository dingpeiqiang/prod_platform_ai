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
 * 接口管理 · 已保存请求（类似 Postman 的请求集合条目）。
 *
 * <p>持久化用户在「接口调试」中保存的请求定义：方法、地址、Header、查询参数、
 * 请求体与备注，可按集合（collection_name）分组。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_api_saved_request")
public class ApiSavedRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户（登录名），为空表示共享。 */
    @TableField("owner")
    private String owner;

    /** 集合/分组名称，默认 default。 */
    @TableField("collection_name")
    private String collectionName;

    @TableField("name")
    private String name;

    @TableField("method")
    private String method = "GET";

    @TableField("url")
    private String url;

    /** Header 列表 JSON：[{name, value, enabled}] */
    @TableField("headers_json")
    private String headersJson;

    /** 查询参数列表 JSON：[{name, value, enabled}] */
    @TableField("params_json")
    private String paramsJson;

    /** 请求体 JSON 文本。 */
    @TableField("body")
    private String body;

    /** 请求体类型：none / json / text / form。 */
    @TableField("body_type")
    private String bodyType = "none";

    @TableField("description")
    private String description;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
