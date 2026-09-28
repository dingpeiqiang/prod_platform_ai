package com.sitech.prodai.service.appstore;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sitech.prodai.config.AppStoreInterfaceProperties;
import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 适配层 real 实现（现场环境，统一方案 §4.5.4/§4.5.5）：业务报文直接 POST 到 EOP 业务地址
 * （{@code base-url + paths.<接口键>}），HTTP 请求头携带鉴权：
 * {@code X-APP-ID} / {@code X-APP-KEY}（应用标识） / {@code Api-Key}（该接口 EOP ApiID） / {@code Staff-Code}（员工编码）。
 * <p>
 * 业务报文差异按接口区分：A1/B4 为 {@code contractRoot.tcpCont + svcCont.requestObject}；
 * B2 query 参数；B3 {@code {records[]}} 裸体；C1~C4 {@code {offerId}}/{@code globalId} 裸体。
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
    /** B3 batchId 规范时间戳（yyyyMMddHHmmss）。 */
    private static final DateTimeFormatter BATCH_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** real 调用失败统一错误码（不回退 mock）。 */
    private static final String ERR_REAL_CALL_FAILED = "REAL_CALL_FAILED";

    /** 鉴权 HTTP 请求头名。 */
    private static final String HEADER_APP_ID = "X-APP-ID";
    private static final String HEADER_APP_KEY = "X-APP-KEY";
    private static final String HEADER_API_KEY = "Api-Key";
    private static final String HEADER_STAFF_CODE = "Staff-Code";

    private final AppStoreInterfaceProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient client;
    /** 存量种子里程碑：real 出参缺 offerInfo/offerModel/offer_group 时按模板/类型衍生补全（全量配置 JSON 兜底）。 */
    private final OfferSeedService seed;
    private final OfferGroupSeedService groupSeed;

    public EopInterfaceAdapter(AppStoreInterfaceProperties props, ObjectMapper objectMapper,
                               OfferSeedService seed, OfferGroupSeedService groupSeed) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.seed = seed;
        this.groupSeed = groupSeed;
        int timeout = Math.max(1000, props.getEop().getTimeoutMs());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        // EOP 响应头 Content-Type 未携带 charset 时，StringHttpMessageConverter 默认按 ISO-8859-1 解码，
        // 导致 errmsg/errdetail 中文乱码（如 «å å¡ç« å»）。显式以 UTF-8 为默认字符集构造转换器。
        StringHttpMessageConverter stringConverter =
                new StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8);
        this.client = RestClient.builder()
                .requestFactory(factory)
                .messageConverters(converters -> converters.add(0, stringConverter))
                .build();
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
        String templateId = firstNonBlank(MapOps.str(req.get("templateId")), MapOps.str(req.get("template_id")));
        String url = businessUrl("similar");
        long begin = System.currentTimeMillis();
        logRealBegin("similar", "POST", url);
        try {
            Map<String, Object> requestObject = new LinkedHashMap<>();
            requestObject.put("businessDesc", desc);
            Map<String, Object> body = postJson("similar", url, envelopeRequest(requestObject));
            Map<String, Object> resultObject = envelopeResultObject("similar", body);
            List<Map<String, Object>> list = normalizeSimilarList(resultObject, templateId);
            Map<String, Object> out = ok();
            out.put("resultMsg", list.isEmpty() ? "未命中相似销售品"
                    : "命中相似销售品 " + list.size() + " 个，主推荐：" + MapOps.str(list.get(0).get("similarOfferName")));
            out.put("similarOffer", list.isEmpty() ? Map.of() : list.get(0));
            out.put("similarOfferList", list);
            // 融合组下发：real 出参不含 offer_group，按主推荐 offer_id 命中存量融合组时补全，保证组链路与 mock 同构
            if (!list.isEmpty()) {
                Map<String, Object> offerGroup = offerGroupOf(list);
                if (offerGroup != null) {
                    out.put("offer_group", offerGroup);
                }
            }
            logRealDone("similar", System.currentTimeMillis() - begin, out);
            return out;
        } catch (Exception e) {
            return realFail("similar", url, e);
        }
    }

    /**
     * A1 real 出参归一：保留 camelCase 四字段（similarOfferId/Name/Score/Desc），
     * 并按 templateId 从存量同类型产品衍生补全 offerInfo/offerModel（全量配置 JSON）——
     * real 网关不返回这两项，缺失会导致需求分析模板轨 merge_nested 无值可补，故此处兜底衍生。
     */
    private List<Map<String, Object>> normalizeSimilarList(Map<String, Object> resultObject, String templateId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : MapOps.castListOfMaps(resultObject.get("similarOfferList"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            String offerId = MapOps.str(item.get("similarOfferId"));
            row.put("similarOfferId", offerId);
            row.put("similarOfferName", MapOps.str(item.get("similarOfferName")));
            row.put("similarityScore", MapOps.str(item.get("similarityScore")));
            row.put("similarityDesc", MapOps.str(item.get("similarityDesc")));
            enrichConfig(offerId, templateId, row);
            out.add(row);
        }
        return out;
    }

    /** 衍生补全全量配置 JSON：存量品优先取自身 seed 报文/24 字段，缺模板时按产品类型衍生同类型值源。 */
    private void enrichConfig(String offerId, String templateId, Map<String, Object> row) {
        Map<String, Object> offer = seed.findOffer(offerId);
        if (offer != null && !offer.isEmpty()) {
            row.put("offerInfo", seed.toFields24(offer));
        }
        if (templateId != null && !templateId.isBlank()) {
            Map<String, Object> model = seed.reportOfOrDerive(offerId, templateId);
            if (model != null && !model.isEmpty()) {
                row.put("offerModel", model);
            }
        }
    }

    /** 融合组下发：主推荐 offer_id 命中存量融合组时返回组定义，否则 null。 */
    private Map<String, Object> offerGroupOf(List<Map<String, Object>> list) {
        Map<String, Object> group = groupSeed.findGroup(MapOps.str(list.get(0).get("similarOfferId")));
        return group == null || group.isEmpty() ? null : group;
    }

    /* ================= B1 稽核文件上传 ================= */

    @Override
    public Map<String, Object> auditFileUpload(Map<String, Object> req) {
        String reqId = firstNonBlank(MapOps.str(req.get("req_id")), MapOps.str(req.get("reqId")));
        String chatId = firstNonBlank(MapOps.str(req.get("chat_id")), MapOps.str(req.get("chatId")));
        Object bytesObj = req.get("bytes");
        byte[] bytes = bytesObj instanceof byte[] b ? b : null;
        String fileName = MapOps.str(firstNonBlank(MapOps.str(req.get("fileName")), MapOps.str(req.get("file_name"))));
        String url = uploadUrl();
        long begin = System.currentTimeMillis();
        logRealBegin("upload", "POST", url);
        try {
            Map<String, Object> body;
            if (bytes != null && bytes.length > 0) {
                if (fileName.isBlank()) {
                    fileName = MapOps.str(reqId) + ".xlsx";
                }
                // 先落盘暂存（文件名附加时间戳）到 upload-dir，再读取该文件上传。
                fileName = stageFile(fileName, bytes);
                bytes = readStaged(uploadDir(), fileName);
                // multipart/form-data 上传，文件字段名由配置指定（默认 file），
                // req_id/chat_id 作为普通表单字段随文件一并提交。
                body = postMultipart("upload", url, reqId, chatId, fileName, bytes);
            } else if (!reqId.isBlank()) {
                // 兼容形态：无文件字节时仅提交 {req_id, chat_id}（服务侧自行生成文件）
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("req_id", reqId);
                if (!chatId.isBlank()) {
                    payload.put("chat_id", chatId);
                }
                body = postJson("upload", url, payload);
            } else {
                return fail(ERR_REAL_CALL_FAILED, "上传文件缺失：real 模式必须提供文件字节流（或 req_id）");
            }
            if (body.get("file_id") == null && body.get("sales_id") == null) {
                throw new IllegalStateException("B1 real 出参缺少 file_id/sales_id");
            }
            logRealDone("upload", System.currentTimeMillis() - begin, body);
            return body;
        } catch (Exception e) {
            return realFail("upload", url, e);
        }
    }

    /* ================= B2 异步报告生成并通知 ================= */

    @Override
    public Map<String, Object> auditAsyncNotify(Map<String, Object> req) {
        String salesId = firstNonBlank(MapOps.str(req.get("sales_id")), MapOps.str(req.get("salesId")));
        if (salesId.isBlank()) {
            return fail("1", "sales_id 必填");
        }
        String location = firstNonBlank(MapOps.str(req.get("location")), "0");
        String sheetName = firstNonBlank(MapOps.str(req.get("sheet_name")), MapOps.str(req.get("sheetName")), "套餐");
        String fileId = firstNonBlank(MapOps.str(req.get("file_id")), MapOps.str(req.get("fileId")));
        String url = businessUrl("notify");
        long begin = System.currentTimeMillis();
        logRealBegin("notify", "GET", url);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sales_id", salesId);
            payload.put("location", location);
            payload.put("sheet_name", sheetName);
            if (!fileId.isBlank()) {
                payload.put("file_id", fileId);
            }
            Map<String, Object> body = getJson("notify", url, payload);
            if (body.get("request_id") == null) {
                throw new IllegalStateException("B2 real 出参缺少 request_id");
            }
            logRealDone("notify", System.currentTimeMillis() - begin, body);
            return body;
        } catch (Exception e) {
            return realFail("notify", url, e);
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
            payload.put("records", normalizeInsertRecords(req.get("records")));
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

    /**
     * B3 入参归一：工作流（wf_sub_03 节点204）发送的记录字段为内部口径
     * （{@code sales_id/file_id/request_id/status}），EOP 规范要求
     * （{@code batchId/reqOrderId/offerId/offerName/requestId/staffId}）。
     * 此处做字段名映射；已是 EOP 规范字段的记录原样透传，保证 mock/real 同构。
     */
    private List<Map<String, Object>> normalizeInsertRecords(Object records) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> src : MapOps.castListOfMaps(records)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("batchId", firstNonBlank(MapOps.str(src.get("batchId")), MapOps.str(src.get("batch_id")),
                    MapOps.str(src.get("file_id")), MapOps.str(src.get("fileId")), nowBatchId()));
            row.put("reqOrderId", firstNonBlank(MapOps.str(src.get("reqOrderId")), MapOps.str(src.get("req_order_id")),
                    MapOps.str(src.get("request_id")), MapOps.str(src.get("requestId")), "-1"));
            row.put("offerId", firstNonBlank(MapOps.str(src.get("offerId")), MapOps.str(src.get("offer_id")),
                    MapOps.str(src.get("sales_id")), MapOps.str(src.get("salesId"))));
            row.put("offerName", firstNonBlank(MapOps.str(src.get("offerName")), MapOps.str(src.get("offer_name"))));
            row.put("requestId", firstNonBlank(MapOps.str(src.get("requestId")), MapOps.str(src.get("request_id"))));
            row.put("staffId", firstNonBlank(MapOps.str(src.get("staffId")), MapOps.str(src.get("staff_id"))));
            normalized.add(row);
        }
        return normalized;
    }

    /** EOP 规范 batchId 格式：{@code yyyyMMddHHmmss + 4位随机数}。 */
    private String nowBatchId() {
        return LocalDateTime.now().format(BATCH_STAMP) + String.format("%04d", ThreadLocalRandom.current().nextInt(0, 10000));
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
            List<Map<String, Object>> scenes = MapOps.castListOfMaps(resultObject.get("testScenes"));
            out.put("testScenes", scenes);
            backfillAcceptanceProof(resultObject, scenes, out);
            logRealDone("testResult", System.currentTimeMillis() - begin, out);
            return out;
        } catch (Exception e) {
            return realFail("testResult", url, e);
        }
    }

    /**
     * C4 受理凭证回填：EOP 规范中 {@code orderId}/{@code offerInstId} 位于每个 testScene 内，
     * 而 mock（{@link OfferSimV16Service#testResult}）与下游消费方（wf_sub_13 节点2305/2315）
     * 均按顶层读取。此处从首个携带该字段的场景回填顶层，保证 mock/real 同构、受理凭证不缺失。
     */
    private void backfillAcceptanceProof(Map<String, Object> resultObject,
                                         List<Map<String, Object>> scenes, Map<String, Object> out) {
        if (out.get("orderId") == null) {
            String orderId = firstSceneField(resultObject, scenes, "orderId");
            if (!orderId.isBlank()) {
                out.put("orderId", orderId);
            }
        }
        if (out.get("offerInstId") == null) {
            String offerInstId = firstSceneField(resultObject, scenes, "offerInstId");
            if (!offerInstId.isBlank()) {
                out.put("offerInstId", offerInstId);
            }
        }
    }

    /** 依次从 resultObject 顶层、各 testScene 内取首个非空字段值（EOP 字段位置差异兼容）。 */
    private String firstSceneField(Map<String, Object> resultObject, List<Map<String, Object>> scenes, String field) {
        String top = MapOps.str(resultObject.get(field));
        if (!top.isBlank()) {
            return top;
        }
        for (Map<String, Object> scene : scenes) {
            String v = MapOps.str(scene.get(field));
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private Map<String, Object> postBare(String key, String url, String globalId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("globalId", globalId);
        return postJson(key, url, payload);
    }

    private String globalId(Map<String, Object> req) {
        return firstNonBlank(MapOps.str(req.get("globalId")), MapOps.str(req.get("global_id")));
    }

    /* ---------------- EOP 直连（调用 / 严格解析） ---------------- */

    /** POST JSON：请求头带 X-APP-ID/X-APP-KEY/Api-Key/Staff-Code 鉴权，解析并严格校验业务响应。 */
    private Map<String, Object> postJson(String key, String url, Map<String, Object> payload) {
        logRealRequest(key, url, payload);
        long start = System.currentTimeMillis();
        String resp;
        try {
            resp = client.post().uri(url)
                    .headers(headers -> applyAuth(headers, key))
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
        assertNoEopError(key, url, body);
        return body;
    }

    /**
     * GET JSON：业务参数以 query 传参，请求头带 X-APP-ID/X-APP-KEY/Api-Key/Staff-Code 鉴权，
     * 解析并严格校验业务响应（B2 通知接口按 EOP 规范使用 GET）。
     */
    private Map<String, Object> getJson(String key, String url, Map<String, Object> query) {
        logRealRequest(key, url, query);
        long start = System.currentTimeMillis();
        String resp;
        try {
            resp = client.get().uri(url,
                            uriBuilder -> {
                                query.forEach((k, v) -> uriBuilder.queryParam(k, v));
                                return uriBuilder.build();
                            })
                    .headers(headers -> applyAuth(headers, key))
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            logRealFailure(key, url, System.currentTimeMillis() - start, e);
            throw e;
        }
        logRealResponse(key, url, System.currentTimeMillis() - start, resp);
        Map<String, Object> body = parse(resp);
        assertNoEopError(key, url, body);
        return body;
    }

    /**
     * B1 上传前落盘暂存：把生成的稽核 Excel 写入 {@code eop.upload-dir}，文件名附加时间戳
     * {@code _yyyyMMddHHmmss}（如 {@code 稽核需求_20260923163045.xlsx}）。目录不存在则自动创建。
     *
     * @return 落盘后的文件名（含时间戳）
     */
    private String stageFile(String fileName, byte[] bytes) throws IOException {
        String stamped = withTimestamp(fileName);
        Path dir = Paths.get(uploadDir());
        Files.createDirectories(dir);
        Path target = dir.resolve(stamped);
        Files.write(target, bytes);
        log.info("[EopInterfaceAdapter][真实·暂存] B1 稽核文件已落盘 path={} bytes={}", target, bytes.length);
        return stamped;
    }

    /** 读取已暂存的上传文件字节流。 */
    private byte[] readStaged(String dir, String fileName) throws IOException {
        return Files.readAllBytes(Paths.get(dir).resolve(fileName));
    }

    /** 落盘目录（未配置回退默认值）。 */
    private String uploadDir() {
        String dir = props.getEop().getUploadDir();
        return dir == null || dir.isBlank() ? "/data/stq/crmpos/crm-pgcent-mng/upload" : dir.trim();
    }

    /** 文件名附加时间戳：原名（含扩展名）→ {@code 原名_yyyyMMddHHmmss.ext}。 */
    private String withTimestamp(String fileName) {
        String name = fileName == null || fileName.isBlank() ? "稽核需求.xlsx" : fileName.trim();
        String stamp = LocalDateTime.now().format(BATCH_STAMP);
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot) + "_" + stamp + name.substring(dot);
        }
        return name + "_" + stamp;
    }

    /**
     * POST multipart/form-data：文件以配置字段名（默认 {@code file}）提交，req_id/chat_id 作为普通表单字段，
     * 请求头同样携带 X-APP-ID/X-APP-KEY/Api-Key/Staff-Code 鉴权，解析并严格校验业务响应。
     */
    private Map<String, Object> postMultipart(String key, String url, String reqId, String chatId,
                                              String fileName, byte[] bytes) {
        logRealRequest(key, url, "multipart file=" + fileName + " bytes=" + bytes.length
                + " req_id=" + reqId + " chat_id=" + chatId);
        long start = System.currentTimeMillis();
        String field = props.getEop().getUploadFileField();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add(field, new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        if (!reqId.isBlank()) {
            form.add("req_id", reqId);
        }
        if (!chatId.isBlank()) {
            form.add("chat_id", chatId);
        }
        String resp;
        try {
            resp = client.post().uri(url)
                    .headers(headers -> applyAuth(headers, key))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            logRealFailure(key, url, System.currentTimeMillis() - start, e);
            throw e;
        }
        logRealResponse(key, url, System.currentTimeMillis() - start, resp);
        Map<String, Object> body = parse(resp);
        assertNoEopError(key, url, body);
        return body;
    }

    /** 附加 EOP 鉴权请求头：X-APP-ID / X-APP-KEY / Api-Key（该接口 ApiID） / Staff-Code。 */
    private void applyAuth(HttpHeaders headers, String key) {
        AppStoreInterfaceProperties.Eop eop = props.getEop();
        headers.set(HEADER_APP_ID, nullSafe(eop.getAppId()));
        headers.set(HEADER_APP_KEY, nullSafe(eop.getAppKey()));
        headers.set(HEADER_API_KEY, nullSafe(eop.apiKey(key)));
        headers.set(HEADER_STAFF_CODE, nullSafe(eop.getStaffCode()));
    }

    /** A1/B4：{contractRoot{tcpCont 固定头 + svcCont.requestObject}} 业务报文（鉴权头已在 HTTP 头）。 */
    private Map<String, Object> envelopeRequest(Map<String, Object> requestObject) {
        AppStoreInterfaceProperties.Eop eop = props.getEop();
        String stamp = LocalDateTime.now().format(STAMP);
        String transactionId = stamp + String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));

        Map<String, Object> tcpCont = new LinkedHashMap<>();
        tcpCont.put("apiCode", "");
        tcpCont.put("transactionId", transactionId);
        tcpCont.put("svcCode", nullSafe(eop.getSvcCode()));
        tcpCont.put("reqTime", stamp);
        tcpCont.put("globalId", transactionId);
        tcpCont.put("version", "V1.0");
        tcpCont.put("appKey", nullSafe(eop.getEnvelopeAppKey()));
        tcpCont.put("dstSysId", nullSafe(eop.getDstSysId()));
        tcpCont.put("sign", nullSafe(eop.getSign()));

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
     * 严格校验 EOP 业务错误：响应含 {@code errcode}/{@code errmsg}/{@code errdetail}（如鉴权失败 errcode=242）
     * 时抛出携带原始错误码/信息/详情的异常，由 {@link #realFail} 归一为 {@code REAL_CALL_FAILED}，
     * 避免把业务错误静默降级为"未命中"。errdetail 常含下游原始返回（如 405 页面片段），须一并透出便于定位。
     * <p>
     * 当 {url} 为 B1 普通直调地址（非 EOP 网关）时，下游响应结构不含 errcode/errmsg，跳过该校验。
     */
    private void assertNoEopError(String key, String url, Map<String, Object> body) {
        if (isPlainUpload(url)) {
            return;
        }
        String errcode = firstNonBlank(MapOps.str(body.get("errcode")), MapOps.str(body.get("errorCode")));
        String errmsg = firstNonBlank(MapOps.str(body.get("errmsg")), MapOps.str(body.get("errorMsg")));
        String errdetail = firstNonBlank(MapOps.str(body.get("errdetail")), MapOps.str(body.get("errorDetail")),
                MapOps.str(body.get("errDetail")));
        if (!errcode.isBlank() || !errmsg.isBlank() || !errdetail.isBlank()) {
            StringBuilder msg = new StringBuilder("EOP 业务错误 [").append(key).append("]")
                    .append(" errcode=").append(errcode)
                    .append(" errmsg=").append(errmsg);
            if (!errdetail.isBlank()) {
                msg.append(" errdetail=").append(errdetail);
            }
            throw new IllegalStateException(msg.toString());
        }
    }

    /** B1 上传地址：配置了 upload-url 则直调该绝对地址，否则回落 base-url + paths.upload。 */
    private String uploadUrl() {
        String u = props.getEop().getUploadUrl();
        if (u != null && !u.isBlank()) {
            return u.trim();
        }
        return businessUrl("upload");
    }

    /** 判断给定地址是否为 B1 普通直调上传地址（用于跳过 EOP 专有的 errcode/errmsg 校验）。 */
    private boolean isPlainUpload(String url) {
        String u = props.getEop().getUploadUrl();
        return u != null && !u.isBlank() && u.trim().equals(url);
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
            return LENIENT_MAPPER.readValue(resp, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("EOP 响应非 JSON: " + e.getMessage(), e);
        }
    }

    /**
     * EOP 响应解析专用宽松 ObjectMapper：EOP 的 {@code errmsg}/{@code errdetail} 会把下游原始返回
     * （如 HTML 错误页）原样透传，其中含未转义的换行/控制字符，标准 JSON 解析会直接失败。
     * 放开字符串内控制字符，保证真实业务错误（errcode/errmsg）能被透出，而非被掩盖成「响应非 JSON」。
     * 仅用于响应解析，不影响全局注入的 {@code objectMapper} 及请求序列化。
     */
    private static final ObjectMapper LENIENT_MAPPER = JsonMapper.builder()
            .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
            .build();

    /* ---------------- real 调用日志（mode=real 时输出完整过程与出入参，统一标注"[真实]"） ---------------- */

    /** 调用开始：接口键 + 方法 + 业务地址 + 生效配置（base-url/超时/鉴权头脱敏）。*/
    private void logRealBegin(String key, String method, String url) {
        AppStoreInterfaceProperties.Eop eop = props.getEop();
        log.info("[EopInterfaceAdapter][真实·BEGIN] key={} {} url={} base-url={} timeout={}ms "
                        + "X-APP-ID={} X-APP-KEY={} Api-Key={} Staff-Code={}",
                key, method, url, eop.getBaseUrl(), eop.getTimeoutMs(),
                mask(eop.getAppId()), mask(eop.getAppKey()), eop.apiKey(key), eop.getStaffCode());
    }

    /** 待发送业务报文（鉴权头已在请求头，此处只记 body）。*/
    private void logRealRequest(String key, String url, Object body) {
        log.info("[EopInterfaceAdapter][真实→EOP] key={} POST {}", key, url);
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

    private String nullSafe(String v) {
        return v == null ? "" : v;
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
