package com.qanoon.common;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** تصدير أي جدول إلى ملف Excel بتنسيق عربي من اليمين لليسار. */
@Service
public class ExcelExportService {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] export(String sheetTitleAr, List<String> headersAr, List<List<Object>> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(safeSheetName(sheetTitleAr));
            sheet.setRightToLeft(true);

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle cellStyle = wb.createCellStyle();
            cellStyle.setAlignment(HorizontalAlignment.RIGHT);
            cellStyle.setBorderBottom(BorderStyle.HAIR);

            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.cloneStyleFrom(cellStyle);
            moneyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            Row head = sheet.createRow(0);
            head.setHeightInPoints(22f);
            for (int i = 0; i < headersAr.size(); i++) {
                Cell c = head.createCell(i);
                c.setCellValue(headersAr.get(i));
                c.setCellStyle(headerStyle);
            }

            int r = 1;
            for (List<Object> row : rows) {
                Row sr = sheet.createRow(r++);
                for (int i = 0; i < row.size(); i++) {
                    Cell c = sr.createCell(i);
                    Object v = row.get(i);
                    if (v == null) {
                        c.setCellValue("");
                        c.setCellStyle(cellStyle);
                    } else if (v instanceof Number n) {
                        c.setCellValue(n.doubleValue());
                        c.setCellStyle(v instanceof BigDecimal ? moneyStyle : cellStyle);
                    } else if (v instanceof LocalDate d) {
                        c.setCellValue(d.format(D));
                        c.setCellStyle(cellStyle);
                    } else if (v instanceof LocalDateTime dt) {
                        c.setCellValue(dt.format(DT));
                        c.setCellStyle(cellStyle);
                    } else if (v instanceof Boolean b) {
                        c.setCellValue(b ? "نعم" : "لا");
                        c.setCellStyle(cellStyle);
                    } else {
                        c.setCellValue(String.valueOf(v));
                        c.setCellStyle(cellStyle);
                    }
                }
            }

            for (int i = 0; i < headersAr.size(); i++) {
                sheet.autoSizeColumn(i);
                int w = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(Math.max(w + 1200, 3000), 16000));
            }
            sheet.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("تعذّر إنشاء ملف Excel: " + e.getMessage());
        }
    }

    /** أسماء أوراق Excel لا تقبل بعض الرموز ولا تتجاوز ٣١ حرفاً. */
    private static String safeSheetName(String name) {
        String n = (name == null || name.isBlank()) ? "تقرير" : name;
        n = n.replaceAll("[\\\\/*?\\[\\]:]", " ").trim();
        return n.length() > 31 ? n.substring(0, 31) : n;
    }
}
