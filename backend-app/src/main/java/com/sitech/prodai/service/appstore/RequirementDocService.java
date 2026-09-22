package com.sitech.prodai.service.appstore;

import com.sitech.prodai.service.ConfigDocumentParser;
import com.sitech.prodai.service.DocumentIR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 需求文档（.xlsx）解析服务 —— 产销品加载 AI 应用 V3.10（统一方案 6.6）。
 * <p>
 * 输入：客户《需求文档》（如《新增四档5G畅享融合套餐销售品.xlsx》），含
 * 「可选包」宽表（每行一个新增销售品）+「关联关系」A/Z 端关系表 + 其它 sheet（忽略）。
 * <p>
 * 输出：结构化需求 {@code {resultCode, offers[], relations[], source_sheets[], count}}：
 * <ul>
 *   <li>{@code offers[]}：{@code {offer_seq, ofr_id, offer_name, sheet_name, fields{原始列→值}}}，
 *       offer_seq 从 1 起，作为**子键**与父 req_id 构成一品多品复合寻址（统一方案 5.3.1）；</li>
 *   <li>{@code relations[]}：{@code {a_ofr_id, a_name, relation, z_ofr_id, z_name}}，
 *       relation 枚举 100000 可选依赖 / 110000 强制依赖 / 120000 默认依赖 / 130000 赠送 /
 *       200000 互斥 / 410000 不可迁转 / 100100 自主版；越界 → E41。</li>
 * </ul>
 * 等价于统一方案 4.5.2 端点 2e（{@code /api/v1/appstore/requirement/parse-xlsx}）；
 * 解析复用零依赖 {@link ConfigDocumentParser}（不引入 POI），与 B1 稽核上传同模式。
 * <p>
 * 纪律：本服务只做**确定性结构归一**（不推理、不补全、不改写原始值）；字段三归属
 * （映射入 24 字段 / 落模板 schema / 冗余入报告）由下游 wf_sub_01 模板轨完成，故此处
 * 保留全量原始列，仅在 fields 中按中文表头原样取值。
 */
@Service
public class RequirementDocService {

    private static final Logger log = LoggerFactory.getLogger(RequirementDocService.class);

    /** 可选包 sheet 表头候选（按中文标签模糊定位核心列） */
    private static final String[] OFFER_ID_HEADERS = {"OFR_ID", "OFFER_ID", "销售品ID", "销售品编码", "商品编码"};
    private static final String[] OFFER_NAME_HEADERS = {"套餐客户化名称", "套餐名称", "销售品名称", "商品名称"};
    /** 关联关系 sheet 表头候选 */
    private static final String[] REL_A_ID_HEADERS = {"A_OFR_ID", "A端销售品ID", "A端ID"};
    private static final String[] REL_A_NAME_HEADERS = {"A_OFR_NAME", "A端名称", "A端销售品名称"};
    private static final String[] REL_TYPE_HEADERS = {"关系", "关系类型", "关联类型"};
    private static final String[] REL_Z_ID_HEADERS = {"Z_OFR_ID", "Z端销售品ID", "Z端ID"};
    private static final String[] REL_Z_NAME_HEADERS = {"Z_OFR_NAME", "Z端名称", "Z端销售品名称"};

    /** 关联关系枚举（统一方案 7.2） */
    private static final List<String> RELATION_ENUM = List.of(
            "100000", "110000", "120000", "130000", "200000", "410000", "100100");
    /**
     * 关系中文标签 → 数字枚举（需求文档【关联关系】sheet 的「关系」列常填中文标签，
     * 如「可选依赖」，而非数字编码；枚举数字见 sheet 备注区）。归一后原始标签保留在 relation_label。
     */
    private static final Map<String, String> RELATION_LABEL_TO_ENUM = Map.ofEntries(
            Map.entry("可选依赖", "100000"),
            Map.entry("强制依赖", "110000"),
            Map.entry("默认依赖", "120000"),
            Map.entry("赠送", "130000"),
            Map.entry("赠送关系", "130000"),
            Map.entry("互斥", "200000"),
            Map.entry("不可迁转", "410000"),
            Map.entry("不可迁转关系", "410000"),
            Map.entry("自主版", "100100"),
            Map.entry("自主版关系", "100100"));

    /** 可选包 sheet 名候选 */
    private static final String[] OFFER_SHEET_HINTS = {"可选包", "销售品", "商品", "offer", "package"};
    /** 关联关系 sheet 名候选 */
    private static final String[] RELATION_SHEET_HINTS = {"关联关系", "关系", "relation"};

    private final ConfigDocumentParser parser;

    public RequirementDocService(ConfigDocumentParser parser) {
        this.parser = parser;
    }

