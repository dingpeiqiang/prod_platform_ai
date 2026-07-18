package com.sitech.prodai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OntologyMvpApiTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetRules() throws Exception {
        mockMvc.perform(post("/api/v1/ontology-mvp/ops/risk-rules/reset"))
                .andExpect(status().isOk());
    }

    @Test
    void healthOk() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.runtime").value("spring-boot"));
    }

    @Test
    void graphSummary() throws Exception {
        mockMvc.perform(get("/api/v1/ontology-mvp/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.shelfCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.scenarios", hasItem("家庭融合")))
                .andExpect(jsonPath("$.classes").isArray())
                .andExpect(jsonPath("$.ruleSets.config").isArray());
    }

    @Test
    void ontologyMeta() throws Exception {
        mockMvc.perform(get("/api/v1/ontology-mvp/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.templates.TPL-CAMPUS-59").exists())
                .andExpect(jsonPath("$.bizScenarios.家庭融合").exists());
    }

    @Test
    void inferFamilyScenario() throws Exception {
        String body = """
                {
                  "slots": {
                    "bizScenario": "家庭融合",
                    "offeringName": "家庭融合畅享158",
                    "monthlyFee": 158
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/ontology-mvp/config/infer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.draft.offeringName").value("家庭融合畅享158"))
                .andExpect(jsonPath("$.appliedRules").isArray());
    }

    @Test
    void chatConfigureFamilyFusion() throws Exception {
        String body = """
                {"text": "给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售"}
                """;
        mockMvc.perform(post("/api/v1/ontology-mvp/config/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.bizScenario").value("家庭融合"))
                .andExpect(jsonPath("$.draft.includeVoice").value("500分钟"))
                .andExpect(jsonPath("$.draft.includeBroadband").value("500M"))
                .andExpect(jsonPath("$.compliancePass").value(false));
    }

    @Test
    void batchCampusDocument() throws Exception {
        String body = """
                {"documentText": "校园迎新产商品方案 校园青春 套餐A 0元"}
                """;
        mockMvc.perform(post("/api/v1/ontology-mvp/config/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.passedCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.pendingCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.items", hasSize(3)));
    }

    @Test
    void rootCauseTop3() throws Exception {
        String body = """
                {"offeringId": "OF-HF-128"}
                """;
        mockMvc.perform(post("/api/v1/ontology-mvp/ops/root-cause")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.paths", hasSize(3)))
                .andExpect(jsonPath("$.paths[0].name").value("营业厅"))
                .andExpect(jsonPath("$.paths[0].weight").value(0.42))
                .andExpect(jsonPath("$.paths[1].name").value("家庭融合加装礼"))
                .andExpect(jsonPath("$.paths[2].name").value("友商融合120"));
    }

    @Test
    void riskAuditAcceptance() throws Exception {
        mockMvc.perform(post("/api/v1/ontology-mvp/ops/risk-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedCount").value(80))
                .andExpect(jsonPath("$.highCount").value(13))
                .andExpect(jsonPath("$.mediumCount").value(7))
                .andExpect(jsonPath("$.suggestDelistCount").value(7));
    }

    @Test
    void opsDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/ontology-mvp/ops/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anomalyOfferingCount").value(1))
                .andExpect(jsonPath("$.highRiskCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.alerts").isArray());
    }

    @Test
    void complianceMissingRequired() throws Exception {
        String body = """
                {
                  "draft": {
                    "offeringName": "",
                    "monthlyFee": null
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/ontology-mvp/config/compliance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.compliancePass").value(false))
                .andExpect(jsonPath("$.issues").isArray());
    }

    @Test
    void compliancePassMinimal() throws Exception {
        String body = """
                {
                  "draft": {
                    "offeringName": "测试套餐",
                    "monthlyFee": 59,
                    "targetUser": "个人",
                    "channelScope": "全渠道",
                    "mutexGroup": "MAIN_PKG",
                    "offeringType": "main_pkg"
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/ontology-mvp/config/compliance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compliancePass").value(true))
                .andExpect(jsonPath("$.canSubmit").value(true));
    }
}
