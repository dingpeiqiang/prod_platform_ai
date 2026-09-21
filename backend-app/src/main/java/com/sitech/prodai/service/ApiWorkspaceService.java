package com.sitech.prodai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.domain.entity.ApiRequestHistory;
import com.sitech.prodai.domain.entity.ApiSavedRequest;
import com.sitech.prodai.mapper.ApiRequestHistoryMapper;
import com.sitech.prodai.mapper.ApiSavedRequestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口管理 · Postman 式请求工作区服务。
 *
 * <p>负责「已保存请求（请求集合）」与「请求历史」的持久化，配套后台管理中心「接口管理」模块的
 * 在线调试能力。请求/响应中的 Header、查询参数、请求体均以 JSON 文本存储，前端负责解析渲染。
 */
@Service
public class ApiWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(ApiWorkspaceService.class);

    /** 响应体快照最大保存长度，避免超长响应撑爆存储。 */
    private static final int MAX_RESPONSE_BODY_CHARS = 20000;
    /** 每组保留的历史条数上限，超出后清理最旧记录。 */
    private static final int MAX_HISTORY_PER_OWNER = 200;
    /** 默认集合名。 */
    private static final String DEFAULT_COLLECTION = "default";

    private final ApiSavedRequestMapper savedRequestMapper;
    private final ApiRequestHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;

    public ApiWorkspaceService(ApiSavedRequestMapper savedRequestMapper,
                               ApiRequestHistoryMapper historyMapper,
                               ObjectMapper objectMapper) {
        this.savedRequestMapper = savedRequestMapper;
        this.historyMapper = historyMapper;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 已保存请求（请求集合）
    // ------------------------------------------------------------------

    /** 查询已保存请求列表，可按集合过滤。 */
    public Map<String, Object> listSavedRequests(String owner, String collectionName) {
        LambdaQueryWrapper<ApiSavedRequest> wrapper = new LambdaQueryWrapper<ApiSavedRequest>()
                .orderByDesc(ApiSavedRequest::getUpdatedAt)
                .orderByDesc(ApiSavedRequest::getId);
        if (owner != null && !owner.isBlank()) {
            wrapper.eq(ApiSavedRequest::getOwner, owner);
        }
        if (collectionName != null && !collectionName.isBlank()) {
            wrapper.eq(ApiSavedRequest::getCollectionName, collectionName);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (ApiSavedRequest entity : savedRequestMapper.selectList(wrapper)) {
            items.add(toSavedDto(entity));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", items);
        body.put("total", items.size());
        return body;
    }

    /** 查询全部集合名（含默认集合）。 */
    public Map<String, Object> listCollections(String owner) {
        LambdaQueryWrapper<ApiSavedRequest> wrapper = new LambdaQueryWrapper<>();
        if (owner != null && !owner.isBlank()) {
            wrapper.eq(ApiSavedRequest::getOwner, owner);
        }
        List<ApiSavedRequest> rows = savedRequestMapper.selectList(wrapper);
        List<String> names = new ArrayList<>();
        names.add(DEFAULT_COLLECTION);
        for (ApiSavedRequest row : rows) {
            String name = row.getCollectionName();
            if (name != null && !name.isBlank() && !names.contains(name)) {
                names.add(name);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", names);
        return body;
    }

    /** 新增已保存请求。 */
    @Transactional
    public Map<String, Object> createSavedRequest(String owner, Map<String, Object> payload) {
        String name = str(payload.get("name"));
        if (name.isBlank()) {
            return Map.of("success", false, "message", "name 不能为空");
        }
        ApiSavedRequest entity = new ApiSavedRequest();
        entity.setOwner(owner);
        applySavedFields(entity, payload);
        savedRequestMapper.insert(entity);
        return Map.of("success", true, "data", toSavedDto(entity));
    }

    /** 更新已保存请求。 */
    @Transactional
    public Map<String, Object> updateSavedRequest(String owner, Long id, Map<String, Object> payload) {
        ApiSavedRequest entity = savedRequestMapper.selectById(id);
        if (entity == null) {
            return Map.of("success", false, "message", "请求不存在");
        }
        if (!canAccess(entity.getOwner(), owner)) {
            return Map.of("success", false, "message", "无权操作该请求");
        }
        applySavedFields(entity, payload);
        if (entity.getOwner() == null) {
            entity.setOwner(owner);
        }
        savedRequestMapper.updateById(entity);
        return Map.of("success", true, "data", toSavedDto(entity));
    }

    /** 删除已保存请求。 */
    @Transactional
    public Map<String, Object> deleteSavedRequest(String owner, Long id) {
        ApiSavedRequest entity = savedRequestMapper.selectById(id);
        if (entity == null) {
            return Map.of("success", false, "message", "请求不存在");
        }
        if (!canAccess(entity.getOwner(), owner)) {
            return Map.of("success", false, "message", "无权操作该请求");
        }
        savedRequestMapper.deleteById(id);
        return Map.of("success", true);
    }

    // ------------------------------------------------------------------
    // 请求历史
    // ------------------------------------------------------------------

    /** 查询请求历史，支持按 owner 过滤与条数限制。 */
    public Map<String, Object> listHistory(String owner, int limit) {
        int capped = Math.max(1, Math.min(limit, 500));
        LambdaQueryWrapper<ApiRequestHistory> wrapper = new LambdaQueryWrapper<ApiRequestHistory>()
                .orderByDesc(ApiRequestHistory::getCreatedAt)
                .orderByDesc(ApiRequestHistory::getId)
                .last("LIMIT " + capped);
        if (owner != null && !owner.isBlank()) {
            wrapper.eq(ApiRequestHistory::getOwner, owner);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (ApiRequestHistory entity : historyMapper.selectList(wrapper)) {
            items.add(toHistoryDto(entity));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", items);
        body.put("total", items.size());
        return body;
    }

    /** 记录一条请求历史。 */
    @Transactional
    public Map<String, Object> createHistory(String owner, Map<String, Object> payload) {
        ApiRequestHistory entity = new ApiRequestHistory();
        entity.setOwner(owner);
        entity.setMethod(defaultIfBlank(str(payload.get("method")), "GET").toUpperCase());
        entity.setUrl(str(payload.get("url")));
        entity.setHeadersJson(jsonText(payload.get("headers")));
        entity.setParamsJson(jsonText(payload.get("params")));
        entity.setBody(strOrNull(payload.get("body")));
        entity.setBodyType(defaultIfBlank(str(payload.get("body_type")), "none"));
        entity.setStatus(intOrNull(payload.get("status")));
        entity.setSuccess(boolValue(payload.get("success")));
        entity.setDurationMs(longOrNull(payload.get("duration_ms")));
        entity.setResponseBody(truncate(strOrNull(payload.get("response_body")), MAX_RESPONSE_BODY_CHARS));
        entity.setErrorMessage(strOrNull(payload.get("error_message")));
        entity.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(entity);
        evictHistory(owner);
        return Map.of("success", true, "id", entity.getId());
    }

    /** 清空某用户的请求历史。 */
    @Transactional
    public Map<String, Object> clearHistory(String owner) {
        LambdaQueryWrapper<ApiRequestHistory> wrapper = new LambdaQueryWrapper<>();
        if (owner != null && !owner.isBlank()) {
            wrapper.eq(ApiRequestHistory::getOwner, owner);
        }
        int deleted = historyMapper.delete(wrapper);
        return Map.of("success", true, "deleted", deleted);
    }

    /** 删除单条历史。 */
    @Transactional
    public Map<String, Object> deleteHistory(String owner, Long id) {
        ApiRequestHistory entity = historyMapper.selectById(id);
        if (entity == null) {
            return Map.of("success", false, "message", "历史记录不存在");
        }
        if (!canAccess(entity.getOwner(), owner)) {
            return Map.of("success", false, "message", "无权操作该记录");
        }
        historyMapper.deleteById(id);
        return Map.of("success", true);
    }

    /** 生成某条请求历史的调用详情报文（TXT），供导出下载。 */
    public byte[] exportHistoryDetail(String owner, Long id) {
        ApiRequestHistory entity = historyMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        if (!canAccess(entity.getOwner(), owner)) {
            return null;
        }
        StringBuilder sb = new StringBuilder(1024);
        sb.append("========== 接口调用详情 ==========\n");
        sb.append("记录ID:   ").append(entity.getId()).append('\n');
        sb.append("时间:     ").append(formatTime(entity.getCreatedAt())).append('\n');
        sb.append("归属用户: ").append(nullToDash(entity.getOwner())).append('\n');
        sb.append('\n');
        sb.append("---- 请求 ----\n");
        sb.append("方法:     ").append(nullToDash(entity.getMethod())).append('\n');
        sb.append("URL:      ").append(nullToDash(entity.getUrl())).append('\n');
        sb.append("请求体类型: ").append(nullToDash(entity.getBodyType())).append('\n');
        sb.append("Header:   ").append(formatJsonOrDefault(entity.getHeadersJson())).append('\n');
        sb.append("参数:     ").append(formatJsonOrDefault(entity.getParamsJson())).append('\n');
        sb.append("请求体:   \n").append(nullToDash(entity.getBody())).append('\n');
        sb.append('\n');
        sb.append("---- 响应 ----\n");
        sb.append("状态码:   ").append(entity.getStatus() == null ? "-" : String.valueOf(entity.getStatus())).append('\n');
        sb.append("是否成功: ").append(Boolean.TRUE.equals(entity.getSuccess()) ? "成功" : "失败").append('\n');
        sb.append("耗时:     ").append(entity.getDurationMs() == null ? "-" : entity.getDurationMs() + " ms").append('\n');
        sb.append("响应体:   \n").append(nullToDash(entity.getResponseBody())).append('\n');
        if (entity.getErrorMessage() != null && !entity.getErrorMessage().isBlank()) {
            sb.append("错误信息: \n").append(entity.getErrorMessage()).append('\n');
        }
        sb.append("\n================================\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String formatTime(LocalDateTime time) {
        return time == null ? "-" : time.format(TIME_FORMAT);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String formatJsonOrDefault(String json) {
        return json == null || json.isBlank() ? "-" : json;
    }


    private void applySavedFields(ApiSavedRequest entity, Map<String, Object> payload) {
        if (payload.containsKey("name")) {
            entity.setName(str(payload.get("name")));
        }
        if (payload.containsKey("collection_name")) {
            entity.setCollectionName(defaultIfBlank(str(payload.get("collection_name")), DEFAULT_COLLECTION));
        }
        if (payload.containsKey("method") && !str(payload.get("method")).isBlank()) {
            entity.setMethod(str(payload.get("method")).toUpperCase());
        }
        if (payload.containsKey("url")) {
            entity.setUrl(str(payload.get("url")));
        }
        if (payload.containsKey("headers")) {
            entity.setHeadersJson(jsonText(payload.get("headers")));
        }
        if (payload.containsKey("params")) {
            entity.setParamsJson(jsonText(payload.get("params")));
        }
        if (payload.containsKey("body")) {
            entity.setBody(strOrNull(payload.get("body")));
        }
        if (payload.containsKey("body_type") && !str(payload.get("body_type")).isBlank()) {
            entity.setBodyType(str(payload.get("body_type")));
        }
        if (payload.containsKey("description")) {
            entity.setDescription(strOrNull(payload.get("description")));
        }
        if (entity.getName() == null || entity.getName().isBlank()) {
            entity.setName("未命名请求");
        }
        if (entity.getCollectionName() == null || entity.getCollectionName().isBlank()) {
            entity.setCollectionName(DEFAULT_COLLECTION);
        }
        if (entity.getMethod() == null || entity.getMethod().isBlank()) {
            entity.setMethod("GET");
        }
    }

    /** 历史条数超限时清理最旧记录。 */
    private void evictHistory(String owner) {
        try {
            LambdaQueryWrapper<ApiRequestHistory> wrapper = new LambdaQueryWrapper<ApiRequestHistory>()
                    .orderByDesc(ApiRequestHistory::getCreatedAt)
                    .orderByDesc(ApiRequestHistory::getId);
            if (owner != null && !owner.isBlank()) {
                wrapper.eq(ApiRequestHistory::getOwner, owner);
            }
            List<ApiRequestHistory> rows = historyMapper.selectList(wrapper);
            if (rows.size() > MAX_HISTORY_PER_OWNER) {
                for (int i = MAX_HISTORY_PER_OWNER; i < rows.size(); i++) {
                    historyMapper.deleteById(rows.get(i).getId());
                }
            }
        } catch (Exception e) {
            log.warn("[ApiWorkspaceService] 清理历史记录失败: {}", e.getMessage());
        }
    }

    private boolean canAccess(String recordOwner, String currentOwner) {
        if (recordOwner == null || recordOwner.isBlank()) {
            return true;
        }
        return recordOwner.equals(currentOwner);
    }

    private Map<String, Object> toSavedDto(ApiSavedRequest entity) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", entity.getId());
        dto.put("owner", entity.getOwner());
        dto.put("collection_name", entity.getCollectionName());
        dto.put("name", entity.getName());
        dto.put("method", entity.getMethod());
        dto.put("url", entity.getUrl());
        dto.put("headers", parseJsonList(entity.getHeadersJson()));
        dto.put("params", parseJsonList(entity.getParamsJson()));
        dto.put("body", entity.getBody());
        dto.put("body_type", entity.getBodyType());
        dto.put("description", entity.getDescription());
        dto.put("created_at", entity.getCreatedAt());
        dto.put("updated_at", entity.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toHistoryDto(ApiRequestHistory entity) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", entity.getId());
        dto.put("owner", entity.getOwner());
        dto.put("method", entity.getMethod());
        dto.put("url", entity.getUrl());
        dto.put("headers", parseJsonList(entity.getHeadersJson()));
        dto.put("params", parseJsonList(entity.getParamsJson()));
        dto.put("body", entity.getBody());
        dto.put("body_type", entity.getBodyType());
        dto.put("status", entity.getStatus());
        dto.put("success", entity.getSuccess());
        dto.put("duration_ms", entity.getDurationMs());
        dto.put("response_body", entity.getResponseBody());
        dto.put("error_message", entity.getErrorMessage());
        dto.put("created_at", entity.getCreatedAt());
        return dto;
    }

    private String jsonText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s.isBlank() ? null : s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[ApiWorkspaceService] JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private List<Object> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<ArrayList<Object>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String strOrNull(Object value) {
        String s = str(value);
        return s.isEmpty() ? null : s;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...(truncated)";
    }

    private static Integer intOrNull(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longOrNull(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return (long) Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean boolValue(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }
}
