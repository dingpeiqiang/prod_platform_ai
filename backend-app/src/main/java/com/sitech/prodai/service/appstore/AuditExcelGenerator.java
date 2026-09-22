package com.sitech.prodai.service.appstore;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 稽核需求 Excel 生成器（统一方案 6.6）——B1 无上传文件时，服务端按**固定模板**生成稽核 Excel。
 * <p>
 * 表头、列序、合并单元格严格对齐《新增四档5G畅享融合套餐销售品.xlsx》：
 * <ul>
 *   <li>【可选包】sheet：两行表头。第一行为主表头（序号 / OFR_ID / ... / 账单展示套餐内 / 账单展示套餐外），
 *       其中「账单展示套餐内」横向合并 AB1:AG1、「账单展示套餐外」横向合并 AH1:AL1，其余列纵向合并至第 2 行；
 *       第二行给出合并块下的子列（AC..AG=流量/短信/彩信/接听免费范围/其他赠送内容，
 *       AH..AL=语音/流量/短信/彩信/其他业务）；数据自第 3 行起。</li>
 *   <li>【关联关系】sheet：单行主表头（序号 / A_OFR_ID / A_OFR_NAME / 关系 / Z_OFR_ID / Z_OFR_NAME），数据自第 2 行起。</li>
 * </ul>
 * 数据来源：入参 {@code offers[]}（{ofr_id/offer_name/fields{列名→值}}）与 {@code relations[]}
 * （{a_ofr_id/a_name/relation/z_ofr_id/z_name}），与 2e {@code /requirement/parse-xlsx} 出参同构。
 * <p>
 * 纪律：只做确定性写出（不推理、不改写值）；缺列/缺值按空单元格落位，保证 EOP 解析口径稳定。
 */
@Service
public class AuditExcelGenerator {

    private static final Logger log = LoggerFactory.getLogger(AuditExcelGenerator.class);

    public static final String OFFER_SHEET = "可选包";
    public static final String RELATION_SHEET = "关联关系";

    /** 「账单展示套餐内」主表头文字（横向合并 AB1:AG1）。 */
    private static final String BILL_IN_HEADER = "账单展示套餐内（如无内容，此处不填）";
    /** 「账单展示套餐外」主表头文字（横向合并 AH1:AL1）。 */
    private static final String BILL_OUT_HEADER = "账单展示套餐外（如无内容，此处不填）";

    /**
     * 【可选包】列模型：主表头（第 1 行）+ 子表头（第 2 行，仅合并块下有值）。
     * <p>子表头为空的列纵向合并 A1:A2；连续多列同属一个主表头时横向合并、子表头逐列给出。
     */
    private static final List<OfferColumn> OFFER_COLUMNS = List.of(
            new OfferColumn("序号", ""),
            new OfferColumn("OFR_ID", ""),
            new OfferColumn("GROUP_ID", ""),
            new OfferColumn("OFFER_NBR", ""),
            new OfferColumn("省级/市级*", ""),
            new OfferColumn("付费模式", ""),
            new OfferColumn("套餐客户化名称", ""),
            new OfferColumn("套餐名称", ""),
            new OfferColumn("基础包/可选包/加装包", ""),
            new OfferColumn("是否主套餐/加装包", ""),
            new OfferColumn("内容描述(套餐的关键信息，如资费价格、优惠折扣、账户/用户、包含的费用、业务等)", ""),
            new OfferColumn("一单清展示（套餐内）", ""),
            new OfferColumn("一单清展示（超出资费）", ""),
            new OfferColumn("一单清展示（其他提醒）", ""),
            new OfferColumn("首月计费模式", ""),
            new OfferColumn("次月计费模式", ""),
            new OfferColumn("套餐销售时间", ""),
            new OfferColumn("套餐停售时间", ""),
            new OfferColumn("过渡套餐资费描述（不填则不配置过渡套餐）", ""),
            new OfferColumn("是否允许修改生失效时间", ""),
            new OfferColumn("是否为不可退订销售品", ""),
            new OfferColumn("订购竣工短信提醒", ""),
            new OfferColumn("到期提醒", ""),
            new OfferColumn("账目类型", ""),
            new OfferColumn("降速级别（单套餐/多套餐）-计费配置", ""),
            new OfferColumn("断网降速阈值-计费配置", ""),
            new OfferColumn("流量提醒-计费配置", ""),
            new OfferColumn(BILL_IN_HEADER, "语音"),
            new OfferColumn(BILL_IN_HEADER, "流量"),
            new OfferColumn(BILL_IN_HEADER, "短信"),
            new OfferColumn(BILL_IN_HEADER, "彩信"),
            new OfferColumn(BILL_IN_HEADER, "接听免费范围"),
            new OfferColumn(BILL_IN_HEADER, "其他赠送内容"),
            new OfferColumn(BILL_OUT_HEADER, "语音"),
            new OfferColumn(BILL_OUT_HEADER, "流量"),
            new OfferColumn(BILL_OUT_HEADER, "短信"),
            new OfferColumn(BILL_OUT_HEADER, "彩信"),
            new OfferColumn(BILL_OUT_HEADER, "其他业务"));

    /** 【关联关系】固定表头（单行）。 */
    private static final List<String> RELATION_HEADERS = List.of(
            "序号", "A_OFR_ID", "A_OFR_NAME", "关系", "Z_OFR_ID", "Z_OFR_NAME");

