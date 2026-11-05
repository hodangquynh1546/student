package com.edutrack.analytics.reports;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);
    private final ReportService reports;
    private final JdbcTemplate db;
    public ReportScheduler(ReportService reports, JdbcTemplate db) { this.reports = reports; this.db = db; }

    @Scheduled(cron = "0 20 1 * * MON", zone = "UTC")
    public void weekly() {
        LocalDate start = LocalDate.now(Clock.systemUTC()).minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        for (UUID student : students()) generate(student, ReportService.Period.WEEK, start);
    }

    @Scheduled(cron = "0 20 2 1 * *", zone = "UTC")
    public void monthly() {
        LocalDate start = LocalDate.now(Clock.systemUTC()).minusMonths(1).withDayOfMonth(1);
        for (UUID student : students()) generate(student, ReportService.Period.MONTH, start);
    }

    private java.util.List<UUID> students() {
        return db.query("SELECT id FROM users WHERE role='STUDENT' AND status<>'DISABLED'",
            (rs, row) -> rs.getObject(1, UUID.class));
    }

    private void generate(UUID student, ReportService.Period period, LocalDate start) {
        try { reports.generate(student, period, start); }
        catch (Exception ex) { log.error("Report generation failed for student {} period {} start {}", student, period, start, ex); }
    }
}
