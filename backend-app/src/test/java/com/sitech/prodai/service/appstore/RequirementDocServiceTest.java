package com.sitech.prodai.service.appstore;

import com.sitech.prodai.ProdAiApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 需求文档（.xlsx）解析测试（统一方案 V3.10 6.6 / 端点 2e）：
 * 以《新增四档5G畅享融合套餐销售品.xlsx》为真实样本，断言
 * 「可选包」4 档销售品 + 「关联关系」四档对副卡 7320110001600005 的可选依赖。
 * 纯只读；样本缺失时跳过（不阻塞 CI）。
 */
@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("需求文档 Excel 解析")
class RequirementDocServiceTest {

    private static final Path SAMPLE = Paths.get(
            "..", "场景设计", "ah_cti_poc", "方案", "新增四档5G畅享融合套餐销售品.xlsx");

    @Autowired
    private RequirementDocService requirementDocService;

    @Test
    @DisplayName("解析四档5G畅享融合套餐：offers=4 且 关系=副卡可选依赖")
    void parseFourTierFusionOffer() throws Exception {
        Path sample = SAMPLE.toAbsolutePath().normalize();
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(sample),
                "样本缺失，跳过：" + sample);

        byte[] bytes = Files.readAllBytes(sample);
        Map<String, Object> body = requirementDocService.parse(bytes, sample.getFileName().toString());

        System.out.println("===== 需求文档解析结果 =====");
        System.out.println("resultCode=" + body.get("resultCode")
                + " count=" + body.get("count")
                + " sheets=" + body.get("source_sheets"));

        Assertions.assertEquals("0", body.get("resultCode"), "解析应成功：" + body.get("resultMsg"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> offers = (List<Map<String, Object>>) body.get("offers");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations = (List<Map<String, Object>>) body.get("relations");
        @SuppressWarnings("unchecked")
        List<String> sheets = (List<String>) body.get("source_sheets");

        Assertions.assertEquals(4, offers.size(), "可选包应为 4 档销售品");
        for (Map<String, Object> offer : offers) {
            System.out.println("- offer_seq=" + offer.get("offer_seq")
                    + " ofr_id=" + offer.get("ofr_id")
                    + " name=" + offer.get("offer_name"));
        }
        // 四档 ofr_id 应与需求文档一致
        List<String> ids = offers.stream().map(o -> String.valueOf(o.get("ofr_id"))).toList();
        Assertions.assertTrue(ids.containsAll(List.of(
                "900117029", "900117030", "900117031", "900117032")),
                "四档 ofr_id 应为 900117029~032，实际=" + ids);

        // 四档均对副卡 7320110001600005 形成可选依赖（relation=100000，原始标签「可选依赖」）
        long dependCount = relations.stream()
                .filter(r -> "7320110001600005".equals(String.valueOf(r.get("z_ofr_id")))
                        || "7320110001600005".equals(String.valueOf(r.get("a_ofr_id"))))
                .count();
        Assertions.assertTrue(dependCount >= 4,
                "四档应对副卡 7320110001600005 形成可选依赖，实际匹配=" + dependCount);
        for (Map<String, Object> rel : relations) {
            System.out.println("- rel a=" + rel.get("a_ofr_id")
                    + " -> " + rel.get("relation") + "(" + rel.get("relation_label") + ")"
                    + " -> z=" + rel.get("z_ofr_id"));
        }
        boolean anyOptional = relations.stream()
                .anyMatch(r -> "100000".equals(String.valueOf(r.get("relation")))
                        && "7320110001600005".equals(String.valueOf(r.get("z_ofr_id"))));
        Assertions.assertTrue(anyOptional, "中文标签「可选依赖」应归一为枚举 100000");

        Assertions.assertTrue(sheets.contains("可选包"), "source_sheets 应含【可选包】：" + sheets);
        Assertions.assertTrue(sheets.contains("关联关系"), "source_sheets 应含【关联关系】：" + sheets);
    }
}
