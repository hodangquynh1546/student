package com.edutrack.analytics.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    @Test
    void periodBoundariesAreUtcCalendarWeeksAndMonths() {
        assertEquals(LocalDate.of(2026, 11, 2), ReportService.normalize(ReportService.Period.WEEK,
            LocalDate.of(2026, 11, 5)));
        assertEquals(LocalDate.of(2026, 11, 9), ReportService.end(ReportService.Period.WEEK,
            LocalDate.of(2026, 11, 2)));
        assertEquals(LocalDate.of(2026, 12, 1), ReportService.end(ReportService.Period.MONTH,
            LocalDate.of(2026, 11, 1)));
        assertEquals(new BigDecimal("66.67"), ReportService.average(List.of(
            new BigDecimal("100.00"), new BigDecimal("33.33"))));
    }

    @Test
    void parentNeedsAcceptedNonRevokedLinkBeforeAnyReportLookup() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        UUID parent = UUID.randomUUID(), child = UUID.randomUUID();
        when(db.queryForObject(anyString(), eq(Boolean.class), eq(parent), eq(child))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            assertTrue(sql.contains("l.revoked_at IS NULL"));
            assertTrue(sql.contains("r.status='ACCEPTED'"));
            return false;
        });
        var error = assertThrows(ResponseStatusException.class,
            () -> new ReportService(db, new ObjectMapper()).parentList(parent, child));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(db).queryForObject(anyString(), eq(Boolean.class), eq(parent), eq(child));
        verifyNoMoreInteractions(db);
    }

    @Test
    void rejectsMisalignedPeriodWithoutQueryingDatabase() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var service = new ReportService(db, new ObjectMapper());
        var error = assertThrows(ResponseStatusException.class,
            () -> service.generate(UUID.randomUUID(), ReportService.Period.WEEK, LocalDate.of(2026, 11, 5)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(db);
    }

    @Test
    void exportLookupConstrainsBothReportAndStudentId() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        UUID student = UUID.randomUUID(), report = UUID.randomUUID();
        when(db.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(report), eq(student)))
            .thenAnswer(call -> {
                assertTrue(((String) call.getArgument(0)).contains("id=? AND student_id=?"));
                return List.of();
            });
        var error = assertThrows(ResponseStatusException.class,
            () -> new ReportService(db, new ObjectMapper()).studentReport(student, report));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(db).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(report), eq(student));
    }

    @Test
    void parentExportRequiresVerifiedLinkBeforeReportQuery() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        UUID parent = UUID.randomUUID(), student = UUID.randomUUID();
        when(db.queryForObject(anyString(), eq(Boolean.class), eq(parent), eq(student))).thenReturn(false);
        var error = assertThrows(ResponseStatusException.class,
            () -> new ReportService(db, new ObjectMapper()).parentReport(parent, student, UUID.randomUUID()));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(db).queryForObject(anyString(), eq(Boolean.class), eq(parent), eq(student));
        verifyNoMoreInteractions(db);
    }
}
