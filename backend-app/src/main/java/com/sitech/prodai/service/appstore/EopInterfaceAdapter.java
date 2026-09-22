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
 * 适配层 real 实现（现场环境，统一方案 §4.5.4/§4.5.5）：RestClient 直连 EOP，组装/解包信封。
 * <p>
 * 信封差异按接口区分：A1/B4 为 {@code contractRoot.tcpCont + svcCont.requestObject}；
 * B1 multipart；B2 GET query；B3 {@code {records[]}} 裸体；C1~C4 {@code {offerId}}/{@code {globalId}} 裸体。
 * 出参解包：A1/B4 解 {@code contractRoot.svcCont.resultObject}；C1~C4 解 {@code resultObject}（裸）；
 * B3 解 {@code resultCode/resultMsg}。统一归一为既有内部出参名（§4.5.5 第 3 点）。
 * <p>
 * <b>现场不可达回退</b>：任一 real 调用失败（连接/超时/解析异常）时自动回退 {@link MockInterfaceAdapter}，
 * 并在出参附 {@code note} 标注"已回退模拟"，保证演示不中断（统一方案 §4.5.4 落地口径）。
 */
public class EopInterfaceAdapter implements InterfaceFacade {

    private static final Logger log = LoggerFactory.getLogger(EopInterfaceAdapter.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final AppStoreInterfaceProperties props;
    private final MockInterfaceAdapter fallback;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    public EopInterfaceAdapter(AppStoreInterfaceProperties props, MockInterfaceAdapter fallback,
                               ObjectMapper objectMapper) {
        this.props = props;
        this.fallback = fallback;
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
        String url = base() + "/api/ah/cpcp/similar";
        try {
            Map<String, Object> requestObject = new LinkedHashMap<>();
            requestObject.put("businessDesc", desc);
            Map<String, Object> body = postEnvelope(url, requestObject);
            Map<String, Object> resultObject = envelopeResultObject(body);
            List<Map<String, Object>> list = normalizeSimilarList(resultObject);
            Map<String, Object> out = ok();
            out.put("resultMsg", list.isEmpty() ? "未命中相似销售品"
                    : "命中相似销售品 " + list.size() + " 个，主推荐：" + MapOps.str(list.get(0).get("similarOfferName")));
            out.put("similarOffer", list.isEmpty() ? Map.of() : list.get(0));
            out.put("similarOfferList", list);
            return out;
        } catch (Exception e) {
            return fallbackSimilar(desc, e);
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

    private Map<String, Object> fallbackSimilar(String desc, Exception e) {
        log.warn("[EopInterfaceAdapter] A1 real 调用失败，回退 mock: {}", e.getMessage());
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("businessDesc", desc);
        return withFallbackNote(fallback.similarOfferQuery(call), e);
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
            return fallback.auditFileUpload(req);
        }
        String url = base() + "/api/ah/cpcp/upload";
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
            String resp = client.post().uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            Map<String, Object> body = parse(resp);
            if (body.get("file_id") == null && body.get("sales_id") == null) {
                throw new IllegalStateException("B1 real 出参缺少 file_id/sales_id");
            }
            return body;
        } catch (Exception e) {
            Map<String, Object> out = fallback.auditFileUpload(req);
            return withFallbackNote(out, e);
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
        StringBuilder url = new StringBuilder(base()).append("/api/ah/cpcp/async_report_notify_direct")
                .append("?sales_id=").append(salesId)
                .append("&location=").append(location)
                .append("&sheet_name=").append(sheetName);
        if (!fileId.isBlank()) {
            url.append("&file_id=").append(fileId);
        }
        try {
            String resp = client.get().uri(url.toString()).retrieve().body(String.class);
            Map<String, Object> body = parse(resp);
            if (body.get("request_id") == null) {
                throw new IllegalStateException("B2 real 出参缺少 request_id");
            }
            return body;
        } catch (Exception e) {
            return withFallbackNote(fallback.auditAsyncNotify(req), e);
        }
    }

    /* ================= B3 插入 AI 稽核结果（{records[]} 裸体） ================= */

    @Override
    public Map<String, Object> auditResultInsert(Map<String, Object> req) {
        String url = base() + "/api/ahhui/cpcp/insertAiAuditResult";
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("records", req.get("records") == null ? List.of() : req.get("records"));
            String resp = client.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            Map<String, Object> body = parse(resp);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resultCode", MapOps.str(body.get("resultCode")));
            out.put("resultMsg", MapOps.str(body.get("resultMsg")));
            return out;
        } catch (Exception e) {
            return withFallbackNote(fallback.auditResultInsert(req), e);
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
        String url = base() + "/api/ah/slzx/invokeInfo/executeSqlService";
        try {
            Map<String, Object> requestObject = new LinkedHashMap<>();
            requestObject.put("sqlId", sqlId);
            requestObject.put("params", params);
            Map<String, Object> body = postEnvelope(url, requestObject);
            Map<String, Object> resultObject = envelopeResultObject(body);
            List<Map<String, Object>> rows = MapOps.castListOfMaps(resultObject.get("executeSqlResp"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resultCode", envelopeResultCode(body));
            out.put("resultMsg", envelopeResultMsg(body));
            out.put("execute_sql_resp", rows);
            return out;
        } catch (Exception e) {
            return withFallbackNote(fallback.auditSqlQuery(req), e);
        }
    }

    /* ================= C1 销售品测试发起（{offerId} 裸体） ================= */

    @Override
    public Map<String, Object> testOfferStart(Map<String, Object> req) {
        String offerId = firstNonBlank(MapOps.str(req.get("offerId")), MapOps.str(req.get("offer_id")));
        if (offerId.isBlank()) {
            return fail("1", "offerId 必填");
        }
        String url = base() + "/api/anhui/cpcp/offerTest";
        try {
            String resp = client.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("offerId", offerId))
                    .retrieve()
                    .body(String.class);
            Map<String, Object> body = parse(resp);
            Map<String, Object> resultObject = bareResultObject(body);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resultCode", MapOps.str(body.get("resultCode")));
            out.put("resultMsg", MapOps.str(body.get("resultMsg")));
            out.put("globalId", MapOps.str(resultObject.get("globalId")));
            return out;
        } catch (Exception e) {
            return withFallbackNote(fallbackTestStart(offerId), e);
        }
    }

    private Map<String, Object> fallbackTestStart(String offerId) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("offerId", offerId);
        return fallback.testOfferStart(call);
    }

    /* ================= C2 查询测试场景（{globalId} 裸体） ================= */

    @Override
    public Map<String, Object> testScenes(Map<String, Object> req) {
        String globalId = globalId(req);
        if (globalId.isBlank()) {
            return fail("1", "globalId 必填");
        }
        String url = base() + "/api/anhui/cpcp/getTestScenes";
        try {
            Map<String, Object> body = postBare(url, globalId, req);
            Map<String, Object> resultObject = bareResultObject(body);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resultCode", MapOps.str(body.get("resultCode")));
            out.put("resultMsg", MapOps.str(body.get("resultMsg")));
            out.put("testScenes", MapOps.castListOfMaps(resultObject.get("testScenes")));
            return out;
        } catch (Exception e) {
            return withFallbackNote(fallbackBare(globalId, "scenes"), e);
        }
    }

    /* ================= C3 查询测试进度（{globalId} 裸体） ================= */

    @Override
    public Map<String, Object> testProgress(Map<String, Object> req) {
        String globalId = globalId(req);
        if (globalId.isBlank()) {
            return fail("1", "globalId 必填");
        }
        String url = base() + "/api/anhui/cpcp/getTestProgress";
        try {
            Map<String, Object> body = postBare(url, globalId, req);
            Map<String, Object> resultObject = bareResultObject(body);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resultCode", MapOps.str(body.get("resultCode")));
            out.put("resultMsg", MapOps.str(body.get("resultMsg")));
            copyIfPresent(resultObject, out, "globalId", "totalSteps", "activeIndex", "done", "failed",
                    "failIndex", "totalSceneCount", "finishedSceneCount", "failedSceneCount");
            return out;
        } catch (Exception e) {
            return withFallbackNote(fallbackBare(globalId, "progress"), e);
        }
    }

    /* ================= C4 查询测试结果（{globalId} 裸体） ================= */

    @Override
    public Map<String, Object> testResult(Map<String, Object> req) {
        String globalId = globalId(req);
        if (globalId.isBlank()) {
            return fail("1", "globalId 必填");
        }
        String url = base() + "/api/anhui/cpcp/getTestResult";
        try {
            Map<String, Object> body = postBare(url, globalId, req);
            Map<String, Object> resultObject = bareResultObject(body);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resultCode", MapOps.str(body.get("resultCode")));
            out.put("resultMsg", MapOps.str(body.get("resultMsg")));
            copyIfPresent(resultObject, out, "testRequestId", "testRequestName", "offerId", "offerName", "globalId");
            out.put("testScenes", MapOps.castListOfMaps(resultObject.get("testScenes")));
            return out;
        } catch (Exception e) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("globalId", globalId);
            call.put("external_base_url", MapOps.str(req.get("external_base_url")));
            return withFallbackNote(fallback.testResult(call), e);
        }
    }

    private Map<String, Object> fallbackBare(String globalId, String kind) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("globalId", globalId);
        return switch (kind) {
            case "scenes" -> fallback.testScenes(call);
            case "progress" -> fallback.testProgress(call);
            default -> fallback.testResult(call);
        };
    }

