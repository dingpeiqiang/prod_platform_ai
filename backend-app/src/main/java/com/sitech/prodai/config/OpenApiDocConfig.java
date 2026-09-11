package com.sitech.prodai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置 —— 后台管理中心「接口管理」模块。
 * <p>
 * 自动扫描所有 @RestController 生成 OpenAPI 3 文档：
 * <ul>
 *   <li>Swagger UI：/swagger-ui.html</li>
 *   <li>OpenAPI JSON：/v3/api-docs</li>
 * </ul>
 * 全局注入 JWT Bearer 认证，Swagger UI 右上角 Authorize 填登录接口返回的 token 即可调试受保护接口。
 */
@Configuration
public class OpenApiDocConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI prodPlatformOpenApi() {
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("AI 原生产商品助手平台 API")
                        .description("后台管理中心 · 接口管理：项目全部 REST API 接口文档，"
                                + "由 SpringDoc 自动扫描 Controller 注解生成。"
                                + "调试受保护接口请先通过 /api/v1/auth/login 获取 token，"
                                + "再点击右上角 Authorize 填入。")
                        .version("0.1.0"))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new io.swagger.v3.oas.models.security.SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("填入登录接口返回的 JWT token（不带 Bearer 前缀）")))
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
