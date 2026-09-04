package com.sitech.prodai.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户表 —— 认证与用户信息存储。
 * <p>
 * password_hash 格式：{@code salt hex + ":" + SHA-256(salt + password) hex}。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pd_ai_users")
public class User {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("username")
    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("display_name")
    private String displayName;

    /** user / admin */
    @TableField("role")
    private String role = "user";

    @TableField("is_enabled")
    private Integer isEnabled = 1;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;
}
