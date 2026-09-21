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
 * 接口管理 · 请求历史（类似 Postman 的 History）。
 *
 * <p>每次在「接口调试」中发送请求后记录一条，保存请求与响应快照，便于回看与重发。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_api_request_history")
public class ApiRequestHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起用户（登录名）。 */
    @TableField("owner")
    private String owner;

    @TableField("method")
    private String method;

    @TableField("url")
    private String url;

    /** Header 列表 JSON：[{name, value, enabled}] */
    @TableField("headers_json")
    private String headersJson;

    /** 查询参数列表 JSON：[{name, value, enabled}] */
    @TableField("params_json")
    private String paramsJson;

    @TableField("body")
    private String body;

    @TableField("body_type")
    private String bodyType = "none";

    /** HTTP 状态码，请求未到达服务器时为 null。 */
    @TableField("status")
    private Integer status;

    /** 是否成功（2xx）。 */
    @TableField("success")
    private Boolean success = false;

    /** 耗时（毫秒）。 */
    @TableField("duration_ms")
    private Long durationMs;

    /** 响应体文本快照（截断存储）。 */
    @TableField("response_body")
    private String responseBody;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