    /** 列定义：主表头 + 子表头（子表头空表示纵向合并）。 */
    private record OfferColumn(String main, String sub) {
    }

    /**
     * 生成稽核 Excel 字节流。
     *
     * @param offers    销售品行：{ofr_id, offer_name, fields{列名→值}}
     * @param relations 关联关系行：{a_ofr_id, a_name, relation, z_ofr_id, z_name}
     * @return xlsx 字节流
     */
    public byte[] generate(List<Map<String, Object>> offers, List<Map<String, Object>> relations) {
        List<Map<String, Object>> safeOffers = offers == null ? new ArrayList<>() : offers;
        List<Map<String, Object>> safeRelations = relations == null ? new ArrayList<>() : relations;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeOfferSheet(wb, safeOffers);
            writeRelationSheet(wb, safeRelations);
            wb.write(out);
            log.info("[AuditExcelGenerator] 生成稽核 Excel offers={} relations={} bytes={}",
                    safeOffers.size(), safeRelations.size(), out.size());
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("稽核 Excel 生成失败：" + e.getMessage(), e);
        }
    }

    /* ---------------- 可选包 sheet ---------------- */

    private void writeOfferSheet(XSSFWorkbook wb, List<Map<String, Object>> offers) {
        Sheet sheet = wb.createSheet(OFFER_SHEET);
        writeOfferHeader(sheet);
        int rowIdx = 2;
        for (Map<String, Object> offer : offers) {
            Map<String, Object> fields = toFields(offer);
            Row row = sheet.createRow(rowIdx);
            for (int c = 0; c < OFFER_COLUMNS.size(); c++) {
                OfferColumn col = OFFER_COLUMNS.get(c);
                setCell(row, c, resolveOfferValue(col, offer, fields, rowIdx - 1));
            }
            rowIdx++;
        }
    }

    /** 写两行表头并建立合并：主表头横向合并块（账单展示套餐内/外）+ 其余列纵向合并。 */
    private void writeOfferHeader(Sheet sheet) {
        Row header0 = sheet.createRow(0);
        Row header1 = sheet.createRow(1);
        int c = 0;
        while (c < OFFER_COLUMNS.size()) {
            OfferColumn col = OFFER_COLUMNS.get(c);
            setCell(header0, c, col.main());
            if (col.sub().isEmpty()) {
                sheet.addMergedRegion(new CellRangeAddress(0, 1, c, c));
                c++;
                continue;
            }
            int start = c;
            while (c < OFFER_COLUMNS.size() && OFFER_COLUMNS.get(c).main().equals(col.main())) {
                setCell(header1, c, OFFER_COLUMNS.get(c).sub());
                c++;
            }
            if (c - start > 1) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, start, c - 1));
            }
        }
    }

    /** 取值优先取 fields（宽表原始列，键为子表头或主表头文字），核心列回落到 offer 顶层字段。 */
    private Object resolveOfferValue(OfferColumn col, Map<String, Object> offer,
                                     Map<String, Object> fields, int seq) {
        if ("序号".equals(col.main())) {
            return seq;
        }
        Object v = fields.get(col.sub().isEmpty() ? col.main() : col.sub());
        if (isBlank(v)) {
            v = fields.get(col.main());
        }
        if (isBlank(v)) {
            if ("OFR_ID".equals(col.main())) {
                v = firstNonBlank(offer.get("ofr_id"), offer.get("offer_id"));
            } else if ("套餐客户化名称".equals(col.main()) || "套餐名称".equals(col.main())) {
                v = offer.get("offer_name");
            }
        }
        return v == null ? "" : v;
    }

    /* ---------------- 关联关系 sheet ---------------- */

    private void writeRelationSheet(XSSFWorkbook wb, List<Map<String, Object>> relations) {
        Sheet sheet = wb.createSheet(RELATION_SHEET);
        Row header = sheet.createRow(0);
        for (int c = 0; c < RELATION_HEADERS.size(); c++) {
            setCell(header, c, RELATION_HEADERS.get(c));
        }
        int rowIdx = 1;
        for (Map<String, Object> rel : relations) {
            Row row = sheet.createRow(rowIdx);
            setCell(row, 0, rowIdx);
            setCell(row, 1, firstNonBlank(rel.get("a_ofr_id"), rel.get("a_id")));
            setCell(row, 2, firstNonBlank(rel.get("a_name"), rel.get("a_ofr_name")));
            setCell(row, 3, firstNonBlank(rel.get("relation"), rel.get("relation_label")));
            setCell(row, 4, firstNonBlank(rel.get("z_ofr_id"), rel.get("z_id")));
            setCell(row, 5, firstNonBlank(rel.get("z_name"), rel.get("z_ofr_name")));
            rowIdx++;
        }
    }

    /* ---------------- 工具方法 ---------------- */

    private void setCell(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value == null || isBlank(value)) {
            cell.setCellValue("");
            return;
        }
        if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toFields(Map<String, Object> offer) {
        Object f = offer.get("fields");
        return f instanceof Map ? (Map<String, Object>) f : new LinkedHashMap<>();
    }

    private static boolean isBlank(Object v) {
        return v == null || String.valueOf(v).trim().isEmpty();
    }

    private static Object firstNonBlank(Object... values) {
        for (Object v : values) {
            if (!isBlank(v)) {
                return v;
            }
        }
        return "";
    }
}
