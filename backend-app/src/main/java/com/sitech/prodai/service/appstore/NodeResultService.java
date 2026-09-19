package com.sitech.prodai.service.appstore;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sitech.prodai.domain.entity.NodeResultRecord;
import com.sitech.prodai.mapper.NodeResultMapper;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 产销品加载 AI 应用 · 节点结果存储/查询服务（save_node_result / query_node_result 插件后端）。
 * <p>
 * 契约见《产销品加载-节点结果存储查询插件-接口设计文档.md》：
 * key 设计 = req_id + node_name；同键覆盖（支持重跑环节）；result_json 透传存储不做格式校验解析；
 * code=0 成功，非 0 业务失败（与现有 11 个插件一致）。
 * <p>
 * 持久化：MyBatis-Plus 落库 pd_ai_node_results（H2/MySQL 同构 DDL，见 sql/ 脚本），
 * 重启不丢失；同键覆盖采用「查最新→删除旧记录→插入新记录」语义，与原内存版一致。
 */
@Service
public class NodeResultService {

    private static final Logger log = LoggerFactory.getLogger(NodeResultService.class);

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_RESULT_JSON_BYTES = 64 * 1024;

    private final NodeResultMapper mapper;
    /** 记录 ID 序号（重启后基于当日已有记录数续排，防重复） */
    private final AtomicLong seqRecord = new AtomicLong(0L);

    public NodeResultService(NodeResultMapper mapper) {
        this.mapper = mapper;
        // 启动时把自增序号拨到当前最大值之后，保证 record_id 全局唯一
        String today = DATE.format(LocalDateTime.now());
        NodeResultRecord latest = mapper.selectOne(new LambdaQueryWrapper<NodeResultRecord>()
                .likeRight(NodeResultRecord::getRecordId, "REC" + today)
                .orderByDesc(NodeResultRecord::getId)
                .last("LIMIT 1"));
        if (latest != null) {
            String rid = latest.getRecordId();
            try {
                seqRecord.set(Long.parseLong(rid.substring(("REC" + today).length())));
            } catch (NumberFormatException ignore) {
                // 历史记录 ID 格式异常时从 0 起排，record_id 冲突概率极低（含秒级日期+6位序号）
            }
        }
    }

    /**
     * 接口一：节点结果存储 save_node_result。
     * <p>
     * V1.7 唯一性保障：req_id 须为 PLAN+yyyyMMddHHmmss+3位随机数 格式（LLM 按当前时刻生成，每次不同）；
     * requirement 环节（执行方案）重写时，若同 req_id 下已存在旧方案（无论内容是否相同），
     * 一律删除旧记录后重新插入（同键覆盖语义），避免旧方案与确认标记/环节结果错位。
     *
     * @return {code, msg, record_id, saved_at} 或 {code, msg} 失败体
     */
    public synchronized Map<String, Object> save(String reqId, String nodeName, String resultJson, String status) {
        String req = reqId == null ? "" : reqId.trim();
        if (!validReqId(req)) {
            return fail(5002, "invalid req_id format: " + req + "（须为 PLAN+yyyyMMddHHmmss+3位随机数）");
        }
        String node = nodeName == null ? "" : nodeName.trim();
        if (node.isEmpty()) {
            return fail(5003, "invalid node_name");
        }
        String result = resultJson == null ? "" : resultJson;
        return doSave(req, node, null, result, status);
    }

    /**
     * V1.7 统一键格式校验：PLAN + 14位时间戳 + 3位随机数（共 19 位数字字母组合）。
     * LLM 生成端保证取当前真实时刻；此处兜底拦截照抄示例值/历史值/非法格式。
     */
    private boolean validReqId(String reqId) {
        return reqId != null && reqId.matches("PLAN\\d{17}");
    }

    /**
     * 接口二：节点结果查询 query_node_result。
     *
     * @return {code, msg, total, list}；total=0 表示无记录（不算失败）
     */
    public Map<String, Object> query(String reqId, String nodeName, String latestOnly) {
        String req = reqId == null ? "" : reqId.trim();
        if (req.isEmpty()) {
            return fail(5002, "invalid req_id format");
        }
        String node = nodeName == null ? "" : nodeName.trim();

        List<NodeResultRecord> hit = mapper.selectList(new LambdaQueryWrapper<NodeResultRecord>()
                .eq(NodeResultRecord::getReqId, req)
                .eq(!node.isEmpty(), NodeResultRecord::getNodeName, node));

        List<Map<String, Object>> list;
        // latest_only 默认 "1"：只返回每个环节最新一条；"0" 返回历史全部版本
        if (!"0".equals(latestOnly)) {
            Map<String, NodeResultRecord> picked = new LinkedHashMap<>();
            hit.sort(Comparator.comparing((NodeResultRecord r) -> tsOf(r)).reversed());
            for (NodeResultRecord r : hit) {
                picked.putIfAbsent(r.getNodeName() == null ? "" : r.getNodeName(), r);
            }
            list = new ArrayList<>();
            for (NodeResultRecord r : picked.values()) {
                list.add(snapshot(r));
            }
        } else {
            hit.sort(Comparator.comparing((NodeResultRecord r) -> tsOf(r)).reversed());
            list = new ArrayList<>();
            for (NodeResultRecord r : hit) {
                list.add(snapshot(r));
            }
        }

        Map<String, Object> body = ok("total", list.size());
        body.put("list", list);
        return body;
    }

