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
        /**
         * ABox 生产同步源（R5）：mock | jdbc。
         * mock = 现状 classpath/HTTP 事实图（dev/demo）；jdbc = 业务系统只读库直连，
         * 由 {@code ABoxSyncScheduler} 首次全量 + 定时刷新，失败自动回退 last-known-good。
         */
        private String aboxSource = "mock";
        /** ABox JDBC 同步开关（@Scheduled 定时刷新；aboxSource=jdbc 时生效）。 */
        private boolean aboxSyncEnabled = false;
        /** ABox 定时刷新间隔（分钟），默认 30 分钟。 */
        private int aboxSyncIntervalMinutes = 30;
        /** ABox JDBC 连接 URL（只读账号；空则 jdbc 源不可用）。 */
        private String aboxJdbcUrl = "";
        /** ABox JDBC 用户名。 */
        private String aboxJdbcUsername = "";
        /** ABox JDBC 密码（生产经环境变量/密钥管理注入，禁止明文落盘）。 */
        private String aboxJdbcPassword = "";
        /** ABox JDBC 驱动类名，默认 MySQL。 */
        private String aboxJdbcDriver = "com.mysql.cj.jdbc.Driver";
        /** 单次同步最大行数护栏（防全表拖垮内存），默认 10000。 */
        private int aboxMaxRows = 10000;

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

        public String getAboxSource() {
            return aboxSource == null || aboxSource.isBlank() ? "mock" : aboxSource.trim().toLowerCase();
        }

        public void setAboxSource(String aboxSource) {
            this.aboxSource = aboxSource;
        }

        public boolean isAboxSyncEnabled() {
            return aboxSyncEnabled;
        }

        public void setAboxSyncEnabled(boolean aboxSyncEnabled) {
            this.aboxSyncEnabled = aboxSyncEnabled;
        }

        public int getAboxSyncIntervalMinutes() {
            return Math.max(1, aboxSyncIntervalMinutes);
        }

        public void setAboxSyncIntervalMinutes(int aboxSyncIntervalMinutes) {
            this.aboxSyncIntervalMinutes = aboxSyncIntervalMinutes;
        }

        public String getAboxJdbcUrl() {
            return aboxJdbcUrl == null ? "" : aboxJdbcUrl.trim();
        }

        public void setAboxJdbcUrl(String aboxJdbcUrl) {
            this.aboxJdbcUrl = aboxJdbcUrl;
        }

        public String getAboxJdbcUsername() {
            return aboxJdbcUsername == null ? "" : aboxJdbcUsername.trim();
        }

        public void setAboxJdbcUsername(String aboxJdbcUsername) {
            this.aboxJdbcUsername = aboxJdbcUsername;
        }

        public String getAboxJdbcPassword() {
            return aboxJdbcPassword == null ? "" : aboxJdbcPassword;
        }

        public void setAboxJdbcPassword(String aboxJdbcPassword) {
            this.aboxJdbcPassword = aboxJdbcPassword;
        }

        public String getAboxJdbcDriver() {
            return aboxJdbcDriver == null || aboxJdbcDriver.isBlank()
                    ? "com.mysql.cj.jdbc.Driver" : aboxJdbcDriver.trim();
        }

        public void setAboxJdbcDriver(String aboxJdbcDriver) {
            this.aboxJdbcDriver = aboxJdbcDriver;
        }

        public int getAboxMaxRows() {
            return Math.max(1, aboxMaxRows);
        }

        public void setAboxMaxRows(int aboxMaxRows) {
            this.aboxMaxRows = aboxMaxRows;
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
