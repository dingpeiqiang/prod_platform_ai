package com.sitech.prodai.service.appstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 需求分析模板 schema 下发服务：从 classpath 读取逻辑模型模板 schema（x-label/x-required JSON-Schema 形态），
 * 供 wf_sub_01 get_template 节点 HTTP 获取，避免代码节点依赖平台不可达的本地文件路径。
 * <p>
 * 数据源：classpath:appstore/templates/{templateId}.schema.json（由 方案/templates/*.schema.json 迁移）。
 * 线程安全：启动后只读。
 */
@Service
public class ConfigTemplateService {

    private static final Logger log = LoggerFactory.getLogger(ConfigTemplateService.class);

    private static final String TEMPLATE_DIR = "appstore/templates/";

    /** 模板标识 → 中文名 + 产品类型（与 方案/templates/_registry.json 对齐） */
    private static final Map<String, String[]> META = new LinkedHashMap<>();

    static {
        META.put("personMainPrc", new String[]{"个人主资费", "个人主套餐"});
        META.put("broadBandMainPrc", new String[]{"宽带主资费", "宽带主套餐"});
        META.put("personAddPrc", new String[]{"个人附加资费", "个人附加资费"});
        META.put("broadBandOptSpeedPrc", new String[]{"宽带加速包", "宽带附加资费"});
        META.put("familyBasePrc", new String[]{"家庭基础套餐", "家庭基础套餐"});
        META.put("familyAddPrc", new String[]{"家庭附加业务", "家庭附加资费"});
    }

    private static final String DEFAULT_TEMPLATE = "personMainPrc";

    /** 模板标识归一：支持 templateId / 中文名 / 产品类型 三种入参 */
    public String normalizeTemplateId(String req) {
        String r = req == null ? "" : req.trim();
        for (Map.Entry<String, String[]> e : META.entrySet()) {
            String[] meta = e.getValue();
            if (r.equals(e.getKey()) || r.equals(meta[0]) || r.equals(meta[1])) {
                return e.getKey();
            }
        }
        return DEFAULT_TEMPLATE;
    }

    /**
     * 读取模板 schema 原文。
     *
     * @param templateId 模板标识（可为中文名/产品类型，内部归一）
     * @return {template_id, template_name_cn, template_product_type, schema_json, schema_file, missing}
     */
    public Map<String, Object> schemaOf(String templateId) {
        String tid = normalizeTemplateId(templateId);
        String[] meta = META.getOrDefault(tid, META.get(DEFAULT_TEMPLATE));
        String fileName = tid + ".schema.json";
        String content = "{}";
        String missing = "0";
        try (InputStream in = new ClassPathResource(TEMPLATE_DIR + fileName).getInputStream()) {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            missing = "1";
            log.warn("[ConfigTemplateService] 模板 schema 读取失败: {}", fileName, ex);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("template_id", tid);
        out.put("template_name_cn", meta[0]);
        out.put("template_product_type", meta[1]);
        out.put("schema_json", content);
        out.put("schema_file", fileName);
        out.put("missing", missing);
        return out;
    }

    /** 可用模板清单（template_id / name_cn / product_type），供调试与前端选择。 */
    public List<Map<String, Object>> list() {
        return META.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("template_id", e.getKey());
            m.put("template_name_cn", e.getValue()[0]);
            m.put("template_product_type", e.getValue()[1]);
            return m;
        }).toList();
    }
}
