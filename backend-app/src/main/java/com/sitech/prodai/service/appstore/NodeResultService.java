package com.sitech.prodai.service.appstore;

import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 产销品加载 AI 应用 · 节点结果存储/查询服务（save_node_result / query_node_result 插件后端）。
 * <p>
 * 契约见《产销品加载-节点结果存储查询插件-接口设计文档.md》：
 * key 设计 = req_id + node_name；同键覆盖（支持重跑环节）；result_json 透传存储不做格式校验解析；
 * code=0 成功，非 0 业务失败（与现有 11 个插件一致）。
 * <p>
 * 线程安全：ConcurrentHashMap + 方法级同步写入。
 */
@Service
public class NodeResultService {

    private static final Logger log = LoggerFactory.getLogger(NodeResultService.class);

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern REQ_ID_PATTERN = Pattern.compile("^REQ-\\d{8}-\\d{3}$");
    private static final int MAX_RESULT_JSON_BYTES = 64 * 1024;
    private static final List<String> NODE_NAMES = List.of(
            "requirement", "config", "spec", "fee", "test", "acceptance", "approval", "monitor");
    private static final List<String> STATUS_VALUES = List.of("ok", "failed", "rejected");

    /** 节点结果记录：record_id -> 记录 */
    private final Map<String, Map<String, Object>> results = new ConcurrentHashMap<>();
    /** 记录 ID 序号 */
    private final AtomicLong seqRecord = new AtomicLong(0L);

    /**
     * 接口一：节点结果存储 save_node_result。
     *
     * @return {code, msg, record_id, saved_at} 或 {code, msg} 失败体
     */
    public synchronized Map<String, Object> save(String reqId, String nodeName, String resultJson, String status) {
        String req = reqId == null ? "" : reqId.trim();
        if (!REQ_ID_PATTERN.matcher(req).matches()) {
            return fail(5002, "invalid req_id format");
        }
        String node = nodeName == null ? "" : nodeName.trim();
        if (!NODE_NAMES.contains(node)) {
            return fail(5003, "invalid node_name");
        }
        String result = resultJson == null ? "" : resultJson;
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_RESULT_JSON_BYTES) {
            return fail(5004, "result_json too large");
        }
        String status0 = status == null || status.isBlank() ? "ok" : status.trim().toLowerCase();
        if (!STATUS_VALUES.contains(status0)) {
            return fail(5005, "invalid status");
        }

        // 同键覆盖：req_id + node_name 唯一确定一条记录（支持重跑环节）
        Map<String, Object> existed = findLatest(req, node);
        String ts = LocalDateTime.now().format(TS);
        String createTime = existed == null ? ts : MapOps.str(existed.get("create_time"));
        if (existed != null) {
            results.remove(MapOps.str(existed.get("record_id")));
            log.info("[NodeResultService] 覆盖旧记录 req_id={} node_name={}", req, node);
        }

        String recordId = nextRecordId();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("record_id", recordId);
        record.put("req_id", req);
        record.put("node_name", node);
        record.put("result_json", result);
        record.put("status", status0);
        record.put("create_time", createTime);
        record.put("update_time", ts);
        results.put(recordId, record);
        log.info("[NodeResultService] 保存节点结果 record_id={} req_id={} node_name={} status={}",
                recordId, req, node, status0);

        Map<String, Object> body = ok("record_id", recordId);
        body.put("saved_at", ts);
        return body;
    }

    /**
     * 接口二：节点结果查询 query_node_result。
     *
     * @return {code, msg, total, list}；total=0 表示无记录（不算失败）
     */
    public Map<String, Object> query(String reqId, String nodeName, String latestOnly) {
        String req = reqId == null ? "" : reqId.trim();
        if (!REQ_ID_PATTERN.matcher(req).matches()) {
            return fail(5002, "invalid req_id format");
        }
        String node = nodeName == null ? "" : nodeName.trim();
        if (!node.isEmpty() && !NODE_NAMES.contains(node)) {
            return fail(5003, "invalid node_name");
        }

        List<Map<String, Object>> hit = new ArrayList<>();
        for (Map<String, Object> r : results.values()) {
            if (!MapOps.str(r.get("req_id")).equals(req)) {
                continue;
            }
            if (!node.isEmpty() && !MapOps.str(r.get("node_name")).equals(node)) {
                continue;
            }
            hit.add(snapshot(r));
        }
        hit.sort(Comparator.comparing((Map<String, Object> r) -> MapOps.str(r.get("update_time"))).reversed());

        List<Map<String, Object>> list;
        // latest_only 默认 "1"：只返回每个环节最新一条；"0" 返回历史全部版本
        if (!"0".equals(latestOnly)) {
            Map<String, String> picked = new LinkedHashMap<>();
            list = new ArrayList<>();
            for (Map<String, Object> r : hit) {
                String n = MapOps.str(r.get("node_name"));
                if (picked.put(n, MapOps.str(r.get("record_id"))) != null) {
                    continue;
                }
                list.add(r);
            }
        } else {
            list = hit;
        }

        Map<String, Object> body = ok("total", list.size());
        body.put("list", list);
        return body;
    }

    /* ---------------- 工具 ---------------- */

    private Map<String, Object> findLatest(String reqId, String nodeName) {
        Map<String, Object> latest = null;
        for (Map<String, Object> r : results.values()) {
            if (!MapOps.str(r.get("req_id")).equals(reqId)
                    || !MapOps.str(r.get("node_name")).equals(nodeName)) {
                continue;
            }
            if (latest == null || MapOps.str(r.get("update_time"))
                    .compareTo(MapOps.str(latest.get("update_time"))) > 0) {
                latest = r;
            }
        }
        return latest;
    }

    private Map<String, Object> snapshot(Map<String, Object> record) {
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put("req_id", record.get("req_id"));
        copy.put("node_name", record.get("node_name"));
        copy.put("result_json", record.get("result_json"));
        copy.put("status", record.get("status"));
        copy.put("create_time", record.get("create_time"));
        copy.put("update_time", record.get("update_time"));
        return copy;
    }

    private String nextRecordId() {
        return "REC" + LocalDateTime.now().format(DATE) + String.format("%06d", seqRecord.incrementAndGet());
    }

    /** 统一成功响应：{code:0, msg:"success", ...业务字段}，与 AppMockStore 契约一致 */
    private static Map<String, Object> ok(String key, Object value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("msg", "success");
        body.put(key, value);
        return body;
    }

    /** 统一失败响应：{code:<非0>, msg:<原因>} */
    private static Map<String, Object> fail(int code, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        return body;
    }
}
