package com.sitech.prodai.controller;

import com.sitech.prodai.config.ConfigLoader;
import com.sitech.prodai.service.FormService;
import com.sitech.prodai.service.OntologyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置 API —— 对齐 Python {@code app/api/config.py}。
 *
 * <p>端点：本体列表 / 应用配置 / 数据源信息 / 导入导出（stub）
 */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final FormService formService;
    private final OntologyService ontologyService;
    private final ConfigLoader configLoader;

    public ConfigController(FormService formService,
                            OntologyService ontologyService,
                            ConfigLoader configLoader) {
        this.formService = formService;
        this.ontologyService = ontologyService;
        this.configLoader = configLoader;
    }

    /** 列出所有本体 —— 对齐 GET /api/v1/config/ontologies */
    @GetMapping("/ontologies")
    public Map<String, Object> listOntologies() {
        return formService.listOntologies();
    }

    /** 应用配置 —— 对齐 GET /api/v1/config/app */
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
    @PostMapping("/reload")
    public Map<String, Object> reload() {
        configLoader.reloadConfig("all");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "配置已重新加载");
        return body;
    }

    /** 导入列表 —— 对齐 GET /api/v1/config/import/list */
    @GetMapping("/import/list")
    public Map<String, Object> importList(@RequestParam(required = false) String formCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", List.of());
        body.put("message", "导入列表功能暂未实现");
        return body;
    }

    /** 导入模板下载 —— 对齐 GET /api/v1/config/import/template/{formCode} */
    @GetMapping("/import/template/{formCode}")
    public Map<String, Object> importTemplate(@PathVariable String formCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "导入模板功能暂未实现");
        return body;
    }

    /** 导出数据 —— 对齐 GET /api/v1/config/export/{formCode} */
    @GetMapping("/export/{formCode}")
    public Map<String, Object> exportData(@PathVariable String formCode,
                                          @RequestParam(required = false) String format) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "导出功能暂未实现");
        return body;
    }

    private boolean isLlmEnabled() {
        String enabled = System.getenv().getOrDefault("LLM_ENABLED", "false");
        return "true".equalsIgnoreCase(enabled);
    }
}
