package com.sitech.prodai.controller;

import com.sitech.prodai.config.JwtAuthFilter;
import com.sitech.prodai.service.ApiWorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 接口管理 · Postman 式请求工作区 API。
 *
 * <p>为后台管理中心「接口管理」模块提供已保存请求（请求集合）与请求历史的持久化能力，
 * 支撑在线调试中的保存、收藏、历史回看等交互。
 */
@Tag(name = "接口管理 · 请求工作区", description = "Postman 式接口调试：已保存请求（集合）与请求历史的增删改查")
@RestController
@RequestMapping("/api/v1/api-workspace")
public class ApiWorkspaceController {

    private final ApiWorkspaceService apiWorkspaceService;

    public ApiWorkspaceController(ApiWorkspaceService apiWorkspaceService) {
        this.apiWorkspaceService = apiWorkspaceService;
    }

    @Operation(summary = "已保存请求列表", description = "返回当前用户的已保存请求，可按 collection_name 过滤")
    @GetMapping("/requests")
    public Map<String, Object> listRequests(HttpServletRequest request,
                                            @RequestParam(required = false) String collection_name) {
        return apiWorkspaceService.listSavedRequests(currentOwner(request), collection_name);
    }

    @Operation(summary = "集合名称列表", description = "返回当前用户已有的请求集合名（含默认集合）")
    @GetMapping("/collections")
    public Map<String, Object> listCollections(HttpServletRequest request) {
        return apiWorkspaceService.listCollections(currentOwner(request));
    }

    @Operation(summary = "保存请求", description = "body: name/method/url/headers/params/body/body_type/collection_name/description")
    @PostMapping("/requests")
    public Map<String, Object> createRequest(HttpServletRequest request,
                                             @RequestBody(required = false) Map<String, Object> body) {
        return apiWorkspaceService.createSavedRequest(currentOwner(request), body == null ? Map.of() : body);
    }

    @Operation(summary = "更新已保存请求", description = "按 id 更新请求定义")
    @PutMapping("/requests/{id}")
    public Map<String, Object> updateRequest(HttpServletRequest request,
                                             @PathVariable Long id,
                                             @RequestBody(required = false) Map<String, Object> body) {
        return apiWorkspaceService.updateSavedRequest(currentOwner(request), id, body == null ? Map.of() : body);
    }

    @Operation(summary = "删除已保存请求", description = "按 id 删除请求定义")
    @DeleteMapping("/requests/{id}")
    public Map<String, Object> deleteRequest(HttpServletRequest request, @PathVariable Long id) {
        return apiWorkspaceService.deleteSavedRequest(currentOwner(request), id);
    }

    @Operation(summary = "请求历史", description = "按时间倒序返回当前用户的请求历史，limit 默认 50")
    @GetMapping("/history")
    public Map<String, Object> listHistory(HttpServletRequest request,
                                           @RequestParam(defaultValue = "50") int limit) {
        return apiWorkspaceService.listHistory(currentOwner(request), limit);
    }

    @Operation(summary = "记录请求历史", description = "body: method/url/headers/params/body/body_type/status/success/duration_ms/response_body/error_message")
    @PostMapping("/history")
    public Map<String, Object> createHistory(HttpServletRequest request,
                                             @RequestBody(required = false) Map<String, Object> body) {
        return apiWorkspaceService.createHistory(currentOwner(request), body == null ? Map.of() : body);
    }

    @Operation(summary = "清空请求历史", description = "删除当前用户的全部请求历史")
    @DeleteMapping("/history")
    public Map<String, Object> clearHistory(HttpServletRequest request) {
        return apiWorkspaceService.clearHistory(currentOwner(request));
    }

    @Operation(summary = "删除单条历史", description = "按 id 删除一条请求历史")
    @DeleteMapping("/history/{id}")
    public Map<String, Object> deleteHistory(HttpServletRequest request, @PathVariable Long id) {
        return apiWorkspaceService.deleteHistory(currentOwner(request), id);
    }

    @Operation(summary = "导出历史调用详情", description = "将某条请求历史导出为 TXT 文本下载")
    @GetMapping("/history/{id}/export")
    public ResponseEntity<byte[]> exportHistory(HttpServletRequest request, @PathVariable Long id) {
        byte[] content = apiWorkspaceService.exportHistoryDetail(currentOwner(request), id);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        String filename = "api-call-" + id + ".txt";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.TEXT_PLAIN)
                .body(content);
    }

    /** 读取当前登录用户名（未开启鉴权时可能为空）。 */
    private String currentOwner(HttpServletRequest request) {
        return JwtAuthFilter.currentUsername(request);
    }
}