    /* ---------------- V1.6 key 规范（plan_id / EXEC{execution_id}_STAGE{n}） ---------------- */

    /**
     * V1.6 接口13：节点结果存储 save_node_result —— key 唯一确定记录（同键覆盖）。
     * key 格式校验由调用方（OfferSimV16Service）完成；非法 key 返回 5002。
     */
    public synchronized Map<String, Object> saveV16(String key, String resultJson, String status) {
        return doSave(null, null, key, resultJson, status);
    }

    /**
     * V1.6 接口14：节点结果查询 query_node_result —— 按 key 查询；查无返回 5005。
     */
    public Map<String, Object> queryV16(String key) {
        NodeResultRecord latest = findLatestByKey(key);
        if (latest == null) {
            return fail(5005, "record not found for key: " + key);
        }
        Map<String, Object> body = ok("record", snapshotV16(latest));
        return body;
    }

    /**
     * V1.6 LLM智能调度模式门禁：按 req_id + node_name 取最新一条记录（供写接口硬校验）。
     * 智能体直调子工作流时，确认标记/环节结果以存储为准，工具层不信任 LLM 传参。
     *
     * @return 命中返回 {result_json, status}；未命中返回 null
     */
    public Map<String, Object> latestRecord(String reqId, String nodeName) {
        NodeResultRecord latest = findLatestByReqNode(reqId == null ? "" : reqId.trim(),
                nodeName == null ? "" : nodeName.trim());
        if (latest == null) {
            return null;
        }
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("result_json", latest.getResultJson());
        rec.put("status", latest.getStatus());
        return rec;
    }

    /* ---------------- 共用写入/查询 ---------------- */

    private synchronized Map<String, Object> doSave(String reqId, String nodeName, String key,
                                                    String resultJson, String status) {
        String result = resultJson == null ? "" : resultJson;
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_RESULT_JSON_BYTES) {
            return fail(5004, "result_json too large");
        }
        String status0 = status == null || status.isBlank() ? "ok" : status.trim().toLowerCase();

        // 同键覆盖：删除旧记录（保留 create_time 语义）
        NodeResultRecord existed = key != null ? findLatestByKey(key) : findLatestByReqNode(reqId, nodeName);
        String ts = LocalDateTime.now().format(TS);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createTimeLdt = existed == null || existed.getCreatedAt() == null
                ? now : existed.getCreatedAt();
        String createTime = createTimeLdt.format(TS);
        if (existed != null) {
            mapper.deleteById(existed.getId());
            log.info("[NodeResultService] 覆盖旧记录 key={} req_id={} node_name={}", key, reqId, nodeName);
        }

        NodeResultRecord record = new NodeResultRecord();
        record.setRecordId(nextRecordId());
        record.setReqId(reqId);
        record.setNodeName(nodeName);
        record.setResultKey(key);
        record.setResultJson(result);
        record.setStatus(status0);
        record.setCreatedAt(createTimeLdt);
        record.setUpdatedAt(now);
        mapper.insert(record);
        log.info("[NodeResultService] 保存节点结果 record_id={} key={} req_id={} node_name={} status={}",
                record.getRecordId(), key, reqId, nodeName, status0);

        Map<String, Object> body = ok("record_id", record.getRecordId());
        if (key != null) {
            body.put("key", key);
        }
        body.put("saved_at", ts);
        return body;
    }

    private NodeResultRecord findLatestByKey(String key) {
        List<NodeResultRecord> hit = mapper.selectList(new LambdaQueryWrapper<NodeResultRecord>()
                .eq(NodeResultRecord::getResultKey, key));
        return latestOf(hit);
    }

    private NodeResultRecord findLatestByReqNode(String reqId, String nodeName) {
        List<NodeResultRecord> hit = mapper.selectList(new LambdaQueryWrapper<NodeResultRecord>()
                .eq(NodeResultRecord::getReqId, reqId)
                .eq(nodeName != null && !nodeName.isEmpty(), NodeResultRecord::getNodeName, nodeName));
        return latestOf(hit);
    }

    private NodeResultRecord latestOf(List<NodeResultRecord> hit) {
        NodeResultRecord latest = null;
        for (NodeResultRecord r : hit) {
            if (latest == null || tsOf(r).compareTo(tsOf(latest)) > 0) {
                latest = r;
            }
        }
        return latest;
    }

    private String tsOf(NodeResultRecord r) {
        LocalDateTime t = r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt();
        return t == null ? "" : t.format(TS);
    }

    private Map<String, Object> snapshotV16(NodeResultRecord r) {
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put("key", r.getResultKey());
        copy.put("result_json", r.getResultJson());
        copy.put("status", r.getStatus());
        copy.put("create_time", r.getCreatedAt() == null ? "" : r.getCreatedAt().format(TS));
        copy.put("update_time", tsOf(r));
        return copy;
    }

    private Map<String, Object> snapshot(NodeResultRecord r) {
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put("req_id", r.getReqId());
        copy.put("node_name", r.getNodeName());
        copy.put("result_json", r.getResultJson());
        copy.put("status", r.getStatus());
        copy.put("create_time", r.getCreatedAt() == null ? "" : r.getCreatedAt().format(TS));
        copy.put("update_time", tsOf(r));
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
