package com.sitech.prodai.service;

import com.sitech.prodai.ProdAiApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警分级 SWRL 测试（R-E00~E04，product.ttl）：
 * 覆盖 P0（计费差错率高）/P1（异常量>0）/P2（趋势劣化）/NONE（正常）四档与 SLA、建单联动。
 */
@SpringBootTest(classes = ProdAiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("告警分级 SWRL")
class AlertGradingTest {

    @Autowired
    private OpsSwrlReasoner reasoner;

    @Autowired
    private OpsRulesService opsRules;

    private OpsSwrlReasoner.SwrlAlertResult grade(Map<String, Object> facts) {
        return reasoner.gradeAlert("OF-HF-128", facts,
                opsRules.alertRule("R-E01"), opsRules.alertRule("R-E02"),
                opsRules.alertRule("R-E03"), opsRules.alertRule("R-E04"),
                opsRules.alertDefaults());
    }

    private Map<String, Object> facts(String err, String fee, String orderTrend, String errTrend, String feeTrend) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("order_count", "500");
        f.put("order_trend", orderTrend);
        f.put("error_count", err);
        f.put("error_trend", errTrend);
        f.put("fee_error_rate", fee);
        f.put("fee_trend", feeTrend);
        return f;
    }

    @Test
    @DisplayName("计费差错率≥5% 判 P0 且需建单")
    void p0ByFee() {
        OpsSwrlReasoner.SwrlAlertResult r = grade(facts("2", "0.0800", "持平", "持平", "上升"));
        Assertions.assertEquals("P0", r.level());
        Assertions.assertTrue(r.needWorkOrder());
        Assertions.assertTrue(r.slaMinutes() > 0);
        Assertions.assertTrue(r.firedRules().contains("R-E00"));
        Assertions.assertTrue(r.firedRules().contains("R-E04"));
    }

    @Test
    @DisplayName("异常量>0 判 P1")
    void p1ByError() {
        OpsSwrlReasoner.SwrlAlertResult r = grade(facts("3", "0.0100", "下降", "上升", "持平"));
        Assertions.assertEquals("P1", r.level());
        Assertions.assertTrue(r.needWorkOrder());
    }

    @Test
    @DisplayName("仅趋势劣化判 P2 不建单")
    void p2ByTrend() {
        OpsSwrlReasoner.SwrlAlertResult r = grade(facts("0", "0.0000", "下降", "持平", "持平"));
        Assertions.assertEquals("P2", r.level());
        Assertions.assertFalse(r.needWorkOrder());
    }

    @Test
    @DisplayName("全正常判 NONE")
    void noneNormal() {
        OpsSwrlReasoner.SwrlAlertResult r = grade(facts("0", "0.0000", "上升", "持平", "持平"));
        Assertions.assertEquals("NONE", r.level());
        Assertions.assertFalse(r.alerted());
    }
}
