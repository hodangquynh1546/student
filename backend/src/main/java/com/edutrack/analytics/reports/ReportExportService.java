package com.edutrack.analytics.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ReportExportService {
    public enum Format { PDF, XLSX }

    public byte[] export(ReportService.Report report, Format format) {
        try {
            return format == Format.PDF ? pdf(report) : xlsx(report);
        } catch (IOException ex) {
            throw new IllegalStateException("Không xuất được báo cáo", ex);
        }
    }

    public String filename(ReportService.Report report, Format format) {
        return "edutrack-report-" + report.studentId() + "-" + report.period().name().toLowerCase()
            + "-" + report.start() + "." + format.name().toLowerCase();
    }

    private byte[] xlsx(ReportService.Report report) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet summary = workbook.createSheet("Tổng quan");
            CellStyle heading = workbook.createCellStyle();
            Font bold = workbook.createFont(); bold.setBold(true); heading.setFont(bold);
            row(summary, 0, "Báo cáo học tập EduTrack", null);
            summary.getRow(0).getCell(0).setCellStyle(heading);
            row(summary, 1, "Mã học sinh", report.studentId().toString());
            row(summary, 2, "Kỳ", report.period() == ReportService.Period.WEEK ? "Tuần" : "Tháng");
            row(summary, 3, "Từ ngày", report.start().toString());
            row(summary, 4, "Đến ngày (không gồm)", report.end().toString());
            row(summary, 5, "Số bài được giao", report.assignedCount());
            row(summary, 6, "Số bài đã hoàn thành", report.completedCount());
            row(summary, 7, "Tỷ lệ hoàn thành (%)", report.completionRate());
            row(summary, 8, "Số kết quả đã công bố", report.publishedResultCount());
            row(summary, 9, "Điểm trung bình (%)", report.averagePercent());
            row(summary, 10, "Thay đổi so với kỳ trước (điểm %)", report.progressPoints());
            row(summary, 11, "Tạo lúc (UTC)", report.generatedAt().toString());
            summary.setColumnWidth(0, 38 * 256);
            summary.setColumnWidth(1, 48 * 256);

            Sheet topics = workbook.createSheet("Chủ đề");
            Row header = topics.createRow(0);
            String[] labels = {"Chủ đề", "Số câu", "Điểm đạt", "Điểm tối đa", "Tỷ lệ (%)"};
            for (int i = 0; i < labels.length; i++) {
                header.createCell(i).setCellValue(labels[i]);
                header.getCell(i).setCellStyle(heading);
            }
            int index = 1;
            for (ReportService.Topic topic : report.topics()) {
                Row cells = topics.createRow(index++);
                cells.createCell(0).setCellValue(safe(topic.name()));
                cells.createCell(1).setCellValue(topic.questionCount());
                number(cells, 2, topic.earnedPoints());
                number(cells, 3, topic.possiblePoints());
                number(cells, 4, topic.percentage());
            }
            topics.setColumnWidth(0, 48 * 256);
            for (int i = 1; i < labels.length; i++) topics.setColumnWidth(i, 18 * 256);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void row(Sheet sheet, int index, String label, Object value) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        if (value instanceof Number number) row.createCell(1).setCellValue(number.doubleValue());
        else row.createCell(1).setCellValue(value == null ? "Chưa có dữ liệu" : safe(value.toString()));
    }

    private static void number(Row row, int column, BigDecimal value) {
        if (value != null) row.createCell(column).setCellValue(value.doubleValue());
    }

    private byte[] pdf(ReportService.Report report) throws IOException {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = ReportExportService.class.getResourceAsStream("/fonts/DejaVuSans.ttf");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (fontStream == null) throw new IOException("Thiếu font PDF tiếng Việt");
            PDType0Font font = PDType0Font.load(document, fontStream);
            List<String> lines = new ArrayList<>();
            lines.add("BÁO CÁO HỌC TẬP EDUTRACK");
            lines.add("Mã học sinh: " + report.studentId());
            lines.add("Kỳ: " + (report.period() == ReportService.Period.WEEK ? "Tuần" : "Tháng")
                + " | " + report.start() + " đến " + report.end().minusDays(1));
            lines.add("Số bài được giao: " + report.assignedCount());
            lines.add("Số bài đã hoàn thành: " + report.completedCount());
            lines.add("Tỷ lệ hoàn thành: " + display(report.completionRate()) + "%");
            lines.add("Số kết quả đã công bố: " + report.publishedResultCount());
            lines.add("Điểm trung bình: " + display(report.averagePercent()) + "%");
            lines.add("Thay đổi so với kỳ trước: " + display(report.progressPoints()) + " điểm %");
            lines.add("Tạo lúc (UTC): " + report.generatedAt());
            lines.add("CHỦ ĐỀ");
            if (report.topics().isEmpty()) lines.add("Chưa có dữ liệu chủ đề.");
            for (ReportService.Topic topic : report.topics()) {
                lines.add(safe(topic.name()) + " | " + topic.questionCount() + " câu | "
                    + display(topic.earnedPoints()) + "/" + display(topic.possiblePoints())
                    + " điểm | " + display(topic.percentage()) + "%");
            }
            float y = 790;
            PDPageContentStream stream = null;
            try {
                for (String line : lines) {
                    for (String fragment : wrap(pdfSafe(safe(line), font), font, 10, 495)) {
                        if (stream == null || y < 55) {
                            if (stream != null) stream.close();
                            PDPage page = new PDPage(PDRectangle.A4);
                            document.addPage(page);
                            stream = new PDPageContentStream(document, page);
                            y = 790;
                        }
                        stream.beginText();
                        stream.setFont(font, 10);
                        stream.newLineAtOffset(50, y);
                        stream.showText(fragment);
                        stream.endText();
                        y -= 17;
                    }
                    y -= 6;
                }
            } finally { if (stream != null) stream.close(); }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static String display(BigDecimal value) { return value == null ? "Chưa có dữ liệu" : value.toPlainString(); }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\p{Cntrl}&&[^\\t]]", " ").replace('\t', ' ').strip();
    }

    private static String pdfSafe(String value, PDType0Font font) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            String character = new String(Character.toChars(cp));
            try {
                font.getStringWidth(character);
                result.append(character);
            } catch (IllegalArgumentException ex) {
                result.append('?');
            }
            offset += Character.charCount(cp);
        }
        return result.toString();
    }

    private static List<String> wrap(String input, PDType0Font font, int size, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : input.split(" +")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.getStringWidth(candidate) * size / 1000 <= maxWidth) {
                line = new StringBuilder(candidate);
                continue;
            }
            if (!line.isEmpty()) { result.add(line.toString()); line = new StringBuilder(); }
            for (int offset = 0; offset < word.length();) {
                int cp = word.codePointAt(offset);
                String next = line + new String(Character.toChars(cp));
                if (!line.isEmpty() && font.getStringWidth(next) * size / 1000 > maxWidth) {
                    result.add(line.toString()); line = new StringBuilder();
                }
                line.appendCodePoint(cp);
                offset += Character.charCount(cp);
            }
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result.isEmpty() ? List.of(" ") : result;
    }
}
