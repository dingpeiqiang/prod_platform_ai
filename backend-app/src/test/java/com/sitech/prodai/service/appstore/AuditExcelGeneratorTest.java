package com.sitech.prodai.service.appstore;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 稽核 Excel 生成器测试（统一方案 6.6）：断言 38 列固定表头按列落位——
 * 有值走 fields，无值填既定默认值，保证 EOP 解析口径稳定（不出现空列）。
 */
@DisplayName("稽核 Excel 生成")
class AuditExcelGeneratorTest {

    private final AuditExcelGenerator generator = new AuditExcelGenerator();

    @Test
    @DisplayName("可选包 38 列均落位：有值取 fields、无值填默认值")
    void offerColumnsFilledWithDefaults() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("OFR_ID", "900117029");
        fields.put("套餐名称", "5G畅享融合79元套餐202512");

        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("ofr_id", "900117029");
        offer.put("offer_name", "5G畅享融合79元套餐202512");
        offer.put("fields", fields);

        byte[] bytes = generator.generate(List.of(offer), List.of());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet(AuditExcelGenerator.OFFER_SHEET);
            Assertions.assertNotNull(sheet, "应含【可选包】sheet");

            Row row = sheet.getRow(2);
            Assertions.assertNotNull(row, "数据应自第 3 行起");
            // 数据行应写满 38 列（A..AL = 0..37），无空列
            Assertions.assertEquals(38, row.getLastCellNum(), "数据行应为 38 列");
            Assertions.assertEquals(38, sheet.getRow(1).getLastCellNum(), "子表头应为 38 列");

            // 有值列
            Assertions.assertEquals("900117029", row.getCell(1).getStringCellValue(), "OFR_ID 应取自 fields");
            Assertions.assertEquals("5G畅享融合79元套餐202512",
                    row.getCell(7).getStringCellValue(), "套餐名称应取自 fields");
            // 无值列 → 既定默认值
            Assertions.assertEquals("全省", row.getCell(4).getStringCellValue(), "省级/市级 应填默认值");
            Assertions.assertEquals("HB", row.getCell(5).getStringCellValue(), "付费模式 应填默认值");
            Assertions.assertEquals("60667 天翼套餐月使用费",
                    row.getCell(23).getStringCellValue(), "账目类型 应填默认值");
        }
    }

    @Test
    @DisplayName("关联关系 sheet 表头 6 列且数据行落位")
    void relationSheetFilled() throws Exception {
        Map<String, Object> rel = new LinkedHashMap<>();
        rel.put("a_ofr_id", "900117029");
        rel.put("a_name", "5G畅享融合79元套餐202512");
        rel.put("relation", "100000");
        rel.put("z_ofr_id", "7320110001600005");
        rel.put("z_name", "5G体验10元副卡功能费201907");

        byte[] bytes = generator.generate(List.of(), List.of(rel));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet(AuditExcelGenerator.RELATION_SHEET);
            Assertions.assertNotNull(sheet, "应含【关联关系】sheet");
            Assertions.assertEquals(6, sheet.getRow(0).getLastCellNum(), "关系表头应为 6 列");

            Row row = sheet.getRow(1);
            Assertions.assertEquals("900117029", row.getCell(1).getStringCellValue());
            Assertions.assertEquals("100000", row.getCell(3).getStringCellValue());
            Assertions.assertEquals("7320110001600005", row.getCell(4).getStringCellValue());
        }
    }
}
