package org.example.client;

import org.example.model.Models.CompareStateRequest;
import org.example.model.Models.CompareStateResponse;
import org.example.model.Models.EvaluatePolicyRequest;
import org.example.model.Models.EvaluatePolicyResponse;
import org.example.model.Models.RetrieveFactsRequest;
import org.example.model.Models.RetrieveFactsResponse;
import org.example.model.PlatformModels;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class OntologyReasoningClient {
    private final RestClient restClient;
    public OntologyReasoningClient(String baseUrl) { this.restClient = RestClient.builder().baseUrl(baseUrl).build(); }
    public RetrieveFactsResponse retrieveFacts(RetrieveFactsRequest request) { return restClient.post().uri("/facts/retrieve").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(RetrieveFactsResponse.class); }
    public EvaluatePolicyResponse evaluatePolicy(EvaluatePolicyRequest request) { return restClient.post().uri("/policy/evaluate").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(EvaluatePolicyResponse.class); }
    public CompareStateResponse compareState(CompareStateRequest request) { return restClient.post().uri("/compare-state").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(CompareStateResponse.class); }
    public PlatformModels.EvaluateSwrlResponse evaluateSwrl(PlatformModels.EvaluateSwrlRequest request) { return restClient.post().uri("/swrl/evaluate").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(PlatformModels.EvaluateSwrlResponse.class); }
    public PlatformModels.ShaclValidationResponse validateShacl(PlatformModels.ShaclValidationRequest request) { return restClient.post().uri("/shacl/validate").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(PlatformModels.ShaclValidationResponse.class); }
    public PlatformModels.HypotheticalEvaluateResponse hypotheticalEvaluate(java.util.Map<String, Object> request) { return restClient.post().uri("/hypothetical/evaluate").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(PlatformModels.HypotheticalEvaluateResponse.class); }
    public PlatformModels.SchemaResponse schema() { return restClient.get().uri("/schema").retrieve().body(PlatformModels.SchemaResponse.class); }
    public PlatformModels.CatalogResponse schemaCatalog() { return restClient.get().uri("/schema/catalog").retrieve().body(PlatformModels.CatalogResponse.class); }
    public PlatformModels.SchemaDetailResponse schemaDetail(PlatformModels.SchemaDetailRequest request) { return restClient.post().uri("/schema/detail").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(PlatformModels.SchemaDetailResponse.class); }
    public PlatformModels.SparqlQueryResponse sparqlQuery(PlatformModels.SparqlQueryRequest request) { return restClient.post().uri("/sparql/query").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(PlatformModels.SparqlQueryResponse.class); }
    public PlatformModels.NlQueryResponse nlQuery(PlatformModels.NlQueryRequest request) { return restClient.post().uri("/nl/query").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(PlatformModels.NlQueryResponse.class); }
    public PlatformModels.ExplainResponse explain(PlatformModels.ExplainRequest request) { return restClient.post().uri("/explain").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(PlatformModels.ExplainResponse.class); }
}