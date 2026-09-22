package com.sitech.prodai.service.appstore;

import com.sitech.prodai.config.AppStoreInterfaceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 混合模式路由门面（统一方案 §4.5.4「混合模式」）：按 {@code real-list} 白名单逐接口路由——
 * 命中键走 real、其余走 mock，便于现场按接口逐个验证。整域 real/mock 时退化为单一适配器直通。
 * <p>
 * <b>传参覆盖</b>：每个请求体顶层可带 {@code mode} 字段（real/mock/空=auto），传参最优先——
 * 传 real/mock 覆盖 mode 与 real-list；不传/auto 回落 yml 配置。
 * <p>
 * 接口键：similar(A1)/upload(B1)/notify(B2)/insert(B3)/sql(B4)/testStart(C1)/testScenes(C2)/
 * testProgress(C3)/testResult(C4)。
 */
public class RoutingInterfaceFacade implements InterfaceFacade {

    private static final Logger log = LoggerFactory.getLogger(RoutingInterfaceFacade.class);

    private final AppStoreInterfaceProperties props;
    private final MockInterfaceAdapter mock;
    private final EopInterfaceAdapter real;

    public RoutingInterfaceFacade(AppStoreInterfaceProperties props, MockInterfaceAdapter mock,
                                  EopInterfaceAdapter real) {
        this.props = props;
        this.mock = mock;
        this.real = real;
    }

    @Override
    public String mode() {
        if (props.realAll()) {
            return "real";
        }
        if (props.getRealList() != null && !props.getRealList().isEmpty()) {
            return "mixed";
        }
        return "mock";
    }

    /** 按接口键 + 请求体顶层 mode 覆盖值选路，并输出入口路由日志（模拟/真实）。 */
    private InterfaceFacade pick(String key, Map<String, Object> req) {
        String override = modeOverride(req);
        boolean isReal = props.resolveReal(key, override);
        log.info("[产销品接口][入口] key={} 生效模式={} 将调用={} yml-mode={} real-list={} override={}",
                key, isReal ? "真实(real)" : "模拟(mock)", isReal ? "EOP真实接口" : "内置模拟报文",
                props.getMode(), props.getRealList(), override.isBlank() ? "auto" : override);
        return isReal ? real : mock;
    }

    /** 读取请求体顶层 mode 覆盖值（real/mock/auto/空）。 */
    private String modeOverride(Map<String, Object> req) {
        if (req == null) {
            return "";
        }
        Object v = req.get("mode");
        return v == null ? "" : String.valueOf(v);
    }

    @Override
    public Map<String, Object> similarOfferQuery(Map<String, Object> req) {
        return pick("similar", req).similarOfferQuery(req);
    }

    @Override
    public Map<String, Object> auditFileUpload(Map<String, Object> req) {
        return pick("upload", req).auditFileUpload(req);
    }

    @Override
    public Map<String, Object> auditAsyncNotify(Map<String, Object> req) {
        return pick("notify", req).auditAsyncNotify(req);
    }

    @Override
    public Map<String, Object> auditResultInsert(Map<String, Object> req) {
        return pick("insert", req).auditResultInsert(req);
    }

    @Override
    public Map<String, Object> auditSqlQuery(Map<String, Object> req) {
        return pick("sql", req).auditSqlQuery(req);
    }

    @Override
    public Map<String, Object> testOfferStart(Map<String, Object> req) {
        return pick("testStart", req).testOfferStart(req);
    }

    @Override
    public Map<String, Object> testScenes(Map<String, Object> req) {
        return pick("testScenes", req).testScenes(req);
    }

    @Override
    public Map<String, Object> testProgress(Map<String, Object> req) {
        return pick("testProgress", req).testProgress(req);
    }

    @Override
    public Map<String, Object> testResult(Map<String, Object> req) {
        return pick("testResult", req).testResult(req);
    }
}
