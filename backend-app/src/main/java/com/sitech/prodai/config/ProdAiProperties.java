package com.sitech.prodai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "prodai")
public class ProdAiProperties {

    private final Ontology ontology = new Ontology();
    private final Llm llm = new Llm();
    private final Kb kb = new Kb();
    private final Mcp mcp = new Mcp();

    public Ontology getOntology() {
        return ontology;
    }

    public Llm getLlm() {
        return llm;
    }

    public Kb getKb() {
        return kb;
    }

    public Mcp getMcp() {
        return mcp;
    }

    public static class Ontology {
        /** 是否使用演示数据路径（mock_graph 等）。仅影响数据源护栏与响应 meta，不改变业务逻辑。 */
        private boolean demoEnabled = false;
        /**
         * 事实图数据源：classpath | http | empty。
         * http 时请求 {@code productCenterBaseUrl}/ops-graph。
         */
        private String dataSource = "classpath";
        /** 产商品事实图；生产应指向真实导出，勿默认 mock_graph。 */
        private String graphPath = "";
        /**
         * 本服务对外发布的 ops-graph 数据文件（GET /ops-graph）。
         * 与 {@link #graphPath} 可相同；勿与 data-source=http 自指形成启动环。
         */
        private String opsGraphPath = "";
        private String rulesPath = "classpath:ontology/ops_rules.json";
        private String productOpsOwlPath = "classpath:ontology/product-ops.ttl";
        private boolean swrlEnabled = true;
        /** 产商品中心基址（data-source=http）。 */
        private String productCenterBaseUrl = "";
        private int productCenterTimeoutMs = 5000;
        /** 槽位/文档抽取是否启用 LLM（需 prodai.llm.enabled=true）。 */
        private boolean llmExtractEnabled = true;
        /** RDF / SPARQL 命名空间基址，勿在业务代码写死 example.org。 */
        private String baseIri = "http://example.org/";
        /**
         * RDF 实例种子文件（JSON：classes/properties/instances）。
         * 为空则不灌数；演示指向 classpath:ontology/rdf_seed.json，生产可换真实导出或留空。
         */
        private String rdfSeedPath = "";
        /**
         * Turtle 本体/实例文件。为空则不导入；演示可指向 sample-ontology.ttl。
         */
        private String ttlPath = "";
        /**
         * 产商品配置本体 TTL（方案七类实体）。为空则跳过；与 {@link #ttlPath} 叠加导入 RDF4J。
         */
        private String configTtlPath = "";

        public boolean isDemoEnabled() {
            return demoEnabled;
        }

        public void setDemoEnabled(boolean demoEnabled) {
            this.demoEnabled = demoEnabled;
        }

        public String getDataSource() {
            return dataSource;
        }

        public void setDataSource(String dataSource) {
            this.dataSource = dataSource;
        }

        public String getGraphPath() {
            return graphPath;
        }

        public void setGraphPath(String graphPath) {
            this.graphPath = graphPath;
        }

        public String getOpsGraphPath() {
            return opsGraphPath;
        }

        public void setOpsGraphPath(String opsGraphPath) {
            this.opsGraphPath = opsGraphPath;
        }

        public String getRulesPath() {
            return rulesPath;
        }

        public void setRulesPath(String rulesPath) {
            this.rulesPath = rulesPath;
        }

        public String getProductOpsOwlPath() {
            return productOpsOwlPath;
        }

        public void setProductOpsOwlPath(String productOpsOwlPath) {
            this.productOpsOwlPath = productOpsOwlPath;
        }

        public boolean isSwrlEnabled() {
            return swrlEnabled;
        }

        public void setSwrlEnabled(boolean swrlEnabled) {
            this.swrlEnabled = swrlEnabled;
        }

        public String getProductCenterBaseUrl() {
            return productCenterBaseUrl;
        }

        public void setProductCenterBaseUrl(String productCenterBaseUrl) {
            this.productCenterBaseUrl = productCenterBaseUrl;
        }

        public int getProductCenterTimeoutMs() {
            return productCenterTimeoutMs;
        }

        public void setProductCenterTimeoutMs(int productCenterTimeoutMs) {
            this.productCenterTimeoutMs = productCenterTimeoutMs;
        }

        public boolean isLlmExtractEnabled() {
            return llmExtractEnabled;
        }

        public void setLlmExtractEnabled(boolean llmExtractEnabled) {
            this.llmExtractEnabled = llmExtractEnabled;
        }

        public String getBaseIri() {
            return baseIri;
        }

        public void setBaseIri(String baseIri) {
            this.baseIri = baseIri == null || baseIri.isBlank() ? "http://example.org/" : baseIri;
        }

        public String getRdfSeedPath() {
            return rdfSeedPath;
        }

        public void setRdfSeedPath(String rdfSeedPath) {
            this.rdfSeedPath = rdfSeedPath == null ? "" : rdfSeedPath;
        }

        public String getTtlPath() {
            return ttlPath;
        }

        public void setTtlPath(String ttlPath) {
            this.ttlPath = ttlPath == null ? "" : ttlPath;
        }

        public String getConfigTtlPath() {
            return configTtlPath;
        }

        public void setConfigTtlPath(String configTtlPath) {
            this.configTtlPath = configTtlPath == null ? "" : configTtlPath;
        }

        /** 保证以 / 结尾，便于拼接相对实体路径。 */
        public String normalizedBaseIri() {
            String b = getBaseIri();
            return b.endsWith("/") ? b : b + "/";
        }
    }

    public static class Kb {
        /** 知识库种子 JSON；空则启动时空库。 */
        private String seedPath = "";

        public String getSeedPath() {
            return seedPath;
        }

        public void setSeedPath(String seedPath) {
            this.seedPath = seedPath == null ? "" : seedPath;
        }
    }

    public static class Mcp {
        /**
         * 外部 MCP 工具种子 JSON（写入 mcp_tool_definitions）；
         * 空则仅暴露内存 {@code ToolRegistry} 工具。
         */
        private String seedPath = "";

        public String getSeedPath() {
            return seedPath;
        }

        public void setSeedPath(String seedPath) {
            this.seedPath = seedPath == null ? "" : seedPath;
        }
    }

    public static class Llm {
        private boolean enabled;
        /** chat 意图是否启用 Function Calling 工具循环。 */
        private boolean functionCallingEnabled = true;
        private String systemPrompt = "You are a helpful assistant.";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFunctionCallingEnabled() {
            return functionCallingEnabled;
        }

        public void setFunctionCallingEnabled(boolean functionCallingEnabled) {
            this.functionCallingEnabled = functionCallingEnabled;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }
    }
}
