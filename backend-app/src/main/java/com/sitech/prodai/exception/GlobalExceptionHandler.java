package com.sitech.prodai.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("[GlobalExceptionHandler] bad_request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(error("bad_request", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.error("[GlobalExceptionHandler] service_unavailable: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error("service_unavailable", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("validation failed");
        log.warn("[GlobalExceptionHandler] validation_error: {}", message);
        return ResponseEntity.badRequest().body(error("validation_error", message));
    }

    @ExceptionHandler({
            HttpMediaTypeNotSupportedException.class,
            MultipartException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<Map<String, Object>> handleMultipart(Exception ex) {
        log.error("[GlobalExceptionHandler] multipart/upload 异常: {}", ex.getMessage(), ex);
        String message = ex.getMessage();
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            message = "上传 Content-Type 不正确，请使用 multipart/form-data（勿强制 application/json）";
        } else if (ex instanceof MaxUploadSizeExceededException) {
            message = "文件过大，超出上传限制";
        } else if (message == null || message.isBlank()) {
            message = "文件上传失败";
        } else if (message.contains("Required part") || message.contains("not present")) {
            message = "缺少上传字段 file";
        }
        return ResponseEntity.badRequest().body(error("upload_error", message));
    }

    /**
     * 数据访问层异常统一映射（含表不存在、连接失败等）。
     * 原始异常信息（表名/SQL）仅保留在日志中，透传给前端的为业务可读文案 + requestId，
     * 避免英文堆栈直接暴露给用户，也方便运维凭 requestId 对账。
     */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessResource(DataAccessResourceFailureException ex) {
        log.error("[GlobalExceptionHandler] 数据库连接异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error("db_unavailable", "数据服务暂不可用，请稍后重试或联系管理员", requestId()));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(org.springframework.dao.DataAccessException ex) {
        String userMessage = resolveDbUserMessage(ex);
        log.error("[GlobalExceptionHandler] 数据访问异常（{}）: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(error("db_error", userMessage, requestId()));
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<Map<String, Object>> handleCompletion(CompletionException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        log.error("[GlobalExceptionHandler] 异步任务完成异常: {}", cause.getMessage(), cause);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("async_error", "异步处理失败: " + cause.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(IOException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("Broken pipe") || msg.contains("reset")) {
            log.warn("[GlobalExceptionHandler] SSE 客户端断开: {}", msg);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("sse_error", "SSE 连接已中断"));
        }
        log.error("[GlobalExceptionHandler] IO 异常: {}", msg, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("io_error", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        // 数据层异常（表不存在/SQL 语法错等）在到达此处前多已被 DataAccessException 分支捕获，
        // 这里做最后兜底：识别数据库关键字段错误，避免英文堆栈透传给前端
        if (isDatabaseError(ex)) {
            String userMessage = resolveDbUserMessage(ex);
            log.error("[GlobalExceptionHandler] 未捕获数据库异常（{}）: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return ResponseEntity.internalServerError()
                    .body(error("db_error", userMessage, requestId()));
        }
        log.error("[GlobalExceptionHandler] 未捕获异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("internal_error", ex.getMessage()));
    }

    private boolean isDatabaseError(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            if (t instanceof org.springframework.dao.DataAccessException
                    || t instanceof java.sql.SQLException
                    || t.getClass().getName().startsWith("org.hibernate.exception")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /** 按异常根因生成业务可读文案；表不存在等细节只进日志，不透传前端 */
    private String resolveDbUserMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String detail = String.valueOf(root.getMessage()).toLowerCase();
        if (detail.contains("doesn't exist") || detail.contains("does not exist")
                || detail.contains("no such table") || detail.contains("unknown table")) {
            log.error("[GlobalExceptionHandler] 表不存在，请检查 sql/ 目录 DDL 是否已执行: {}", root.getMessage());
            return "数据存储异常：数据库表缺失，请执行 sql/01_full_schema_ddl.sql 初始化后重试";
        }
        if (detail.contains("access denied")) {
            return "数据存储异常：数据库账号无权限，请检查连接配置";
        }
        if (detail.contains("public key retrieval") || detail.contains("communications link")
                || detail.contains("connection refused") || detail.contains("connect timed out")) {
            return "数据服务暂不可用，请确认数据库已启动后重试";
        }
        return "数据存储异常，请稍后重试或联系管理员";
    }

    /** 关联 RequestLoggingFilter 写入 MDC 的 requestId，便于日志对账 */
    private String requestId() {
        String id = org.slf4j.MDC.get("requestId");
        return id != null ? id : "";
    }

    private Map<String, Object> error(String code, String message) {
        return error(code, message, null);
    }

    private Map<String, Object> error(String code, String message, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error_code", code);
        body.put("message", message);
        if (requestId != null && !requestId.isBlank()) {
            body.put("request_id", requestId);
        }
        return body;
    }
}