    /**
     * 解析需求文档字节流。
     *
     * @param bytes    文件字节
     * @param fileName 原始文件名（判扩展名）
     * @return 结构化需求体；解析失败/无销售品/关系越界返回 {@code resultCode!=0}（E41）
     */
    public Map<String, Object> parse(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            return fail("需求文档为空或未上传");
        }
        ConfigDocumentParser.ParseResult pr = parser.parse(bytes, fileName);
        if (pr == null || !pr.success() || pr.document() == null) {
            String msg = pr == null ? "解析器返回空" : pr.message();
            log.warn("[RequirementDoc] 解析失败 file={} msg={}", fileName, msg);
            return fail("需求文档解析失败：" + msg);
        }
        List<DocumentIR.Block> tables = pr.document().tables();
        if (tables.isEmpty()) {
            return fail("需求文档未识别到表格（需含【可选包】与【关联关系】sheet）");
        }

        List<String> sourceSheets = new ArrayList<>();
        DocumentIR.Block offerTable = null;
        DocumentIR.Block relationTable = null;
        for (DocumentIR.Block t : tables) {
            String sheet = t.sheetName() == null ? "" : t.sheetName();
            sourceSheets.add(sheet);
            if (relationTable == null && matches(sheet, RELATION_SHEET_HINTS)) {
                relationTable = t;
            } else if (offerTable == null && matches(sheet, OFFER_SHEET_HINTS)) {
                offerTable = t;
            }
        }
        // 兜底：未按 sheet 名识别到时，取第一个含 OFR_ID 列的表为可选包表
        if (offerTable == null) {
            for (DocumentIR.Block t : tables) {
                if (columnIndexOf(t.headers(), OFFER_ID_HEADERS) >= 0) {
                    offerTable = t;
                    break;
                }
            }
        }
        if (offerTable == null) {
            return fail("需求文档未识别到【可选包】sheet（需含 OFR_ID / 套餐名称 列）");
        }

        List<Map<String, Object>> offers = extractOffers(offerTable);
        if (offers.isEmpty()) {
            return fail("需求文档【可选包】sheet 无有效销售品行（offers 为空）");
        }
        List<Map<String, Object>> relations = relationTable == null
                ? new ArrayList<>() : extractRelations(relationTable);

