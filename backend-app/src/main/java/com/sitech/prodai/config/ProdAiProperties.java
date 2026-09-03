package com.sitech.prodai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "prodai")
public class ProdAiProperties {

    private final Ontology ontology = new Ontology();
    private final Llm llm = new Llm();
    private final Kb kb = new Kb();
    private final Mcp mcp = new Mcp();
    private final FlowRouter flowRouter = new FlowRouter();

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

    public FlowRouter getFlowRouter() {
        return flowRouter;
    }

    /** 流程意图路由注册表（S1 对话即编排）：启动时把配置的关键词规则注册进 FlowIntentRouter。 */
    public static class FlowRouter {
        /** 是否启用流程意图路由（灰度开关，默认关闭零风险）。 */
        private boolean enabled = false;
        /** 路由规则：workflowCode → 显示名 + 触发关键词（任一命中即路由）。 */
        private List<Map<String, Object>> routes = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<Map<String, Object>> getRoutes() {
            return routes;
        }

        public void setRoutes(List<Map<String, Object>> routes) {
            this.routes = routes;
        }
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

        /**
         * 默认生效模型名，对应 {@link #models} 中某项的 {@code name}。
         * 请求未显式指定模型时采用该项。未命中时取 {@link #models} 第一条。
         */
        private String defaultModel = "";

        /** 多模型配置列表（各自独立连接）。至少配置一项才能正常调用。 */
        private List<LlmModelConfig> models = new ArrayList<>();

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

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel == null ? "" : defaultModel;
        }

        public List<LlmModelConfig> getModels() {
            return models;
        }

        public void setModels(List<LlmModelConfig> models) {
            this.models = models == null ? new ArrayList<>() : models;
        }
    }

    /**
     * 单条模型连接配置（各自独立）。与 {@link Llm} 的单组字段语义一致，
     * 用于 prodai.llm.models 下配置多个模型并各自指定连接参数。
     */
    public static class LlmModelConfig {
        /** 模型别名，供 {@code prodai.llm.default-model} 引用。 */
        private String name = "";
        /** 模型服务地址（OpenAI 兼容）。 */
        private String baseUrl = "";
        /** API Key。 */
        private String apiKey = "";
        /** 模型名称，如 deepseek-chat。 */
        private String model = "";
        private double temperature = 0.3;
        private int maxTokens = 4096;
        private Integer maxCompletionTokens;
        private boolean thinking = false;
        private boolean streamEnabled = true;
        private boolean isFullUrl = false;
        private String authType = "bearer";
        private String authHeader = "";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name == null ? "" : name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl == null ? "" : baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model == null ? "" : model;
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
            this.authType = authType == null ? "" : authType;
        }

        public String getAuthHeader() {
            return authHeader;
        }

        public void setAuthHeader(String authHeader) {
            this.authHeader = authHeader == null ? "" : authHeader;
        }
    }
}
