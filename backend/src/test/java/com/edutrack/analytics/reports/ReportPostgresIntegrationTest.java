package com.edutrack.analytics.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportPostgresIntegrationTest {
    private PostgreSQLContainer<?> container;
    private String baseUrl, username, password, schema;
    private JdbcTemplate db;
    private ReportService reports;

    @BeforeAll
    void setUp() throws Exception {
        baseUrl = System.getenv("EDUTRACK_TEST_DATABASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Set EDUTRACK_TEST_DATABASE_URL or provide Docker for PostgreSQL integration tests");
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            baseUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            username = System.getenv().getOrDefault("EDUTRACK_TEST_DATABASE_USER", "edutrack");
            password = System.getenv().getOrDefault("EDUTRACK_TEST_DATABASE_PASSWORD", "");
        }
        schema = "edutrack_report_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var conn = DriverManager.getConnection(baseUrl, username, password);
             var stmt = conn.createStatement()) { stmt.execute("CREATE SCHEMA " + schema); }
        var source = new DriverManagerDataSource(withSchema(baseUrl, schema), username, password);
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
        db = new JdbcTemplate(source);
        reports = new ReportService(db, new ObjectMapper());
    }

    @AfterAll
    void tearDown() throws Exception {
        if (schema != null) {
            try (var conn = DriverManager.getConnection(baseUrl, username, password);
                 var stmt = conn.createStatement()) { stmt.execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
        if (container != null) container.stop();
    }

    @Test
    void snapshotsUsePublishedScoresAndParentNeedsLiveVerifiedLink() {
        UUID admin = user("ADMIN_TEACHER"), child = user("STUDENT"), parent = user("PARENT");
        LocalDate week = LocalDate.now(java.time.Clock.systemUTC()).minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        UUID publishedExam = exam(admin), privateExam = exam(admin);
        assignment(admin, child, publishedExam, week.plusDays(3));
        assignment(admin, child, privateExam, week.plusDays(3));
        attempt(child, publishedExam, week.plusDays(3), true, "75");
        attempt(child, privateExam, week.plusDays(3), false, "95");

        var report = reports.generate(child, ReportService.Period.WEEK, week);
        assertEquals(2, report.assignedCount());
        assertEquals(2, report.completedCount());
        assertEquals(1, report.publishedResultCount());
        assertEquals(new BigDecimal("75.00"), report.averagePercent());
        assertEquals(1, reports.studentList(child).size());
        assertEquals(report.id(), reports.studentReport(child, report.id()).id());
        UUID otherStudent = user("STUDENT");
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> reports.studentReport(otherStudent, report.id())).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> reports.parentList(parent, child)).getStatusCode());

        UUID request = UUID.randomUUID();
        db.update("INSERT INTO parent_link_requests(id,student_id,parent_email,requested_by,status,expires_at) " +
            "VALUES (?,?,?,?,'ACCEPTED',now()+interval '1 day')", request, child, "parent@example.test", admin);
        db.update("INSERT INTO parent_student_links(id,parent_id,student_id,verified_request_id) VALUES (?,?,?,?)",
            UUID.randomUUID(), parent, child, request);
        var parentReport = reports.parentList(parent, child).getFirst();
        assertEquals(report.id(), reports.parentReport(parent, child, report.id()).id());
        assertEquals(1, parentReport.publishedResultCount());
        assertEquals(new BigDecimal("75.00"), parentReport.averagePercent());
        assertEquals(2, parentReport.assignedCount());
        assertEquals(2, parentReport.completedCount());
        assertEquals(new BigDecimal("100.00"), parentReport.completionRate());
        db.update("UPDATE parent_student_links SET revoked_at=now() WHERE parent_id=? AND student_id=?", parent, child);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> reports.parentList(parent, child)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> reports.parentReport(parent, child, report.id())).getStatusCode());
    }

    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO users(id,email,full_name,role,status,password_hash) VALUES (?,?,?,?,'ACTIVE',?)",
            id, id + "@example.test", "Report user", role, "test-hash");
        return id;
    }
    private UUID exam(UUID admin) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO exams(id,title,kind,status,total_points,created_by) VALUES (?,'Report exam','EXAM','PUBLISHED',100,?)",
            id, admin);
        return id;
    }
    private void assignment(UUID admin, UUID child, UUID exam, LocalDate due) {
        db.update("INSERT INTO assignments(id,exam_id,student_id,due_at,created_by) VALUES (?,?,?,?,?)",
            UUID.randomUUID(), exam, child, Timestamp.from(due.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()), admin);
    }
    private void attempt(UUID child, UUID exam, LocalDate date, boolean published, String score) {
        UUID id = UUID.randomUUID();
        Instant submitted = date.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC);
        db.update("INSERT INTO exam_attempts(id,exam_id,student_id,attempt_number,status,started_at,submitted_at,final_score,published_at) " +
            "VALUES (?,?,?,1,'GRADED',?,?,?,?)", id, exam, child, Timestamp.from(submitted.minusSeconds(600)),
            Timestamp.from(submitted), new BigDecimal(score), published ? Timestamp.from(submitted.plusSeconds(60)) : null);
    }
    private static String withSchema(String url, String schema) {
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }
}
