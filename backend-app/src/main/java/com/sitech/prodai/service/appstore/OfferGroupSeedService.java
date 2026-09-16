package com.sitech.prodai.service.appstore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 融合商品组种子数据中心（V2.0 融合商品加载扩展，工具1/2/4/6/7/8/14 组维度出参的数据源）。
 * <p>
 * 数据源：classpath appstore/seed_offer_groups.json（4 融合组，结构与技能包侧
 * references/seed_offer_groups.json 完全一致——成员构成/角色/required/group_rules 逐字同源）；
 * 成员关系唯一数据源纪律：下游一律引用本服务出参 offer_group，禁止自行推理成员关系。
 * <p>
 * 线程安全：启动一次性加载，运行期只读。
 */
@Service
public class OfferGroupSeedService {

    private static final Logger log = LoggerFactory.getLogger(OfferGroupSeedService.class);

    private static final String SEED_FILE = "appstore/seed_offer_groups.json";

    private final ObjectMapper objectMapper;

    /** main_offer_id -> 融合组定义（LinkedHashMap 保序） */
    private final Map<String, Map<String, Object>> groups = new LinkedHashMap<>();

    public OfferGroupSeedService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        Map<String, Object> root = readJson(SEED_FILE);
        List<Map<String, Object>> list = castMapList(root.get("groups"));
        for (Map<String, Object> group : list) {
            Object id = group.get("main_offer_id");
            if (id != null && !String.valueOf(id).isBlank()) {
                groups.put(String.valueOf(id), group);
            }
        }
        log.info("[OfferGroupSeedService] 融合组种子加载完成 groups={}", groups.size());
    }

    /* ---------------- 查询接口 ---------------- */

    public Map<String, Object> findGroup(String mainOfferId) {
        return mainOfferId == null ? null : groups.get(mainOfferId.trim());
    }

    public boolean isFusion(String mainOfferId) {
        return findGroup(mainOfferId) != null;
    }

    public int count() {
        return groups.size();
    }

    /**
     * 相似度命中结果对应融合组（优先精确 offer_id，其次主推荐）；
     * 未命中融合组返回 null（单商品模式，出参无 offer_group 键）。
     */
    public Map<String, Object> groupOfSimilar(List<Map<String, Object>> similarList) {
        if (similarList == null || similarList.isEmpty()) {
            return null;
        }
        for (Map<String, Object> item : similarList) {
            Map<String, Object> group = findGroup(MapOps.str(item.get("similarOfferId")));
            if (group != null) {
                return group;
            }
        }
        return null;
    }

    /**
     * 出参 offer_group 结构组装（逐字引用种子，禁止加工改写）：
     * {group_id, main_offer_id, main_offer_name, offer_type:"融合", members:[{role, offer_id, required,
     *  dependency?, preset?}], group_rules:{共享规则, 互斥, 依赖, 退订联动}}。
     */
    public Map<String, Object> toOfferGroup(Map<String, Object> group) {
        if (group == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("group_id", MapOps.str(group.get("group_id")));
        out.put("main_offer_id", MapOps.str(group.get("main_offer_id")));
        out.put("main_offer_name", MapOps.str(group.get("main_offer_name")));
        out.put("offer_type", "融合");
        List<Map<String, Object>> members = new ArrayList<>();
        for (Map<String, Object> m : castMapList(group.get("members"))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", MapOps.str(m.get("role")));
            item.put("offer_id", MapOps.str(m.get("offer_id")));
            item.put("required", Boolean.TRUE.equals(m.get("required")));
            if (m.get("dependency") != null && !MapOps.str(m.get("dependency")).isBlank()) {
                item.put("dependency", MapOps.str(m.get("dependency")));
            }
            Map<String, Object> preset = castMap(m.get("preset"));
            if (!preset.isEmpty()) {
                item.put("preset", preset);
            }
            members.add(item);
        }
        out.put("members", members);
        out.put("group_rules", group.get("group_rules") == null ? Map.of() : group.get("group_rules"));
        return out;
    }

    /**
     * 组内成员角色集合（不含主卡套餐）：组一致性核对（offer_group_check）与组场景生成使用。
     */
    public List<String> memberRoles(Map<String, Object> group) {
        List<String> roles = new ArrayList<>();
        if (group == null) {
            return roles;
        }
        for (Map<String, Object> m : castMapList(group.get("members"))) {
            String role = MapOps.str(m.get("role"));
            if (!role.isBlank() && !"主卡套餐".equals(role)) {
                roles.add(role);
            }
        }
        return roles;
    }

    /** 成员必选性：required=true 的成员角色清单（含主卡套餐） */
    public List<String> requiredRoles(Map<String, Object> group) {
        List<String> roles = new ArrayList<>();
        if (group == null) {
            return roles;
        }
        for (Map<String, Object> m : castMapList(group.get("members"))) {
            if (Boolean.TRUE.equals(m.get("required"))) {
                roles.add(MapOps.str(m.get("role")));
            }
        }
        return roles;
    }

    /* ---------------- 工具 ---------------- */

    private Map<String, Object> readJson(String classpath) {
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        } catch (Exception ex) {
            log.error("[OfferGroupSeedService] 融合组种子加载失败: {}", classpath, ex);
            return Map.of();
        }
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return Map.of();
    }

    private List<Map<String, Object>> castMapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    result.add(castMap(item));
                }
            }
        }
        return result;
    }
}
