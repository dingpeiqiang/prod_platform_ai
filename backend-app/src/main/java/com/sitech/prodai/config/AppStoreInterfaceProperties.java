package com.sitech.prodai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产销品场景接口适配层配置（统一方案 §4.5.4）：开发 mock ↔ 现场 real 配置化切换。
 * <p>
 * mode=mock（默认）走 {@code MockInterfaceAdapter}（包装 OfferSimV16Service 与内置确定性 mock），
 * mode=real 走 {@code EopInterfaceAdapter}（业务报文直接 POST 业务地址，请求头带 X-APP-ID/X-APP-KEY/Api-Key/Staff-Code）。
 * real 调用失败严格报错（{@code resultCode=REAL_CALL_FAILED}，带 EOP 原始 errcode/errmsg），不回退 mock。
 * <p>
 * 混合模式：real-list 白名单命中接口走 real、其余走 mock，便于现场按接口逐个联调。
 */
@Component
@ConfigurationProperties(prefix = "prodai.appstore.interface")
public class AppStoreInterfaceProperties {

    /** 总开关：mock（默认，开发）/ real（现场）。 */
    private String mode = "mock";

    private final Eop eop = new Eop();

    private final Poll poll = new Poll();

    /** 混合模式白名单：命中接口键（similar/audit/upload/notify/insert/sql/test）走 real，其余 mock。空=按 mode 全量。 */
    private List<String> realList = new ArrayList<>();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Eop getEop() {
        return eop;
    }

    public Poll getPoll() {
        return poll;
    }

    public List<String> getRealList() {
        return realList;
    }

    public void setRealList(List<String> realList) {
        this.realList = realList == null ? new ArrayList<>() : realList;
    }

    /** 是否整域 real（mode=real 且白名单为空）。 */
    public boolean realAll() {
        return "real".equalsIgnoreCase(mode) && (realList == null || realList.isEmpty());
    }

    /** 指定接口键是否走 real（白名单命中或整域 real）。 */
    public boolean isReal(String key) {
        if (realList != null && !realList.isEmpty()) {
            return realList.stream().anyMatch(k -> k != null && k.equalsIgnoreCase(key));
        }
        return "real".equalsIgnoreCase(mode);
    }

    /**
     * 解析单次调用生效模式（统一方案 §4.5.4 传参覆盖）：
     * <ul>
     *   <li>override 传 real/mock → 以传参为准（最优先，覆盖 mode 与 real-list）；</li>
     *   <li>override 为空/auto → 回落到 yml：real-list 命中或 mode=real → real，否则 mock。</li>
     * </ul>
     * key 用于 yml 回落路由；caller 若已明确指定接口键不可再传 auto。
     *
     * @param key      接口键（similar/upload/notify/insert/sql/testStart/testScenes/testProgress/testResult）
     * @param override  单次覆盖值（real/mock/空/auto）
     * @return true=走 real
     */
    public boolean resolveReal(String key, String override) {
        if (override != null && !override.isBlank()) {
            String o = override.trim();
            if ("real".equalsIgnoreCase(o)) {
                return true;
            }
            if ("mock".equalsIgnoreCase(o)) {
                return false;
            }
            // auto 或无意义值 → 回落 yml
        }
        return isReal(key);
    }

