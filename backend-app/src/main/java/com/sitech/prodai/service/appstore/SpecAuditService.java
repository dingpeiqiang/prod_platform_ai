package com.sitech.prodai.service.appstore;

import com.sitech.prodai.service.common.MapOps;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置规格稽核（接口5）：按业务规范模板做完整性/合规性校验。
 * <p>校验项：必填属性、命名规则、生效期逻辑、销售范围合法性；模板可配置（audit_template 覆盖默认规范）。
 */
@Service
public class SpecAuditService {

    private static final Set<String> LEGAL_SCOPES = Set.of(
            "anhui-all", "anhui-hefei", "anhui-wuhu", "anhui-bengbu", "nationwide");
    private static final String NAME_PATTERN = "^[\\u4e00-\\u9fa5A-Za-z0-9]{2,20}$";

    /** 稽核：crm_config_json + billing_config_json + 可选模板 */
    public Map<String, Object> audit(Map<String, Object> crmConfig, Map<String, Object> billingConfig,
                                     Map<String, Object> template) {
        List<Map<String, Object>> errors = new ArrayList<>();

        checkNaming(crmConfig, template, errors);
        checkRequiredAttrs(crmConfig, template, errors);
        checkEffectPeriod(crmConfig, billingConfig, errors);
        checkSaleScope(crmConfig, errors);
        checkCrossSystem(crmConfig, billingConfig, errors);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("msg", "success");
        body.put("pass", errors.isEmpty() ? 0 : 1);
        body.put("error_list", errors);
        return body;
    }

    /** 必填属性清单（默认规范模板，可被 audit_template 覆盖/追加） */
    public List<String> defaultRequiredAttrs() {
        return List.of("product_name", "product_desc", "fee_json", "sale_scope", "effect_date");
    }

    private void checkNaming(Map<String, Object> crm, Map<String, Object> template, List<Map<String, Object>> errors) {
        String name = MapOps.str(crm.get("product_name"));
        String pattern = MapOps.str(MapOps.castMap(template).get("name_pattern"));
        java.util.regex.Pattern p;
        try {
            p = java.util.regex.Pattern.compile(pattern.isBlank() ? NAME_PATTERN : pattern);
        } catch (Exception ex) {
            p = java.util.regex.Pattern.compile(NAME_PATTERN);
        }
        if (name.isBlank()) {
            errors.add(error("product_name", "high", "产品名称缺失", "补充产品名称"));
        } else if (!p.matcher(name).matches()) {
            errors.add(error("product_name", "middle", "产品名称不符合命名规则: " + name,
                    "2-20位中英文/数字，不含特殊字符"));
        }
    }

    private void checkRequiredAttrs(Map<String, Object> crm, Map<String, Object> template,
                                    List<Map<String, Object>> errors) {
        List<String> required = new ArrayList<>(defaultRequiredAttrs());
        Object templateRequired = MapOps.castMap(template).get("required_attrs");
        if (templateRequired != null) {
            for (Object o : MapOps.castList(templateRequired)) {
                String attr = MapOps.str(o);
                if (!required.contains(attr)) {
                    required.add(attr);
                }
            }
        }
        for (String attr : required) {
            if (MapOps.empty(crm.get(attr))) {
                errors.add(error(attr, "high", "必填属性缺失: " + attr, "按 CRM 导入格式补充该属性"));
            }
        }
    }

    private void checkEffectPeriod(Map<String, Object> crm, Map<String, Object> billing,
                                   List<Map<String, Object>> errors) {
        String effect = MapOps.str(crm.get("effect_date"));
        String expire = MapOps.str(crm.get("expire_date"));
        if (!effect.isBlank() && !expire.isBlank() && effect.compareTo(expire) > 0) {
            errors.add(error("effect_date", "high", "生效日期晚于失效日期: " + effect + " > " + expire,
                    "修正生效期逻辑"));
        }
        String billingEffect = MapOps.str(billing.get("effect_date"));
        if (!effect.isBlank() && !billingEffect.isBlank() && !effect.equals(billingEffect)) {
            errors.add(error("effect_date", "low", "CRM 与计费侧生效日期不一致: " + effect + " / " + billingEffect,
                    "统一两侧生效日期"));
        }
    }

    private void checkSaleScope(Map<String, Object> crm, List<Map<String, Object>> errors) {
        String scope = MapOps.str(crm.get("sale_scope"));
        if (!scope.isBlank() && !LEGAL_SCOPES.contains(scope)) {
            errors.add(error("sale_scope", "middle", "销售范围不合法: " + scope,
                    "使用合法范围: " + String.join("/", LEGAL_SCOPES)));
        }
    }

    private void checkCrossSystem(Map<String, Object> crm, Map<String, Object> billing,
                                  List<Map<String, Object>> errors) {
        String crmProductId = MapOps.str(crm.get("product_id"));
        String billingProductId = MapOps.str(billing.get("product_id"));
        if (!crmProductId.isBlank() && !billingProductId.isBlank() && !crmProductId.equals(billingProductId)) {
            errors.add(error("product_id", "high", "CRM 与计费侧 product_id 不一致: "
                    + crmProductId + " / " + billingProductId, "以 CPCP 侧 product_id 为准对齐"));
        }
    }

    private Map<String, Object> error(String item, String level, String desc, String suggest) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("item", item);
        e.put("level", level);
        e.put("desc", desc);
        e.put("suggest", suggest);
        return e;
    }
}
