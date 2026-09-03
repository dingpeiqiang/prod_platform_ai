package com.sitech.prodai.service.flow;

import com.sitech.prodai.service.FormService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G4 表单规格端口适配器：FlowEngineService.FormSchemaPort → FormService.getFormSchema。
 * <p>
 * 引擎经端口读取 human 节点引用的表单字段定义（fieldCode/fieldName/required），
 * 挂起时随响应下发、恢复时校验必填——引擎不直连 FormService/OntologyService，
 * 避免引入新的依赖环（对齐 LlmGateway/HttpGateway 既有端口模式）。
 * <p>
 * 字段归一化为 snake_case 契约（field_code/field_name/required），缺失字段容错跳过。
 */
@Component
public class FormSchemaPortAdapter implements FlowEngineService.FormSchemaPort {

    private final FormService formService;

    public FormSchemaPortAdapter(FormService formService) {
        this.formService = formService;
    }

    @Override
    public List<Map<String, Object>> fields(String formCode) {
        if (formCode == null || formCode.isBlank()) {
            return null;
        }
        Map<String, Object> schema = formService.getFormSchema(formCode);
        if (schema == null || !Boolean.TRUE.equals(schema.get("success"))
                || !(schema.get("fields") instanceof List<?> rawFields)) {
            return null;
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Object raw : rawFields) {
            if (!(raw instanceof Map<?, ?> field)) {
                continue;
            }
            String fieldCode = str(field.get("fieldCode"));
            if (fieldCode == null || fieldCode.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("field_code", fieldCode);
            row.put("field_name", str(field.get("fieldName")) == null ? fieldCode : str(field.get("fieldName")));
            row.put("field_type", str(field.get("fieldType")) == null ? "input" : str(field.get("fieldType")));
            row.put("required", Boolean.TRUE.equals(field.get("required")));
            fields.add(row);
        }
        return fields;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
