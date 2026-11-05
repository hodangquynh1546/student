package com.edutrack.analytics.reports;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportExportServiceTest {
    private final ReportExportService exports = new ReportExportService();

    @Test
    void createsVietnamesePdfFromStoredReportWithoutDroppingTopics() throws Exception {
        var report = report();
        byte[] bytes = exports.export(report, ReportExportService.Format.PDF);
        assertEquals("%PDF", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        try (var pdf = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("BÁO CÁO HỌC TẬP"));
            assertTrue(text.contains("Phân số"));
            assertTrue(text.contains("75.00"));
        }
    }

    @Test
    void createsExcelWithNumericMetricsAndSafeFilename() throws Exception {
        var report = report();
        byte[] bytes = exports.export(report, ReportExportService.Format.XLSX);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals(4d, workbook.getSheet("Tổng quan").getRow(5).getCell(1).getNumericCellValue());
            assertEquals(75d, workbook.getSheet("Tổng quan").getRow(7).getCell(1).getNumericCellValue());
            assertEquals("Phân số", workbook.getSheet("Chủ đề").getRow(1).getCell(0).getStringCellValue());
        }
        assertEquals("edutrack-report-" + report.studentId() + "-week-2026-11-02.xlsx",
            exports.filename(report, ReportExportService.Format.XLSX));
    }

    private static ReportService.Report report() {
        return new ReportService.Report(UUID.randomUUID(), UUID.randomUUID(), ReportService.Period.WEEK,
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9), 4, 3,
            new BigDecimal("75.00"), 2, new BigDecimal("80.00"), new BigDecimal("5.00"),
            List.of(new ReportService.Topic("Phân số", 5, new BigDecimal("7.50"),
                new BigDecimal("10.00"), new BigDecimal("75.00"))), Instant.parse("2026-11-10T00:00:00Z"));
    }
}
