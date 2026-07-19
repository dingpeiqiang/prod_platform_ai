package org.example;

import org.example.client.OntologyReasoningClient;
import org.example.engine.IntegrativeReasonEngine;
import org.example.model.Models.CompareStateRequest;
import org.example.model.Models.EntityRef;
import org.example.model.Models.FactSet;
import org.example.model.Models.RetrieveFactsRequest;
import org.example.model.Models.StateChangePatch;
import org.example.model.Models.TraceContext;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(OntologyReasoningApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args)) {
            IntegrativeReasonEngine localEngine = ctx.getBean(IntegrativeReasonEngine.class);
            OntologyReasoningClient client = ctx.getBean(OntologyReasoningClient.class);

            TraceContext traceContext = new TraceContext(null, "marketing_tenant", null);
            var retrieveRequest = new RetrieveFactsRequest(
                    List.of(new EntityRef("Customer_Li", "Customer", "ontology")),
                    Map.of("scope", "profile"),
                    traceContext
            );

            var facts = client.retrieveFacts(retrieveRequest);
            var decision = client.evaluatePolicy(new org.example.model.Models.EvaluatePolicyRequest(
                    facts.factsMap().values().stream().findFirst().orElse(new FactSet(Map.of())),
                    Map.of("policy_set_id", "PS_MARKETING_RECOMMEND_V1"),
                    traceContext
            ));
            var compare = client.compareState(new CompareStateRequest(
                    facts.snapshotId(),
                    List.of(new StateChangePatch(new EntityRef("Customer_Li", "Customer", "ontology"), new FactSet(Map.of("vipLevel", "Platinum")), "升级为铂金卡会员")),
                    List.of("compliance_status"),
                    "PS_MARKETING_RECOMMEND_V1",
                    traceContext
            ));

            System.out.println("local_trace_steps=" + localEngine.getTrace(traceContext.traceId()).size());
            System.out.println("client_snapshot_id=" + facts.snapshotId());
            System.out.println("client_verdict=" + decision.decision().verdict());
            System.out.println("client_compare_count=" + compare.comparisons().size());
        }
    }
}