        // 关联关系枚举校验（越界 → E41）
        for (Map<String, Object> rel : relations) {
            String type = str(rel.get("relation"));
            if (type.isEmpty()) {
                continue;
            }
            if (!RELATION_ENUM.contains(type)) {
                return fail("关联关系枚举越界：relation=" + type
                        + "（原始值「" + str(rel.get("relation_label")) + "」，合法值 " + RELATION_ENUM
                        + "），请检查【关联关系】sheet");
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resultCode", "0");
        body.put("resultMsg", "success");
        body.put("count", offers.size());
        body.put("offers", offers);
        body.put("relations", relations);
        body.put("source_sheets", sourceSheets);
        log.info("[RequirementDoc] 解析成功 file={} offers={} relations={} sheets={}",
                fileName, offers.size(), relations.size(), sourceSheets);
        return body;
    }

    /* ---------------- 可选包 → offers[] ---------------- */

    private List<Map<String, Object>> extractOffers(DocumentIR.Block table) {
        List<String> headers = table.headers();
        List<List<String>> rows = table.rows();
        List<Map<String, Object>> offers = new ArrayList<>();
        int idIdx = columnIndexOf(headers, OFFER_ID_HEADERS);
        int nameIdx = columnIndexOf(headers, OFFER_NAME_HEADERS);
        int seq = 0;
        for (List<String> row : rows) {
            // 跳过空行 / 表头重复行
            if (row == null || row.isEmpty() || isBlankRow(row)) {
                continue;
            }
            String ofrId = cell(row, idIdx);
            String name = cell(row, nameIdx);
            if (ofrId.isEmpty() && name.isEmpty()) {
                continue;
            }
            if (matches(ofrId, OFFER_ID_HEADERS)) {
                continue; // 表头行自身
            }
            seq++;
            Map<String, Object> offer = new LinkedHashMap<>();
            offer.put("offer_seq", seq);
            offer.put("ofr_id", ofrId);
            offer.put("offer_name", name);
            offer.put("sheet_name", table.sheetName() == null ? "" : table.sheetName());
            offer.put("fields", rowToFields(headers, row));
            offers.add(offer);
        }
        return offers;
    }

    /** 整行按表头映射为 {列名: 值}（列名空白时用 col1/col2… 占位，保证不丢列）。 */
    private Map<String, Object> rowToFields(List<String> headers, List<String> row) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < row.size(); i++) {
            String key = i < headers.size() && headers.get(i) != null && !headers.get(i).isBlank()
                    ? headers.get(i).trim()
                    : ("col" + (i + 1));
            if (!fields.containsKey(key)) {
                fields.put(key, row.get(i) == null ? "" : row.get(i));
            }
        }
        return fields;
    }

    /* ---------------- 关联关系 → relations[] ---------------- */

    private List<Map<String, Object>> extractRelations(DocumentIR.Block table) {
        List<String> headers = table.headers();
        List<List<String>> rows = table.rows();
        List<Map<String, Object>> relations = new ArrayList<>();
        int aIdIdx = columnIndexOf(headers, REL_A_ID_HEADERS);
        int aNameIdx = columnIndexOf(headers, REL_A_NAME_HEADERS);
        int typeIdx = columnIndexOf(headers, REL_TYPE_HEADERS);
        int zIdIdx = columnIndexOf(headers, REL_Z_ID_HEADERS);
        int zNameIdx = columnIndexOf(headers, REL_Z_NAME_HEADERS);
        for (List<String> row : rows) {
            if (row == null || row.isEmpty() || isBlankRow(row)) {
                continue;
            }
            String aId = cell(row, aIdIdx);
            String zId = cell(row, zIdIdx);
            String rawType = cell(row, typeIdx);
            if (matches(aId, REL_A_ID_HEADERS) || matches(rawType, REL_TYPE_HEADERS)) {
                continue; // 表头行 / 重复表头行自身
            }
            if (aId.isEmpty() && zId.isEmpty() && rawType.isEmpty()) {
                continue;
            }
            // 关系列本身为说明文字（如「关系类型：」枚举备注）且无 A/Z 端 → 跳过
            if (aId.isEmpty() && zId.isEmpty()) {
                continue;
            }
            Map<String, Object> rel = new LinkedHashMap<>();
            rel.put("a_ofr_id", aId);
            rel.put("a_name", cell(row, aNameIdx));
            rel.put("relation", normalizeRelation(rawType));
            rel.put("relation_label", rawType);
            rel.put("z_ofr_id", zId);
            rel.put("z_name", cell(row, zNameIdx));
            relations.add(rel);
        }
        // 去重：合并单元格展开会让同一关系行出现多次（如样例块 4 行同值），按 A|关系|Z 归一
        List<Map<String, Object>> deduped = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Map<String, Object> rel : relations) {
            String key = rel.get("a_ofr_id") + "|" + rel.get("relation") + "|" + rel.get("z_ofr_id");
            if (seen.add(key)) {
                deduped.add(rel);
            }
        }
        return deduped;
    }

    /* ---------------- 工具方法 ---------------- */

    /** 表头匹配：返回首个命中的列下标（大小写不敏感、去空白），未命中 -1。 */
    private static int columnIndexOf(List<String> headers, String[] candidates) {
        if (headers == null) {
            return -1;
        }
        for (int i = 0; i < headers.size(); i++) {
            String h = norm(headers.get(i));
            if (h.isEmpty()) {
                continue;
            }
            for (String c : candidates) {
                if (h.equals(norm(c))) {
                    return i;
                }
            }
        }
        // 二次宽松匹配（包含关系）
        for (int i = 0; i < headers.size(); i++) {
            String h = norm(headers.get(i));
            if (h.isEmpty()) {
                continue;
            }
            for (String c : candidates) {
                String cn = norm(c);
                if (!cn.isEmpty() && (h.contains(cn) || cn.contains(h))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean matches(String value, String[] candidates) {
        String v = norm(value);
        if (v.isEmpty()) {
            return false;
        }
        for (String c : candidates) {
            String cn = norm(c);
            if (!cn.isEmpty() && (v.equals(cn) || v.contains(cn))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 关系列归一：中文标签 → 数字枚举；已是数字枚举或未知值则原样返回（由调用方校验越界）。
     */
    private static String normalizeRelation(String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return "";
        }
        if (RELATION_ENUM.contains(v)) {
            return v;
        }
        String mapped = RELATION_LABEL_TO_ENUM.get(v);
        return mapped != null ? mapped : v;
    }

    private static String cell(List<String> row, int idx) {
        if (idx < 0 || idx >= row.size() || row.get(idx) == null) {
            return "";
        }
        return row.get(idx).trim();
    }

    private static boolean isBlankRow(List<String> row) {
        for (String c : row) {
            if (c != null && !c.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static Map<String, Object> fail(String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resultCode", "E41");
        body.put("resultMsg", msg);
        body.put("count", 0);
        body.put("offers", List.of());
        body.put("relations", List.of());
        body.put("source_sheets", List.of());
        return body;
    }
}
