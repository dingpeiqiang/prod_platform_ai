package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.domain.entity.McpCallLog;
import com.sitech.prodai.domain.entity.McpToolDefinition;
import com.sitech.prodai.intent.tools.ToolDefinition;
import com.sitech.prodai.intent.tools.ToolRegistry;
import com.sitech.prodai.repository.McpCallLogRepository;
import com.sitech.prodai.repository.McpToolDefinitionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * MCP 工具管理：内存 {@link ToolRegistry} + DB 外部工具定义。
 * 演示种子由 {@code prodai.mcp.seed-path} 写入 DB（已存在则跳过）。
 */
@Service
public class McpManagementService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpManagementService.class);

    private final ToolRegistry toolRegistry;
    private final McpToolDefinitionRepository toolRepo;
    private final McpCallLogRepository callLogRepo;
    private final ProdAiProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public McpManagementService(ToolRegistry toolRegistry,
                                McpToolDefinitionRepository toolRepo,
                                McpCallLogRepository callLogRepo,
                                ProdAiProperties properties,
                                ResourceLoader resourceLoader,
                                ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.toolRepo = toolRepo;
        this.callLogRepo = callLogRepo;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        loadSeed();
    }

    private void loadSeed() {
        String path = properties.getMcp().getSeedPath();
        if (path == null || path.isBlank()) {
            log.info("[McpManagementService] mcp.seed-path 未配置，仅使用 ToolRegistry");
            return;
        }
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                throw new IllegalStateException("MCP seed not found: " + path);
            }
            try (InputStream in = resource.getInputStream()) {
                Map<String, Object> seed = objectMapper.readValue(in, new TypeReference<>() {});
                Object raw = seed.get("tools");
                if (!(raw instanceof List<?> list)) {
                    return;
                }
                int created = 0;
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> m)) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    String name = str(row.get("tool_name"));
                    if (name.isBlank() || toolRepo.findByToolName(name).isPresent()) {
                        continue;
                    }
                    McpToolDefinition def = new McpToolDefinition();
                    def.setToolName(name);
                    def.setToolCode(str(row.getOrDefault("tool_code", name)));
                    def.setDescription(str(row.get("description")));
                    def.setCategory(str(row.getOrDefault("category", "external")));
                    def.setIsEnabled(!Boolean.FALSE.equals(row.get("is_enabled")));
                    def.setIsPublic(!Boolean.FALSE.equals(row.get("is_public")));
                    def.setToolType(str(row.getOrDefault("tool_type", "url")));
                    def.setProtocol(str(row.getOrDefault("protocol", "http")));
                    def.setRequestMethod(str(row.getOrDefault("request_method", "POST")));
                    def.setUrl(str(row.get("url")));
                    if (row.get("input_schema") instanceof Map<?, ?> is) {
                        Map<String, Object> schema = new LinkedHashMap<>();
                        is.forEach((k, v) -> schema.put(String.valueOf(k), v));
                        def.setInputSchema(schema);
                    }
                    if (row.get("output_schema") instanceof Map<?, ?> os) {
                        Map<String, Object> schema = new LinkedHashMap<>();
                        os.forEach((k, v) -> schema.put(String.valueOf(k), v));
                        def.setOutputSchema(schema);
                    }
                    toolRepo.save(def);
                    created++;
                }
                log.info("[McpManagementService] seeded {} external tools from {}", created, path);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load MCP seed: " + path, e);
        }
    }

    public Map<String, Object> listTools(String category) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (String name : toolRegistry.getAllToolNames()) {
            toolRegistry.getTool(name).ifPresent(def -> {
                Map<String, Object> row = toRegistryToolView(def);
                if (category == null || category.isBlank()
                        || category.equals(((Map<?, ?>) row.get("metadata")).get("category"))) {
                    tools.add(row);
                }
            });
        }
        for (McpToolDefinition def : toolRepo.findByIsEnabledTrue()) {
            Map<String, Object> row = toDbToolView(def);
            if (category == null || category.isBlank()
                    || category.equals(def.getCategory())) {
                tools.add(row);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("tools", tools);
        body.put("total", tools.size());
        return body;
    }

    public Map<String, Object> stats() {
        List<McpCallLog> all = callLogRepo.findAll();
        long success = all.stream().filter(l -> Boolean.TRUE.equals(l.getSuccess())).count();
        Set<String> cats = new LinkedHashSet<>();
        toolRegistry.getAllToolNames().forEach(n -> cats.add(inferCategory(n)));
        toolRepo.findAll().forEach(t -> {
            if (t.getCategory() != null) {
                cats.add(t.getCategory());
            }
        });
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_tools", toolRegistry.getToolCount() + toolRepo.count());
        data.put("categories", new ArrayList<>(cats));
        data.put("total_calls", all.size());
        data.put("success_calls", success);
        data.put("failed_calls", all.size() - success);
        data.put("success_rate", all.isEmpty() ? 0.0 : (success * 100.0 / all.size()));
        data.put("recent_logs_count", Math.min(all.size(), 100));
        return Map.of("success", true, "data", data);
    }

    public Map<String, Object> categories() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : toolRegistry.getAllToolNames()) {
            String cat = inferCategory(name);
            counts.merge(cat, 1, Integer::sum);
        }
        for (McpToolDefinition def : toolRepo.findAll()) {
            String cat = def.getCategory() == null || def.getCategory().isBlank() ? "external" : def.getCategory();
            counts.merge(cat, 1, Integer::sum);
        }
        List<Map<String, Object>> cats = new ArrayList<>();
        counts.forEach((code, count) -> cats.add(Map.of(
                "code", code,
                "name", categoryLabel(code),
                "count", count
        )));
        return Map.of("success", true, "categories", cats);
    }

    public Map<String, Object> logs(String toolName, int limit) {
        int lim = Math.max(1, Math.min(limit, 500));
        List<McpCallLog> rows;
        if (toolName != null && !toolName.isBlank()) {
            rows = callLogRepo.findByToolNameOrderByTimestampDesc(toolName);
        } else {
            rows = callLogRepo.findAll();
            rows.sort((a, b) -> {
                LocalDateTime ta = a.getTimestamp();
                LocalDateTime tb = b.getTimestamp();
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            });
        }
        if (rows.size() > lim) {
            rows = rows.subList(0, lim);
        }
        List<Map<String, Object>> logs = new ArrayList<>();
        for (McpCallLog logRow : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", logRow.getId());
            m.put("tool_name", logRow.getToolName());
            m.put("tool_category", logRow.getToolCategory());
            m.put("success", logRow.getSuccess());
            m.put("execution_time_ms", logRow.getExecutionTimeMs());
            m.put("error_message", logRow.getErrorMessage());
            m.put("timestamp", logRow.getTimestamp() == null ? null : logRow.getTimestamp().toString());
            m.put("request_args", logRow.getRequestArgs());
            m.put("response_data", logRow.getResponseData());
            logs.add(m);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("logs", logs);
        body.put("data", logs);
        body.put("total", logs.size());
        return body;
    }

    @Transactional
    public Map<String, Object> testTool(String toolName, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        boolean ok = false;
        Object resultPayload;
        String error = null;
        String category = inferCategory(toolName);

        Optional<String> registryResult = toolRegistry.execute(toolName, safeArgs);
        if (registryResult.isPresent()) {
            ok = true;
            resultPayload = registryResult.get();
        } else {
            Optional<McpToolDefinition> dbTool = toolRepo.findByToolName(toolName);
            if (dbTool.isPresent()) {
                McpToolDefinition def = dbTool.get();
                category = def.getCategory();
                Map<String, Object> simulated = new LinkedHashMap<>();
                simulated.put("message", "外部工具已登记，当前返回契约探测结果（未真实 HTTP 调用）");
                simulated.put("tool", toolName);
                simulated.put("url", def.getUrl());
                simulated.put("method", def.getRequestMethod());
                simulated.put("args", safeArgs);
                ok = Boolean.TRUE.equals(def.getIsEnabled());
                resultPayload = simulated;
                if (!ok) {
                    error = "tool disabled";
                }
            } else {
                error = "tool not found: " + toolName;
                resultPayload = Map.of("error", error);
            }
        }

        double elapsed = System.currentTimeMillis() - start;
        McpCallLog callLog = new McpCallLog();
        callLog.setToolName(toolName);
        callLog.setToolCategory(category);
        callLog.setSuccess(ok);
        callLog.setExecutionTimeMs(elapsed);
        callLog.setErrorMessage(error);
        callLog.setTimestamp(LocalDateTime.now());
        try {
            callLog.setRequestArgs(objectMapper.writeValueAsString(safeArgs));
            callLog.setResponseData(resultPayload instanceof String s
                    ? s
                    : objectMapper.writeValueAsString(resultPayload));
        } catch (Exception e) {
            callLog.setRequestArgs(String.valueOf(safeArgs));
            callLog.setResponseData(String.valueOf(resultPayload));
        }
        callLogRepo.save(callLog);

        toolRepo.findByToolName(toolName).ifPresent(def -> {
            def.setTotalCalls((def.getTotalCalls() == null ? 0 : def.getTotalCalls()) + 1);
            def.setLastCalledAt(LocalDateTime.now());
            toolRepo.save(def);
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", toolName);
        data.put("ok", ok);
        data.put("result", resultPayload);
        data.put("timestamp", Instant.now().toString());
        data.put("execution_time_ms", elapsed);
        return Map.of("success", true, "data", data);
    }

    public Map<String, Object> externalTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpToolDefinition def : toolRepo.findAll()) {
            tools.add(toExternalToolDto(def));
        }
        return Map.of("success", true, "data", tools, "tools", tools, "total", tools.size());
    }

    public Map<String, Object> getExternalTool(String toolName) {
        return toolRepo.findByToolName(toolName)
                .<Map<String, Object>>map(def -> Map.of("success", true, "data", toExternalToolDto(def)))
                .orElse(Map.of("success", false, "message", "not found"));
    }

    @Transactional
    public Map<String, Object> createExternalTool(Map<String, Object> body) {
        String name = str(body.get("tool_name"));
        if (name.isBlank()) {
            return Map.of("success", false, "message", "tool_name required");
        }
        if (toolRepo.findByToolName(name).isPresent()) {
            return Map.of("success", false, "message", "tool already exists");
        }
        McpToolDefinition def = fromBody(new McpToolDefinition(), body);
        def.setToolName(name);
        toolRepo.save(def);
        return Map.of("success", true, "data", toExternalToolDto(def));
    }

    @Transactional
    public Map<String, Object> updateExternalTool(String toolName, Map<String, Object> body) {
        Optional<McpToolDefinition> opt = toolRepo.findByToolName(toolName);
        if (opt.isEmpty()) {
            return Map.of("success", false, "message", "not found");
        }
        McpToolDefinition def = fromBody(opt.get(), body);
        toolRepo.save(def);
        return Map.of("success", true, "data", toExternalToolDto(def));
    }

    @Transactional
    public Map<String, Object> deleteExternalTool(String toolName) {
        Optional<McpToolDefinition> opt = toolRepo.findByToolName(toolName);
        if (opt.isEmpty()) {
            return Map.of("success", false, "message", "not found");
        }
        toolRepo.delete(opt.get());
        return Map.of("success", true, "message", "deleted");
    }

    @Transactional
    public Map<String, Object> toggleExternalTool(String toolName, boolean enabled) {
        Optional<McpToolDefinition> opt = toolRepo.findByToolName(toolName);
        if (opt.isEmpty()) {
            return Map.of("success", false, "message", "not found");
        }
        McpToolDefinition def = opt.get();
        def.setIsEnabled(enabled);
        toolRepo.save(def);
        return Map.of("success", true, "data", toExternalToolDto(def));
    }

    private McpToolDefinition fromBody(McpToolDefinition def, Map<String, Object> body) {
        if (body.containsKey("tool_code")) {
            def.setToolCode(str(body.get("tool_code")));
        }
        if (body.containsKey("description")) {
            def.setDescription(str(body.get("description")));
        }
        if (body.containsKey("category")) {
            def.setCategory(str(body.get("category")));
        }
        if (body.containsKey("url")) {
            def.setUrl(str(body.get("url")));
        }
        if (body.containsKey("request_method")) {
            def.setRequestMethod(str(body.get("request_method")));
        }
        if (body.containsKey("protocol")) {
            def.setProtocol(str(body.get("protocol")));
        }
        if (body.containsKey("tool_type")) {
            def.setToolType(str(body.get("tool_type")));
        }
        if (body.containsKey("is_enabled")) {
            def.setIsEnabled(Boolean.TRUE.equals(body.get("is_enabled")) || "true".equalsIgnoreCase(str(body.get("is_enabled"))));
        }
        if (body.get("input_schema") instanceof Map<?, ?> is) {
            Map<String, Object> schema = new LinkedHashMap<>();
            is.forEach((k, v) -> schema.put(String.valueOf(k), v));
            def.setInputSchema(schema);
        }
        if (body.get("output_schema") instanceof Map<?, ?> os) {
            Map<String, Object> schema = new LinkedHashMap<>();
            os.forEach((k, v) -> schema.put(String.valueOf(k), v));
            def.setOutputSchema(schema);
        }
        if (def.getToolCode() == null || def.getToolCode().isBlank()) {
            def.setToolCode(def.getToolName());
        }
        if (def.getCategory() == null || def.getCategory().isBlank()) {
            def.setCategory("external");
        }
        return def;
    }

    private Map<String, Object> toRegistryToolView(ToolDefinition def) {
        String category = inferCategory(def.name());
        long total = callLogRepo.countByToolName(def.name());
        long success = callLogRepo.countByToolNameAndSuccess(def.name(), true);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_calls", total);
        stats.put("success_calls", success);
        stats.put("avg_response_time_ms", avgExecMs(def.name()));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", def.name());
        row.put("description", def.description());
        row.put("label", def.name());
        row.put("category", category);
        row.put("enabled", true);
        row.put("inputSchema", def.parameters() == null ? Map.of() : def.parameters());
        row.put("outputSchema", Map.of());
        row.put("metadata", Map.of("category", category, "source", "registry"));
        row.put("stats", stats);
        return row;
    }

    private Map<String, Object> toDbToolView(McpToolDefinition def) {
        long total = callLogRepo.countByToolName(def.getToolName());
        long success = callLogRepo.countByToolNameAndSuccess(def.getToolName(), true);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_calls", Math.max(total, def.getTotalCalls() == null ? 0 : def.getTotalCalls()));
        stats.put("success_calls", success);
        stats.put("avg_response_time_ms", avgExecMs(def.getToolName()));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", def.getToolName());
        row.put("description", def.getDescription());
        row.put("label", def.getToolName());
        row.put("category", def.getCategory());
        row.put("enabled", def.getIsEnabled());
        row.put("url", def.getUrl());
        row.put("requestMethod", def.getRequestMethod());
        row.put("protocol", def.getProtocol());
        row.put("authType", def.getAuthType());
        row.put("inputSchema", def.getInputSchema() == null ? Map.of() : def.getInputSchema());
        row.put("outputSchema", def.getOutputSchema() == null ? Map.of() : def.getOutputSchema());
        row.put("metadata", Map.of("category", def.getCategory() == null ? "external" : def.getCategory(), "source", "db"));
        row.put("stats", stats);
        return row;
    }

    private Map<String, Object> toExternalToolDto(McpToolDefinition def) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tool_name", def.getToolName());
        m.put("tool_code", def.getToolCode());
        m.put("description", def.getDescription());
        m.put("category", def.getCategory());
        m.put("is_enabled", def.getIsEnabled());
        m.put("is_public", def.getIsPublic());
        m.put("tool_type", def.getToolType());
        m.put("protocol", def.getProtocol());
        m.put("request_method", def.getRequestMethod());
        m.put("url", def.getUrl());
        m.put("input_schema", def.getInputSchema());
        m.put("output_schema", def.getOutputSchema());
        m.put("total_calls", def.getTotalCalls());
        m.put("last_called_at", def.getLastCalledAt() == null ? null : def.getLastCalledAt().toString());
        return m;
    }

    private double avgExecMs(String toolName) {
        List<McpCallLog> logs = callLogRepo.findByToolNameOrderByTimestampDesc(toolName);
        if (logs.isEmpty()) {
            return 0;
        }
        double sum = 0;
        int n = 0;
        for (McpCallLog l : logs) {
            if (l.getExecutionTimeMs() != null) {
                sum += l.getExecutionTimeMs();
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    private static String inferCategory(String toolName) {
        if (toolName == null) {
            return "general";
        }
        if (toolName.startsWith("ontology") || toolName.contains("sparql") || toolName.equals("explain") || toolName.equals("compare_state")) {
            return "ontology";
        }
        if (toolName.contains("policy") || toolName.contains("compliance") || toolName.contains("risk")) {
            return "compliance";
        }
        if (toolName.contains("form") || toolName.contains("validate")) {
            return "form";
        }
        return "general";
    }

    private static String categoryLabel(String code) {
        return switch (code) {
            case "ontology" -> "本体";
            case "compliance" -> "合规";
            case "form" -> "表单";
            case "external" -> "外部";
            default -> code;
        };
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
