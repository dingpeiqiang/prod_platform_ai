package com.sitech.prodai.service;

import com.sitech.prodai.domain.entity.OntologyAssetVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * last-known-good 快照守卫（P1-6，设计方案 §13.2，独立类）。
 * <p>事务式四步：{@code LOAD → VALIDATE → SMOKE → COMMIT}；
 * 任一步失败返回 {@code success:false + 差异报告}，保留现行版本、不抛异常。
 * <p>持久快照：{@code payload} 非空时，成功登记表 A（published）+ 表 B（reload 日志）；
 * 失败行留 review 态供复盘。graphCache 即"上一可用图谱"，由调用方在 COMMIT 内原子切换。
 * <p>时序说明（消除与 P1-7 循环依赖）：SMOKE 断言依赖 P1-7 固化的回归用例集——
 * P1-6 交付时 {@code smoke} 传 null 置空直通；P1-7 用例集固化后回接，完整自动门禁由 P2-6 收口。
 */
@Service
public class LastKnownGoodGuard {

    private static final Logger log = LoggerFactory.getLogger(LastKnownGoodGuard.class);

    private final OntologyVersionService versionService;

    public LastKnownGoodGuard(OntologyVersionService versionService) {
        this.versionService = versionService;
    }

    /** 加载 pending（解析 newPayload，暂存不覆盖现行）。 */
    @FunctionalInterface
    public interface PendingLoader {
        Map<String, Object> load() throws Exception;
    }

    /** 校验 pending（复用 OpsGraphSchemaValidator / 模板注册校验等），返回错误列表（空=通过）。 */
    @FunctionalInterface
    public interface PendingValidator {
        List<String> validate(Map<String, Object> pending);
    }

    /** 冒烟断言：用 pending 跑家庭/校园基线断言，返回失败断言列表（空=通过）。 */
    @FunctionalInterface
    public interface SmokeRunner {
        List<Map<String, Object>> run(Map<String, Object> pending);
    }

    /** 提交：全部通过后原子切换生效（COMMIT 内含内存/缓存 swap 与登记）。 */
    @FunctionalInterface
    public interface Committer {
        void commit(Map<String, Object> pending) throws Exception;
    }

    /** 动态事实源：从 pending 提取源码全文（重载场景 payload 在 LOAD 后才可知）。 */
    @FunctionalInterface
    public interface PayloadSerializer {
        String serialize(Map<String, Object> pending);
    }

    /** 守卫请求（一次事务式重载的全部输入）。 */
    public static final class GuardRequest {
        private final String assetType;
        private final String assetCode;
        private final String version;
        private final String author;
        private final String summary;
        private final String payload;
        private final PayloadSerializer payloadFrom;
        private final PendingLoader loader;
        private final PendingValidator validator;
        private final SmokeRunner smoke;
        private final Committer committer;

        private GuardRequest(Builder builder) {
            this.assetType = builder.assetType;
            this.assetCode = builder.assetCode;
            this.version = builder.version;
            this.author = builder.author;
            this.summary = builder.summary;
            this.payload = builder.payload;
            this.payloadFrom = builder.payloadFrom;
            this.loader = builder.loader;
            this.validator = builder.validator;
            this.smoke = builder.smoke;
            this.committer = builder.committer;
        }

        public static Builder builder(String assetType, String assetCode, PendingLoader loader, Committer committer) {
            return new Builder(assetType, assetCode, loader, committer);
        }

        public static final class Builder {
            private final String assetType;
            private final String assetCode;
            private final PendingLoader loader;
            private final Committer committer;
            private String version = "1.0.0";
            private String author = "system";
            private String summary = "";
            private String payload;
            private PayloadSerializer payloadFrom;
            private PendingValidator validator;
            private SmokeRunner smoke;

            private Builder(String assetType, String assetCode, PendingLoader loader, Committer committer) {
                this.assetType = assetType;
                this.assetCode = assetCode;
                this.loader = loader;
                this.committer = committer;
            }

            public Builder version(String value) { this.version = value; return this; }

            public Builder author(String value) { this.author = value; return this; }

            public Builder summary(String value) { this.summary = value; return this; }

            /** 表 A 事实源（源码全文）；null 表示本次仅守卫不登记表 A。 */
            public Builder payload(String value) { this.payload = value; return this; }

