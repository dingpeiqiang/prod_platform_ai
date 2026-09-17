package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 存量产品批量合规扫描测试（V9.2）：
 * 遍历在架（shelfOfferings）存量产品逐一执行 R-C* 规则校验（含 R-C04 附加资费依赖缺失），
 * 结果写入 target/shelf-compliance-result.json 并打印摘要。纯只读，不修改存量数据。
 */
@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("存量产品批量合规扫描")
class ShelfComplianceScanTest {

    @Autowired
    private ProductOntologyService productOntologyService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("扫描全部存量产品并输出 R-C* 违规清单")
    void scanAllShelfCompliance() throws Exception {
        Map<String, Object> result = productOntologyService.auditShelfCompliance(List.of());

        int total = (Integer) result.getOrDefault("total", 0);
        int passed = (Integer) result.getOrDefault("passedCount", 0);
        int failed = (Integer) result.getOrDefault("failedCount", 0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");

        StringBuilder summary = new StringBuilder();
        summary.append("===== 存量批量合规扫描结果 =====\n");
        summary.append("total=").append(total).append(" passed=").append(passed).append(" failed=").append(failed).append("\n");
        for (Map<String, Object> item : items) {
            summary.append("- ").append(item.get("offeringId"))
                    .append(" | ").append(item.get("offeringName"))
                    .append(" | type=").append(item.get("offeringType"))
                    .append(" | pass=").append(item.get("pass"))
                    .append(" | resultCode=").append(item.get("resultCode"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> viols = (List<Map<String, Object>>) item.get("violations");
            if (viols != null && !viols.isEmpty()) {
                for (Map<String, Object> v : viols) {
                    summary.append("\n      [").append(v.get("ruleId")).append("|")
                            .append(v.get("issueLevel")).append("|").append(v.get("field")).append("] ")
                            .append(v.get("message"));
                }
            }
            summary.append("\n");
        }

        System.out.println(summary);

        Path out = Paths.get("target", "shelf-compliance-result.json").toAbsolutePath();
        Files.createDirectories(out.getParent());
        Files.writeString(out, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        System.out.println("RESULT_FILE=" + out);

        Assertions.assertTrue(Boolean.TRUE.equals(result.get("success")),
                "存量合规扫描应成功返回");
        Assertions.assertNotNull(items, "items 不应为空");
    }
}
