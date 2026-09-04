package com.sitech.prodai.exception;

/**
 * 大模型配置类异常（api_key 缺失/无效、网关认证失败、base_url 错误等）。
 * <p>
 * 与"大模型返回为空"（偶发空 choices，可重试）区分开：该类错误重试无意义，
 * 需要向用户透出可行动的修复指引（如在管理页录入 api_key）。
 * 全局异常处理器按 503 + error_code=llm_config_error 透传 message。
 */
public class LlmConfigException extends RuntimeException {

    public LlmConfigException(String message) {
        super(message);
    }

    public LlmConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