    private Map<String, Object> postBare(String url, String globalId, Map<String, Object> req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("globalId", globalId);
        String resp = client.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
        return parse(resp);
    }

    private String globalId(Map<String, Object> req) {
        return firstNonBlank(MapOps.str(req.get("globalId")), MapOps.str(req.get("global_id")));
    }

    /* ---------------- 信封组装/解包 ---------------- */

    /** A1/B4：{contractRoot{tcpCont 固定头 + svcCont.requestObject}}，POST JSON。 */
    private Map<String, Object> postEnvelope(String url, Map<String, Object> requestObject) {
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
        tcpCont.put("appKey", eop.getAppKey());
        tcpCont.put("dstSysId", eop.getDstSysId());
        tcpCont.put("sign", eop.getSign());

        Map<String, Object> svcCont = new LinkedHashMap<>();
        svcCont.put("requestObject", requestObject);

        Map<String, Object> contractRoot = new LinkedHashMap<>();
        contractRoot.put("tcpCont", tcpCont);
        contractRoot.put("svcCont", svcCont);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractRoot", contractRoot);

        String resp = client.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
        return parse(resp);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> envelopeResultObject(Map<String, Object> body) {
        Object cr = body.get("contractRoot");
        if (cr instanceof Map<?, ?> root) {
            Object svc = root.get("svcCont");
            if (svc instanceof Map<?, ?> svcCont && svcCont.get("resultObject") instanceof Map<?, ?> ro) {
                return (Map<String, Object>) ro;
            }
        }
        return new LinkedHashMap<>();
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
    private Map<String, Object> bareResultObject(Map<String, Object> body) {
        if (body.get("resultObject") instanceof Map<?, ?> ro) {
            return (Map<String, Object>) ro;
        }
        return new LinkedHashMap<>();
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

    private Map<String, Object> withFallbackNote(Map<String, Object> out, Exception e) {
        Map<String, Object> safe = out == null ? new LinkedHashMap<>() : out;
        safe.put("note", "已回退模拟（real 接口不可达：" + e.getMessage() + "）");
        safe.put("mode", "real-fallback-mock");
        return safe;
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

    private String base() {
        String b = props.getEop().getBaseUrl();
        if (b == null || b.isBlank()) {
            throw new IllegalStateException("prodai.appstore.interface.eop.base-url 未配置");
        }
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
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
