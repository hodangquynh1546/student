package com.edutrack.classroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class StudentImportServiceTest {
    @Test
    void parsesQuotedCommasAndNewlines() {
        var rows=StudentImportService.parseCsv("email,fullName\r\na@example.test,\"Nguyễn, An\"\r\nb@example.test,\"Tên\nhai dòng\"\r\n");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1).get(1)).isEqualTo("Nguyễn, An");
        assertThat(rows.get(2).get(1)).isEqualTo("Tên\nhai dòng");
    }

    @Test
    void rejectsBrokenQuotesAndMissingHeaders() {
        assertThatThrownBy(() -> StudentImportService.parseCsv("email,\"unterminated"))
            .isInstanceOf(ResponseStatusException.class);
        var importer=new StudentImportService(null);
        var file=new MockMultipartFile("file","students.csv","text/csv","name,email\nAn,a@example.test".getBytes());
        assertThatThrownBy(() -> importer.importCsv(file,UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void importsValidRowsAndReportsInvalidRows() {
        var accounts=mock(StudentAccountService.class);
        var importer=new StudentImportService(accounts);
        UUID admin=UUID.randomUUID();
        var file=new MockMultipartFile("file","students.csv","text/csv",
            "email,fullName,dateOfBirth\na@example.test,An,2010-01-02\ninvalid, Bình,2010-01-03\n".getBytes());
        var result=importer.importCsv(file,admin);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors().getFirst().row()).isEqualTo(3);
        verify(accounts).create("a@example.test","An",null,java.time.LocalDate.of(2010,1,2),null,admin);
    }

    @Test
    void importsExcelWorkbookUsingSameValidation() throws Exception {
        var accounts=mock(StudentAccountService.class);
        var importer=new StudentImportService(accounts);
        UUID admin=UUID.randomUUID();
        byte[] bytes;
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("Học sinh");
            var header=sheet.createRow(0);header.createCell(0).setCellValue("email");header.createCell(1).setCellValue("fullName");
            var row=sheet.createRow(1);row.createCell(0).setCellValue("excel@example.test");row.createCell(1).setCellValue("Học sinh Excel");
            workbook.write(output);bytes=output.toByteArray();
        }
        var file=new MockMultipartFile("file","students.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes);
        var result=importer.importFile(file,admin);
        assertThat(result.created()).isEqualTo(1);
        verify(accounts).create("excel@example.test","Học sinh Excel",null,null,null,admin);
    }
}
