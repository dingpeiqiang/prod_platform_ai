package com.sitech.prodai.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 统一响应体 —— 对齐 Python backend 的 {@code {success, data, message}} 契约。
 *
 * <p>Python 侧 SuccessResponse / ErrorResponse：
 * <pre>
 * class SuccessResponse: success=True, data=None
 * class ErrorResponse:   success=False, message=str, code=Optional[str]
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;
    private String code;
    private List<String> errors;

    public ApiResponse() {
    }

    public ApiResponse(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }

    public static <T> ApiResponse<T> fail(String message, String code) {
        ApiResponse<T> resp = new ApiResponse<>(false, null, message);
        resp.code = code;
        return resp;
    }

    public static <T> ApiResponse<T> fail(String message, List<String> errors) {
        ApiResponse<T> resp = new ApiResponse<>(false, null, message);
        resp.errors = errors;
        return resp;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
