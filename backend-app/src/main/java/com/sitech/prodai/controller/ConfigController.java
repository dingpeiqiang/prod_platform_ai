package com.sitech.prodai.controller;

import com.sitech.prodai.config.ConfigLoader;
import com.sitech.prodai.service.FormDataImportService;
import com.sitech.prodai.service.FormService;
import com.sitech.prodai.service.OntologyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置 API —— 对齐 Python {@code app/api/config.py}。
 *
 * <p>端点：本体列表 / 应用配置 / 数据源信息 / 表单历史数据导入导出
 */
@Tag(name = "系统配置", description = "运行时配置：本体清单、应用信息、数据源状态、导入导出")
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final FormService formService;
    private final OntologyService ontologyService;
    private final ConfigLoader configLoader;
    private final FormDataImportService formDataImportService;

    public ConfigController(FormService formService,
                            OntologyService ontologyService,
                            ConfigLoader configLoader,
                            FormDataImportService formDataImportService) {
        this.formService = formService;
        this.ontologyService = ontologyService;
        this.configLoader = configLoader;
        this.formDataImportService = formDataImportService;
    }

    /** 列出所有本体 —— 对齐 GET /api/v1/config/ontologies */
    @Operation(summary = "本体配置清单", description = "返回可用本体资产配置")
@GetMapping("/ontologies")
    public Map<String, Object> listOntologies() {
        return formService.listOntologies();
    }

    /** 应用配置 —— 对齐 GET /api/v1/config/app */
    @Operation(summary = "应用信息", description = "返回应用名/版本/构建信息")
@GetMapping("/app")
    public Map<String, Object> appConfig() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appName", "AI 产品平台");
        data.put("version", "0.1.0");
        data.put("llmEnabled", isLlmEnabled());
        body.put("data", data);
        return body;
    }

    /** 数据源信息 —— 对齐 GET /api/v1/config/datasource */
    @Operation(summary = "数据源状态", description = "返回本体/指标库等数据源连接状态")
@GetMapping("/datasource")
    public Map<String, Object> datasource() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "file");
        data.put("description", "文件数据源（classpath:ontologies/*.json）");
        body.put("data", data);
        return body;
    }

    /** 重新加载配置 —— 对齐 POST /api/v1/config/reload */
    @Operation(summary = "重载配置", description = "重载运行时配置与图谱资源")
@PostMapping("/reload")
    public Map<String, Object> reload() {
        configLoader.reloadConfig("all");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "配置已重新加载");
        return body;
    }

    /** 导入列表 —— 对齐 GET /api/v1/config/import/list */
    @Operation(summary = "导入清单", description = "返回可导入的表单配置清单")
@GetMapping("/import/list")
    public Map<String, Object> importList(@RequestParam(required = false) String formCode) {
        return formDataImportService.listImportableForms();
    }

    /** 导入模板下载 —— 对齐 GET /api/v1/config/import/template/{formCode} */
    @Operation(summary = "导入模板下载", description = "按 formCode 返回 Excel 导入模板")
@GetMapping("/import/template/{formCode}")
    public ResponseEntity<byte[]> importTemplate(@PathVariable String formCode) {
        Map<String, Object> template = formDataImportService.buildTemplate(formCode);
        if (!Boolean.TRUE.equals(template.get("success"))) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(String.valueOf(template.get("message")).getBytes(StandardCharsets.UTF_8));
        }
        String content = String.valueOf(template.get("content"));
        String fileName = String.valueOf(template.get("fileName"));
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    /** JSONL 文件上传导入 —— 对齐 POST /api/v1/config/import/upload */
    @Operation(summary = "上传导入文件", description = "multipart 上传 JSONL 导入文件并预校验")
    @PostMapping(value = "/import/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importUpload(@RequestParam("file") MultipartFile file,
                                            @RequestParam(required = false) String formCode,
                                            @RequestParam(required = false) Integer limit) {
        if (file == null || file.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "上传文件为空");
            return body;
        }
        try (var in = file.getInputStream()) {
            return formDataImportService.importJsonl(formCode, in, limit);
        } catch (Exception e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "导入失败: " + e.getMessage());
            return body;
        }
    }

    /** 表单历史数据导入执行 —— 对齐 POST /api/v1/config/import/execute */
    @Operation(summary = "执行导入", description = "执行已上传文件的导入（返回成功/失败明细）")
@PostMapping("/import/execute")
    public Map<String, Object> importExecute(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> req = request == null ? Map.of() : request;
        String formCode = String.valueOf(req.getOrDefault("formCode", req.getOrDefault("form_code", "")));
        Integer limit = req.get("limit") instanceof Number n ? n.intValue() : null;
        List<Object> records = req.get("records") instanceof List<?> list ? List.copyOf(list) : List.of();
        if (formCode.isBlank()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "formCode 为必填项");
            return body;
        }
        return formDataImportService.importRecords(formCode, records, limit);
    }

    /** 导出数据 —— 对齐 GET /api/v1/config/export/{formCode}，JSONL 格式与导入对齐可直接回导 */
    @Operation(summary = "数据导出", description = "按 formCode 导出数据为 Excel")
@GetMapping("/export/{formCode}")
    public ResponseEntity<byte[]> exportData(@PathVariable String formCode,
                                             @RequestParam(required = false) Integer limit) {
        Map<String, Object> result = formDataImportService.exportJsonl(formCode, limit);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(String.valueOf(result.get("message")).getBytes(StandardCharsets.UTF_8));
        }
        String content = String.valueOf(result.get("content"));
        String fileName = String.valueOf(result.get("fileName"));
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isLlmEnabled() {
        String enabled = System.getenv().getOrDefault("LLM_ENABLED", "false");
        return "true".equalsIgnoreCase(enabled);
    }
}
