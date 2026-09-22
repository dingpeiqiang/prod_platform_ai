package com.sitech.prodai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 产销品场景接口适配层配置（统一方案 §4.5.4）：开发 mock ↔ 现场 real 配置化切换。
 * <p>
 * mode=mock（默认）走 {@code MockInterfaceAdapter}（包装 OfferSimV16Service 与内置确定性 mock），
 * mode=real 走 {@code EopInterfaceAdapter}（RestClient 直连 EOP，组装/解包信封）。
 * real 不可达时自动回退 mock 并在出参附 note 标注，保证演示不中断。
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
        /** real 模式 EOP 基础地址。 */
        private String baseUrl = "https://eop-ops.cloud.anhuitelecom.com:10008";
        /** tcpCont 固定头：应用标识。 */
        private String appKey = "eOrder1";
        /** tcpCont 固定头：目标系统标识。 */
        private String dstSysId = "OrderCenter";
        /** SQL 查询服务编码（tcpCont.svcCode）。 */
        private String svcCode = "5012010056";
        /** 签名占位（现场若有真实签名算法在此扩展）。 */
        private String sign = "123";
        /** B1 稽核文件上传表单字段名。 */
        private String uploadFileField = "file";
        /** real 接口调用超时（毫秒）。 */
        private int timeoutMs = 60000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getDstSysId() {
            return dstSysId;
        }

        public void setDstSysId(String dstSysId) {
            this.dstSysId = dstSysId;
        }

        public String getSvcCode() {
            return svcCode;
        }

        public void setSvcCode(String svcCode) {
            this.svcCode = svcCode;
        }

        public String getSign() {
            return sign;
        }

        public void setSign(String sign) {
            this.sign = sign;
        }

        public String getUploadFileField() {
            return uploadFileField;
        }

        public void setUploadFileField(String uploadFileField) {
            this.uploadFileField = uploadFileField;
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
