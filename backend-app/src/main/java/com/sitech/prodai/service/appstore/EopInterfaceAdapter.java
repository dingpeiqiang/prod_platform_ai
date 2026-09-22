package com.sitech.prodai.service.appstore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.AppStoreInterfaceProperties;
import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 适配层 real 实现（现场环境，统一方案 §4.5.4/§4.5.5）：RestClient 直连 EOP 业务地址，
 * 每个请求头带 {@code X-APP-ID} / {@code X-APP-KEY} 鉴权（值由配置注入），并组装/解包业务信封。
 * <p>
 * 业务地址 = {@code base-url} + {@code paths.<接口键>}；请求头鉴权参见
 * {@link AppStoreInterfaceProperties.Eop#getAppId()} / {@link AppStoreInterfaceProperties.Eop#getAppKey()}。
 * <p>
 * 信封差异按接口区分：A1/B4 为 {@code contractRoot.tcpCont + svcCont.requestObject}；
 * B1 multipart；B2 GET query；B3 {@code {records[]}} 裸体；C1~C4 {@code {offerId}}/{@code globalId} 裸体。
 * 出参解包：A1/B4 解 {@code contractRoot.svcCont.resultObject}；C1~C4 解 {@code resultObject}（裸）；
 * B3 解 {@code resultCode/resultMsg}。统一归一为既有内部出参名（§4.5.5 第 3 点）。
 * <p>
 * <b>严格 real（不回退）</b>：real 模式下必须调用真实 EOP 接口；任一调用失败（连接/超时/解析异常/出参缺字段）
 * 均不降级到 {@link MockInterfaceAdapter}，直接返回错误出参（{@code resultCode=REAL_CALL_FAILED} +
 * {@code resultMsg} 含失败原因）。EOP 业务错误（响应含 {@code errcode/errmsg}，如鉴权失败）亦按此严格报错，
 * 并把 EOP 原始 {@code errcode/errmsg} 带入 {@code resultMsg}，绝不静默降级为"未命中"。
 */
public class EopInterfaceAdapter implements InterfaceFacade {

    private static final Logger log = LoggerFactory.getLogger(EopInterfaceAdapter.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** real 调用失败统一错误码（不回退 mock）。 */
    private static final String ERR_REAL_CALL_FAILED = "REAL_CALL_FAILED";

    /** EOP 鉴权请求头名。 */
    private static final String HEADER_APP_ID = "X-APP-ID";
    private static final String HEADER_APP_KEY = "X-APP-KEY";

    private final AppStoreInterfaceProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    public EopInterfaceAdapter(AppStoreInterfaceProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        int timeout = Math.max(1000, props.getEop().getTimeoutMs());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String mode() {
        return "real";
    }

    /* ================= A1 相似度分析 ================= */

    @Override
    public Map<String, Object> similarOfferQuery(Map<String, Object> req) {
        String desc = firstNonBlank(MapOps.str(req.get("businessDesc")), MapOps.str(req.get("business_desc")));
        if (desc.isBlank()) {
            return fail("PARAM_MISSING", "businessDesc 必填");
        }
        String url = businessUrl("similar");
        long begin = System.currentTimeMillis();
        logRealBegin("similar", "POST", url);
        try {
            Map<String, Object> requestObject = new LinkedHashMap<>();
            requestObject.put("businessDesc", desc);
            Map<String, Object> body = postJson("similar", url, envelopeRequest(requestObject));
            Map<String, Object> resultObject = envelopeResultObject("similar", body);
            List<Map<String, Object>> list = normalizeSimilarList(resultObject);
            Map<String, Object> out = ok();
            out.put("resultMsg", list.isEmpty() ? "未命中相似销售品"
                    : "命中相似销售品 " + list.size() + " 个，主推荐：" + MapOps.str(list.get(0).get("similarOfferName")));
            out.put("similarOffer", list.isEmpty() ? Map.of() : list.get(0));
            out.put("similarOfferList", list);
            logRealDone("similar", System.currentTimeMillis() - begin, out);
            return out;
        } catch (Exception e) {
            return realFail("similar", url, e);
        }
    }

    /** A1 real 出参仅 4 字段（similarOfferId/Name/Score/Desc），归一保留 camelCase（§4.5.5 第 3/6 点）。 */
    private List<Map<String, Object>> normalizeSimilarList(Map<String, Object> resultObject) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : MapOps.castListOfMaps(resultObject.get("similarOfferList"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("similarOfferId", MapOps.str(item.get("similarOfferId")));
            row.put("similarOfferName", MapOps.str(item.get("similarOfferName")));
            row.put("similarityScore", MapOps.str(item.get("similarityScore")));
            row.put("similarityDesc", MapOps.str(item.get("similarityDesc")));
            out.add(row);
        }
        return out;
    }

    /* ================= B1 稽核文件上传（multipart） ================= */

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> auditFileUpload(Map<String, Object> req) {
        Object bytesObj = req.get("bytes");
        Object nameObj = firstNonBlank(MapOps.str(req.get("fileName")), MapOps.str(req.get("file_name")));
        byte[] bytes = bytesObj instanceof byte[] b ? b : null;
        String fileName = MapOps.str(nameObj);
        if (bytes == null || fileName.isBlank()) {
            return fail(ERR_REAL_CALL_FAILED, "上传文件缺失：real 模式必须提供 fileName 与字节流");
        }
        String url = businessUrl("upload");
        long begin = System.currentTimeMillis();
        logRealBegin("upload", "POST(multipart)", url);
        try {
            String field = props.getEop().getUploadFileField();
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add(field, resource);
            logRealRequest("upload", "POST(multipart)", url,
                    Map.of("field", field, "fileName", fileName, "size", bytes.length));
            long start = System.currentTimeMillis();
            String resp = client.post().uri(url)
                    .headers(this::applyAuth)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            logRealResponse("upload", url, System.currentTimeMillis() - start, resp);
            Map<String, Object> body = parse(resp);
            assertNoEopError("upload", body);
            if (body.get("file_id") == null && body.get("sales_id") == null) {
                throw new IllegalStateException("B1 real 出参缺少 file_id/sales_id");
            }
            logRealDone("upload", System.currentTimeMillis() - begin, body);
            return body;
        } catch (Exception e) {
            return realFail("upload", url, e);
        }
    }

    /* ================= B2 异步报告生成并通知（GET query） ================= */

    @Override
    public Map<String, Object> auditAsyncNotify(Map<String, Object> req) {
        String salesId = firstNonBlank(MapOps.str(req.get("sales_id")), MapOps.str(req.get("salesId")));
        if (salesId.isBlank()) {
            return fail("1", "sales_id 必填");
        }
        String location = firstNonBlank(MapOps.str(req.get("location")), "0");
        String sheetName = firstNonBlank(MapOps.str(req.get("sheet_name")), MapOps.str(req.get("sheetName")), "套餐");
        String fileId = firstNonBlank(MapOps.str(req.get("file_id")), MapOps.str(req.get("fileId")));
        StringBuilder url = new StringBuilder(businessUrl("notify"))
                .append("?sales_id=").append(salesId)
                .append("&location=").append(location)
                .append("&sheet_name=").append(sheetName);
        if (!fileId.isBlank()) {
            url.append("&file_id=").append(fileId);
        }
        long begin = System.currentTimeMillis();
        logRealBegin("notify", "GET", url.toString());
        try {
            logRealRequest("notify", "GET", url.toString(), null);
            long start = System.currentTimeMillis();
            String resp = client.get().uri(url.toString())
                    .headers(this::applyAuth)
                    .retrieve()
                    .body(String.class);
            logRealResponse("notify", url.toString(), System.currentTimeMillis() - start, resp);
            Map<String, Object> body = parse(resp);
            assertNoEopError("notify", body);
            if (body.get("request_id") == null) {
                throw new IllegalStateException("B2 real 出参缺少 request_id");
            }
            logRealDone("notify", System.currentTimeMillis() - begin, body);
            return body;
        } catch (Exception e) {
            return realFail("notify", url.toString(), e);
        }
    }

    /* ================= B3 插入 AI 稽核结果（{records[]} 裸体） ================= */

    @Override
    public Map<String, Object> auditResultInsert(Map<String, Object> req) {
        String url = businessUrl("insert");
        long begin = System.currentTimeMillis();
        logRealBegin("insert", "POST", url);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("records", req.get("records") == null ? List.of() : req.get("records"));
            Map<String, Object> body = postJson("insert", url, payload);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resultCode", MapOps.str(body.get("resultCode")));
            out.put("resultMsg", MapOps.str(body.get("resultMsg")));
            logRealDone("insert", System.currentTimeMillis() - begin, out);
            return out;
        } catch (Exception e) {
            return realFail("insert", url, e);
        }
    }

    /* ================= B4 通用 SQL 查询（信封，解 resultObject.executeSqlResp[]） ================= */

    @Override
    public Map<String, Object> auditSqlQuery(Map<String, Object> req) {
        String sqlId = firstNonBlank(MapOps.str(req.get("sql_id")), MapOps.str(req.get("sqlId")));
        String params = MapOps.str(req.get("params"));
        if (sqlId.isBlank()) {
            return fail("1", "sql_id 必填");
        }
        String url = businessUrl("sql");
        long begin = System.currentTimeMillis();
        logRealBegin("sql", "POST", url);
        try {
            Map<String, Object> requestObject = new LinkedHashMap<>();
            requestObject.put("sqlId", sqlId);
            requestObject.put("params", params);
            Map<String, Object> body = postJson("sql", url, envelopeRequest(requestObject));
            Map<String, Object> resultObject = envelopeResultObject("sql", body);
            List<Map<String, Object>> rows = MapOps.castListOfMaps(resultObject.get("executeSqlResp"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resultCode", envelopeResultCode(body));
            out.put("resultMsg", envelopeResultMsg(body));
            out.put("execute_sql_resp", rows);
            logRealDone("sql", System.currentTimeMillis() - begin, out);
            return out;
        } catch (Exception e) {
            return realFail("sql", url, e);
        }
    }

    /* ================= C1 销售品测试发起（{offerId} 裸体） ================= */

    @Override
    public Map<String, Object> testOfferStart(Map<String, Object> req) {
        String offerId = firstNonBlank(MapOps.str(req.get("offerId")), MapOps.str(req.get("offer_id")));
        if (offerId.isBlank()) {
            return fail("1", "offerId 必填");
        }
        String url = businessUrl("testStart");
        long begin = System.currentTimeMillis();
        logRealBegin("testStart", "POST", url);
        try {
            Map<String, Object> payload = Map.of("offerId", offerId);
            Map<String, Object> body = postJson("testStart", url, payload);
            Map<String, Object> resultObject = bareResultObject("testStart", body);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resultCode", MapOps.str(body.get("resultCode")));
            out.put("resultMsg", MapOps.str(body.get("resultMsg")));
            out.put("globalId", MapOps.str(resultObject.get("globalId")));
            logRealDone("testStart", System.currentTimeMillis() - begin, out);
            return out;
        } catch (Exception e) {
            return realFail("testStart", url, e);
        }
    }

    /* ================= C2 查询测试场景（{globalId} 裸体） ================= */

    @Override
    public Map<String, Object> testScenes(Map<String, Object> req) {
        String globalId = globalId(req);
        if (globalId.isBlank()) {
            return fail("1", "globalId 必填");
        }
        String url = businessUrl("testScenes");
        long begin = System.currentTimeMillis();
        logRealBegin("testScenes", "POST", url);
        try {
            Map<String, Object> body = postBare("testScenes", url, globalId);
            Map<String, Object> resultObject = bareResultObject("testScenes", body);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resultCode", MapOps.str(body.get("resultCode")));
            out.put("resultMsg", MapOps.str(body.get("resultMsg")));
            out.put("testScenes", MapOps.castListOfMaps(resultObject.get("testScenes")));
            logRealDone("testScenes", System.currentTimeMillis() - begin, out);
            return out;
        } catch (Exception e) {
            return realFail("testScenes", url, e);
        }
    }

    /* ================= C3 查询测试进度（{globalId} 裸体） ================= */

    @Override
    public Map<String, Object> testProgress(Map<String, Object> req) {
        String globalId = globalId(req);
        if (globalId.isBlank()) {
            return fail("1", "globalId 必填");
        }
        String url = businessUrl("testProgress");
        long begin = System.currentTimeMillis();
        logRealBegin("testProgress", "POST", url);
        try {
            Map<String, Object> body = postBare("testProgress", url, globalId);
            Map<String, Object> resultObject = bareResultObject("testProgress", body);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resultCode", MapOps.str(body.get("resultCode")));
            out.put("resultMsg", MapOps.str(body.get("resultMsg")));
            copyIfPresent(resultObject, out, "globalId", "totalSteps", "activeIndex", "done", "failed",
                    "failIndex", "totalSceneCount", "finishedSceneCount", "failedSceneCount");
            logRealDone("testProgress", System.currentTimeMillis() - begin, out);
            return out;
        } catch (Exception e) {
            return realFail("testProgress", url, e);
        }
    }

    /* ================= C4 查询测试结果（{globalId} 裸体） ================= */

    @Override
    public Map<String, Object> testResult(Map<String, Object> req) {
        String globalId = globalId(req);
        if (globalId.isBlank()) {
            return fail("1", "globalId 必填");
        }
        String url = businessUrl("testResult");
        long begin = System.currentTimeMillis();
        logRealBegin("testResult", "POST", url);
        try {
            Map<String, Object> body = postBare("testResult", url, globalId);
            Map<String, Object> resultObject = bareResultObject("testResult", body);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resultCode", MapOps.str(body.get("resultCode")));
            out.put("resultMsg", MapOps.str(body.get("resultMsg")));
            copyIfPresent(resultObject, out, "testRequestId", "testRequestName", "offerId", "offerName", "globalId");
            out.put("testScenes", MapOps.castListOfMaps(resultObject.get("testScenes")));
            logRealDone("testResult", System.currentTimeMillis() - begin, out);
            return out;
        } catch (Exception e) {
            return realFail("testResult", url, e);
        }
    }

    private Map<String, Object> postBare(String key, String url, String globalId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("globalId", globalId);
        return postJson(key, url, payload);
    }

    private String globalId(Map<String, Object> req) {
        return firstNonBlank(MapOps.str(req.get("globalId")), MapOps.str(req.get("global_id")));
    }

    /* ---------------- EOP 直连（信封组装 / 调用 / 严格解析） ---------------- */

    /** POST JSON：请求头带 X-APP-ID/X-APP-KEY 鉴权，解析响应并严格校验 EOP 业务错误。 */
    private Map<String, Object> postJson(String key, String url, Map<String, Object> payload) {
        logRealRequest(key, "POST", url, payload);
        long start = System.currentTimeMillis();
        String resp;
        try {
            resp = client.post().uri(url)
                    .headers(this::applyAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            logRealFailure(key, url, System.currentTimeMillis() - start, e);
            throw e;
        }
        logRealResponse(key, url, System.currentTimeMillis() - start, resp);
        Map<String, Object> body = parse(resp);
        assertNoEopError(key, body);
        return body;
    }

    /** 为直连请求附加 EOP 鉴权请求头（值空时也照传，由现场注入真实值）。 */
    private void applyAuth(org.springframework.http.HttpHeaders headers) {
        AppStoreInterfaceProperties.Eop eop = props.getEop();
        headers.set(HEADER_APP_ID, eop.getAppId() == null ? "" : eop.getAppId());
        headers.set(HEADER_APP_KEY, eop.getAppKey() == null ? "" : eop.getAppKey());
    }

    /** A1/B4：{contractRoot{tcpCont 固定头 + svcCont.requestObject}}，POST JSON。 */
    private Map<String, Object> envelopeRequest(Map<String, Object> requestObject) {
        String stamp = LocalDateTime.now().format(STAMP);
        String transactionId = stamp + String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));
        AppStoreInterfaceProperties.Eop eop = props.getEop();

        Map<String, Object> tcpCont = new LinkedHashMap<>();
        tcpCont.put("apiCode", "");
        tcpCont.put("transactionId", transactionId);
        tcpCont.put("svcCode", eop.getSvcCode());
        tcpCont.put("reqTime", stamp);
        tcpCont.put("globalId", transactionId);
        tcpCont.put("version", "V1.0");
        tcpCont.put("appKey", eop.getHeaderAppKey());
        tcpCont.put("dstSysId", eop.getDstSysId());
        tcpCont.put("sign", eop.getSign());

        Map<String, Object> svcCont = new LinkedHashMap<>();
        svcCont.put("requestObject", requestObject);

        Map<String, Object> contractRoot = new LinkedHashMap<>();
        contractRoot.put("tcpCont", tcpCont);
        contractRoot.put("svcCont", svcCont);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractRoot", contractRoot);
        return payload;
    }

    /**
     * 严格校验 EOP 业务错误：响应含 {@code errcode}/{@code errmsg}（如鉴权失败 errcode=242）
     * 时抛出携带原始错误码/信息的异常，由 {@link #realFail} 归一为 {@code REAL_CALL_FAILED}，
     * 避免把业务错误静默降级为"未命中"。
     */
    private void assertNoEopError(String key, Map<String, Object> body) {
        String errcode = firstNonBlank(MapOps.str(body.get("errcode")), MapOps.str(body.get("errorCode")));
        String errmsg = firstNonBlank(MapOps.str(body.get("errmsg")), MapOps.str(body.get("errorMsg")));
        if (!errcode.isBlank() || !errmsg.isBlank()) {
            throw new IllegalStateException("EOP 业务错误 [" + key + "] errcode=" + errcode + " errmsg=" + errmsg);
        }
    }

    /** 业务地址 = base-url + paths.<接口键>；未配置则明确报错（严格 real，不静默）。 */
    private String businessUrl(String key) {
        String base = props.getEop().getBaseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("prodai.appstore.interface.eop.base-url 未配置");
        }
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String path = props.getEop().path(key);
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("prodai.appstore.interface.eop.paths." + key + " 未配置");
        }
        return path.startsWith("/") ? b + path : b + "/" + path;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> envelopeResultObject(String key, Map<String, Object> body) {
        Object cr = body.get("contractRoot");
        if (cr instanceof Map<?, ?> root) {
            Object svc = root.get("svcCont");
            if (svc instanceof Map<?, ?> svcCont && svcCont.get("resultObject") instanceof Map<?, ?> ro) {
                return (Map<String, Object>) ro;
            }
        }
        throw new IllegalStateException("EOP 响应缺少 contractRoot.svcCont.resultObject [" + key + "]：" + toJson(body));
    }

    private String envelopeResultCode(Map<String, Object> body) {
        return envelopeField(body, "resultCode");
    }

    private String envelopeResultMsg(Map<String, Object> body) {
        return envelopeField(body, "resultMsg");
    }

    private String envelopeField(Map<String, Object> body, String key) {
        Object cr = body.get("contractRoot");
        if (cr instanceof Map<?, ?> root) {
            Object svc = root.get("svcCont");
            if (svc instanceof Map<?, ?> svcCont) {
                return MapOps.str(svcCont.get(key));
            }
        }
        return "";
    }

    /** C1~C4：{resultObject} 裸结构。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> bareResultObject(String key, Map<String, Object> body) {
        if (body.get("resultObject") instanceof Map<?, ?> ro) {
            return (Map<String, Object>) ro;
        }
        throw new IllegalStateException("EOP 响应缺少 resultObject [" + key + "]：" + toJson(body));
    }

    /* ---------------- 通用 ---------------- */

    private Map<String, Object> parse(String resp) {
        if (resp == null || resp.isBlank()) {
            throw new IllegalStateException("EOP 返回空响应");
        }
        try {
            return objectMapper.readValue(resp, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("EOP 响应非 JSON: " + e.getMessage(), e);
        }
    }

    /* ---------------- real 调用日志（mode=real 时输出完整过程与出入参，统一标注"[真实]"） ---------------- */

    /** 调用开始：接口键 + 方法 + 完整地址 + 生效配置（base-url/超时/鉴权头是否已注入）。*/
    private void logRealBegin(String key, String method, String url) {
        AppStoreInterfaceProperties.Eop eop = props.getEop();
        log.info("[EopInterfaceAdapter][真实·BEGIN] key={} {} url={} base-url={} timeout={}ms app-id={} app-key={}",
                key, method, url, eop.getBaseUrl(), eop.getTimeoutMs(),
                mask(eop.getAppId()), mask(eop.getAppKey()));
    }

    /** 请求体/关键业务参数：接口键 + 待发送报文（raw 出参，非密钥）。files 形态只打印字段名/文件名/字节数。*/
    private void logRealRequest(String key, String method, String url, Object body) {
        log.info("[EopInterfaceAdapter][真实→EOP] key={} {} {}", key, method, url);
        log.info("[EopInterfaceAdapter][真实→EOP] key={} request={}", key, toJson(body));
    }

    /** 响应到达：接口键 + 耗时 + 原始响应体（解包前留痕）。*/
    private void logRealResponse(String key, String url, long costMs, String resp) {
        log.info("[EopInterfaceAdapter][真实←EOP] key={} cost={}ms response={}", key, costMs, resp);
    }

    /** 出参解包完成：接口键 + 归一后出参（供调用方直接对照工作流取值）。*/
    private void logRealDone(String key, long costMs, Map<String, Object> out) {
        log.info("[EopInterfaceAdapter][真实·DONE] key={} cost={}ms result={}", key, costMs, toJson(out));
    }

    /** 调用异常：接口键 + 地址 + 耗时 + 异常类型/信息（严格模式，不回退）。*/
    private void logRealFailure(String key, String url, long costMs, Exception e) {
        log.warn("[EopInterfaceAdapter][真实✗EOP] key={} cost={}ms url={} error={}: {}",
                key, costMs, url, e.getClass().getSimpleName(), e.getMessage());
    }

    /** 密钥脱敏：仅保留末 4 位，空值显示 <empty>。 */
    private String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return "<empty>";
        }
        return "****" + secret.substring(Math.max(0, secret.length() - 4));
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * real 调用失败统一出参（严格模式，不回退 mock）：仅记录责任接口与失败原因，便于调用方感知并处理。
     * 结果形如 {@code {resultCode:"REAL_CALL_FAILED", resultMsg:"...", key:"..."}}。
     */
    private Map<String, Object> realFail(String key, String url, Exception e) {
        log.error("[EopInterfaceAdapter][真实✗调用失败] key={} url={} error={}: {}",
                key, url, e.getClass().getSimpleName(), e.getMessage());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultCode", ERR_REAL_CALL_FAILED);
        out.put("resultMsg", "真实接口调用失败：" + e.getMessage());
        out.put("key", key);
        return out;
    }

    private void copyIfPresent(Map<String, Object> src, Map<String, Object> dst, String... keys) {
        for (String k : keys) {
            if (src.containsKey(k)) {
                dst.put(k, src.get(k));
            }
        }
    }

    private Map<String, Object> ok() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resultCode", "0");
        body.put("resultMsg", "success");
        return body;
    }

    private Map<String, Object> fail(String code, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resultCode", code);
        body.put("resultMsg", msg);
        return body;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
