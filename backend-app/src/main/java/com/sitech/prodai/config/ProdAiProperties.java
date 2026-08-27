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
        /**
         * 是否启用定时批量风险稽核（对齐方案每日全量筛查）。
         * 默认 false，避免生产误开；demo/dev 可显式打开。
         */
        private boolean batchAuditEnabled = false;
        /** 批量稽核 cron，默认每天 02:00。 */
        private String batchAuditCron = "0 0 2 * * ?";

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

        public boolean isBatchAuditEnabled() {
            return batchAuditEnabled;
        }

        public void setBatchAuditEnabled(boolean batchAuditEnabled) {
            this.batchAuditEnabled = batchAuditEnabled;
        }

        public String getBatchAuditCron() {
            return batchAuditCron == null || batchAuditCron.isBlank() ? "0 0 2 * * ?" : batchAuditCron;
        }

        public void setBatchAuditCron(String batchAuditCron) {
            this.batchAuditCron = batchAuditCron == null ? "" : batchAuditCron;
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
         * 外部 MCP 工具种子 JSON（写入 pd_ai_mcp_tool_definitions）；
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
        /** 模型服务地址（OpenAI 兼容），如 https://api.deepseek.com */
        private String baseUrl = "https://api.openai.com";
        /** API Key。 */
        private String apiKey = "sk-placeholder";
        /** 模型名称。 */
        private String model = "gpt-4o-mini";
        /** 采样温度。 */
        private double temperature = 0.3;
        /** 普通模型输出 token 预算（max_tokens）。 */
        private int maxTokens = 4096;
        /** 推理模型输出 token 预算（max_completion_tokens），为空则回退到 maxTokens。 */
        private Integer maxCompletionTokens;
        /** 是否推理模型（如 DeepSeek 深度思考），true 时使用 max_completion_tokens。 */
        private boolean thinking = false;
        /** 是否启用流式输出。 */
        private boolean streamEnabled = true;
        /** base_url 是否为完整请求地址（含 /chat/completions）。 */
        private boolean isFullUrl = false;
        /** 鉴权类型：bearer（Authorization: Bearer）| custom（使用 authHeader 指定的请求头）。 */
        private String authType = "bearer";
        /** 自定义鉴权请求头名（auth-type=custom 时生效），如网关要求的 token。 */
        private String authHeader = "";

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

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Integer getMaxCompletionTokens() {
            return maxCompletionTokens;
        }

        public void setMaxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
        }

        public boolean isThinking() {
            return thinking;
        }

        public void setThinking(boolean thinking) {
            this.thinking = thinking;
        }

        public boolean isStreamEnabled() {
            return streamEnabled;
        }

        public void setStreamEnabled(boolean streamEnabled) {
            this.streamEnabled = streamEnabled;
        }

        public boolean isFullUrl() {
            return isFullUrl;
        }

        public void setFullUrl(boolean fullUrl) {
            isFullUrl = fullUrl;
        }

        public String getAuthType() {
            return authType;
        }

        public void setAuthType(String authType) {
            this.authType = authType;
        }

        public String getAuthHeader() {
            return authHeader;
        }

        public void setAuthHeader(String authHeader) {
            this.authHeader = authHeader;
        }
    }
}
