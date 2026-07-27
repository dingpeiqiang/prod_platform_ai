package com.sitech.prodai.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        log.error("[GlobalExceptionHandler] 未捕获异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("internal_error", ex.getMessage()));
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error_code", code);
        body.put("message", message);
        return body;
    }
}
