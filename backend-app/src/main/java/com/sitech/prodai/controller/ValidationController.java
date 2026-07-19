package com.sitech.prodai.controller;

import com.sitech.prodai.service.FormService;
import com.sitech.prodai.service.ValidationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表单校验 API —— 对齐 Python {@code app/api/validation.py}。
 *
 * <p>三个端点：
 * <ul>
 *   <li>POST /api/v1/validation/field — 单字段校验</li>
 *   <li>POST /api/v1/validation/form — 整表单校验</li>
 *   <li>POST /api/v1/validation/llm — LLM 智能校验（基于本体规则）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/validation")
public class ValidationController {

    private final ValidationService validationService;
    private final FormService formService;

    public ValidationController(ValidationService validationService, FormService formService) {
        this.validationService = validationService;
        this.formService = formService;
    }

    /** 单字段校验 —— 对齐 Python POST /validation/field */
    @PostMapping("/field")
    public Map<String, Object> validateField(@RequestBody Map<String, Object> request) {
        Object fieldValue = request.get("fieldValue");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) request.getOrDefault("rules", List.of());

        ValidationService.ValidationResult result = validationService.validateField(fieldValue, rules);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("valid", result.valid);
        body.put("errors", result.errors);
        return body;
    }

    /** 整表单校验 —— 对齐 Python POST /validation/form */
    @PostMapping("/form")
    public Map<String, Object> validateForm(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) request.getOrDefault("data", new LinkedHashMap<>());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) request.getOrDefault("fields", List.of());

        ValidationService.ValidationResult result = validationService.validateForm(data, fields);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("valid", result.valid);
        body.put("errors", result.errors);
        body.put("warnings", List.of());
        return body;
    }

    /** LLM 智能校验 —— 对齐 Python POST /validation/llm（委托给 FormService 保持契约一致） */
    @PostMapping("/llm")
    public Map<String, Object> validateWithLlm(@RequestBody Map<String, Object> request) {
        return formService.validateWithLlm(request);
    }
}