    /** EOP 接入参数（前缀 eop）。 */
    public static class Eop {
        /** real 模式 EOP 基础地址（直连业务地址前缀）。 */
        private String baseUrl = "https://eop-ops.cloud.anhuitelecom.com:10008";
        /** HTTP 请求头 X-APP-ID。 */
        private String appId = "91382a43511f4f7e037856c280f0a1f1";
        /** HTTP 请求头 X-APP-KEY。 */
        private String appKey = "18ce44e39e63f17316404f20177d4252";
        /** HTTP 请求头 Staff-Code（员工编码）。 */
        private String staffCode = "501195900";
        /** HTTP 请求头 Api-Key 未配置接口键时的兜底值（一般不用，按接口 ApiID 取）。 */
        private String apiKey = "";
        /** 信封（A1/B4）tcpCont.svcCode：EOP 服务编码，必填（为空时 EOP 报「请求报文svcCode为空」）。 */
        private String svcCode = "5012010056";
        /** 信封（A1/B4）tcpCont.appKey：应用编码。 */
        private String envelopeAppKey = "eOrder1";
        /** 信封（A1/B4）tcpCont.dstSysId：目标系统标识。 */
        private String dstSysId = "OrderCenter";
        /** 信封（A1/B4）tcpCont.sign：签名（按 EOP 约定，默认占位）。 */
        private String sign = "123";
        /** B1 稽核文件上传表单字段名。 */
        private String uploadFileField = "file";
        /**
         * B1 上传前生成的稽核 Excel 本地落盘目录（文件名自动附加时间戳 {@code _yyyyMMddHHmmss}）。
         * 先把文件写入该目录暂存，再读取该文件上传 EOP；目录不存在则自动创建。
         */
        private String uploadDir = "/data/stq/crmpos/crm-pgcent-mng/upload";
        /** real 接口调用超时（毫秒）。 */
        private int timeoutMs = 60000;
        /**
         * 接口键 → 业务路径映射。键为接口键：
         * similar/upload/notify/insert/sql/testStart/testScenes/testProgress/testResult；
         * 值拼到 {@code base-url} 之后调用（如 /api/ah/cpcp/similar）。
         */
        private Map<String, String> paths = new LinkedHashMap<>();
        /**
         * 接口键 → EOP ApiID（HTTP 请求头 Api-Key 的值）。键为接口键：
         * similar/upload/notify/insert/sql/testStart/testScenes/testProgress/testResult。
         */
        private Map<String, String> apiKeys = new LinkedHashMap<>();

        /** 按接口键取业务路径（未配置返回 null）。 */
        public String path(String key) {
            return paths == null ? null : paths.get(key);
        }

        /** 按接口键取 Api-Key（HTTP 请求头 Api-Key 的值）；未配置回退 {@link #apiKey}。 */
        public String apiKey(String key) {
            String v = apiKeys == null ? null : apiKeys.get(key);
            return v == null || v.isBlank() ? apiKey : v;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getStaffCode() {
            return staffCode;
        }

        public void setStaffCode(String staffCode) {
            this.staffCode = staffCode;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getSvcCode() {
            return svcCode;
        }

        public void setSvcCode(String svcCode) {
            this.svcCode = svcCode;
        }

        public String getEnvelopeAppKey() {
            return envelopeAppKey;
        }

        public void setEnvelopeAppKey(String envelopeAppKey) {
            this.envelopeAppKey = envelopeAppKey;
        }

        public String getDstSysId() {
            return dstSysId;
        }

        public void setDstSysId(String dstSysId) {
            this.dstSysId = dstSysId;
        }

        public String getSign() {
            return sign;
        }

        public void setSign(String sign) {
            this.sign = sign;
        }

        public Map<String, String> getPaths() {
            return paths;
        }

        public void setPaths(Map<String, String> paths) {
            this.paths = paths == null ? new LinkedHashMap<>() : paths;
        }

        public Map<String, String> getApiKeys() {
            return apiKeys;
        }

        public void setApiKeys(Map<String, String> apiKeys) {
            this.apiKeys = apiKeys == null ? new LinkedHashMap<>() : apiKeys;
        }

        public String getUploadFileField() {
            return uploadFileField;
        }

        public void setUploadFileField(String uploadFileField) {
            this.uploadFileField = uploadFileField;
        }

        public String getUploadDir() {
            return uploadDir;
        }

        public void setUploadDir(String uploadDir) {
            this.uploadDir = uploadDir;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    /** 轮询窗口（前缀 poll，供工作流 type=6 代码节点读取；窗口耗尽输出"处理中"引导续查，非中断）。 */
    public static class Poll {
        private final Window audit = new Window(5000, 60);
        private final Window test = new Window(5000, 60);

        public Window getAudit() {
            return audit;
        }

        public Window getTest() {
            return test;
        }
    }

    /** 单次轮询窗口：intervalMs × max。 */
    public static class Window {
        private int intervalMs;
        private int max;

        public Window() {
        }

        public Window(int intervalMs, int max) {
            this.intervalMs = intervalMs;
            this.max = max;
        }

        public int getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(int intervalMs) {
            this.intervalMs = intervalMs;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }
    }
}