            /** 动态事实源：LOAD 后由 pending 提取（与 payload 二选一，payload 优先）。 */
            public Builder payloadFrom(PayloadSerializer value) { this.payloadFrom = value; return this; }

            public Builder validator(PendingValidator value) { this.validator = value; return this; }

            public Builder smoke(SmokeRunner value) { this.smoke = value; return this; }

            public GuardRequest build() { return new GuardRequest(this); }
        }
    }

    /**
     * 执行事务式重载。返回报告：{@code success / step / errors / message}。
     */
    public Map<String, Object> execute(GuardRequest request) {
        Map<String, Object> report = new LinkedHashMap<>();

        // 1. LOAD：解析 newPayload，暂存 pending，不覆盖现行
        Map<String, Object> pending;
        try {
            pending = request.loader.load();
        } catch (Exception e) {
            log.warn("[守卫] LOAD 失败（保留现行版本）: {}:{} - {}", request.assetType, request.assetCode, e.getMessage());
            return fail(request, null, report, "LOAD", List.of(Map.of("step", "LOAD", "error", str(e.getMessage()))));
        }

        // 2. VALIDATE：复用既有校验（OpsGraphSchemaValidator / 模板注册校验）
        List<String> errors = request.validator == null ? List.of() : request.validator.validate(pending);
        if (errors != null && !errors.isEmpty()) {
            log.warn("[守卫] VALIDATE 失败（保留现行版本）: {}:{} - {}", request.assetType, request.assetCode, errors);
            return fail(request, pending, report, "VALIDATE", errors);
        }

        // 3. SMOKE：家庭/校园基线断言（P1-6 置空直通；P1-7 用例集固化后回接）
        List<Map<String, Object>> smokeFailures = request.smoke == null ? List.of() : request.smoke.run(pending);
        if (smokeFailures != null && !smokeFailures.isEmpty()) {
            log.warn("[守卫] SMOKE 失败（保留现行版本）: {}:{} - {}", request.assetType, request.assetCode, smokeFailures);
            return fail(request, pending, report, "SMOKE", smokeFailures);
        }

        // 4. COMMIT：全过则切换生效并登记版本行
        try {
            request.committer.commit(pending);
        } catch (Exception e) {
            log.error("[守卫] COMMIT 失败（保留现行版本）: {}:{} - {}", request.assetType, request.assetCode, e.getMessage(), e);
            return fail(request, pending, report, "COMMIT", List.of(Map.of("step", "COMMIT", "error", str(e.getMessage()))));
        }

        register(request, pending, OntologyVersionService.STATUS_PUBLISHED, Map.of("success", true));
        report.put("success", true);
        report.put("step", "COMMIT");
        report.put("asset", request.assetType + ":" + request.assetCode + "@" + request.version);
        report.put("message", "reload committed, last-known-good updated");
        return report;
    }

    /** 失败：保留现行版本、不抛异常；payload 可得时登记 review 态失败行供复盘。 */
    private Map<String, Object> fail(GuardRequest request, Map<String, Object> pending,
                                     Map<String, Object> report, String step, List<?> errors) {
        register(request, pending, OntologyVersionService.STATUS_REVIEW,
                Map.of("success", false, "step", step, "errors", errors));
        report.put("success", false);
        report.put("step", step);
        report.put("errors", errors);
        report.put("message", "reload rejected at " + step + ", last-known-good retained（现行版本保持生效）");
        return report;
    }

    private void register(GuardRequest request, Map<String, Object> pending, String status, Map<String, Object> logDetail) {
        String payload = request.payload;
        if (payload == null && request.payloadFrom != null) {
            try {
                payload = request.payloadFrom.serialize(pending);
            } catch (Exception e) {
                log.warn("[守卫] payload 序列化失败（跳过表 A 登记）: {}", e.getMessage());
            }
        }
        if (payload == null) {
            return;
        }
        try {
            OntologyAssetVersion row = versionService.register(
                    request.assetType, request.assetCode, request.version,
                    status, request.author, request.summary, payload);
            versionService.log(row.getId(), "reload", request.author, logDetail);
        } catch (Exception e) {
            // 登记失败不阻断守卫结果（版本库不可用时不影响现行版本运行）
            log.warn("[守卫] 版本登记失败（不影响守卫结果）: {}", e.getMessage());
        }
    }

    private String str(String value) {
        return value == null ? "null" : value;
    }
}
