package com.edutrack.notification;

import com.edutrack.identity.NotificationSender;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmailOutboxPostgresIntegrationTest {
    private PostgreSQLContainer<?> container;
    private String baseUrl, username, password, schema;
    private JdbcTemplate db;
    private UUID admin;

    @BeforeAll
    void setUp() throws Exception {
        baseUrl = System.getenv("EDUTRACK_TEST_DATABASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker unavailable; set EDUTRACK_TEST_DATABASE_URL to a disposable PostgreSQL test DB");
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            baseUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            username = System.getenv().getOrDefault("EDUTRACK_TEST_DATABASE_USER", "edutrack");
            password = System.getenv().getOrDefault("EDUTRACK_TEST_DATABASE_PASSWORD", "");
        }
        schema = "edutrack_mail_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(baseUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        var source = new DriverManagerDataSource(baseUrl + (baseUrl.contains("?") ? "&" : "?")
            + "currentSchema=" + schema, username, password);
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
        db = new JdbcTemplate(source);
        admin = user("ADMIN_TEACHER");
    }

    @AfterAll
    void tearDown() throws Exception {
        if (schema != null) {
            try (var connection = DriverManager.getConnection(baseUrl, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
        if (container != null) container.stop();
    }

    @Test
    void committedResultEventSendsOnlyToStillAuthorizedRecipients() {
        UUID child = user("STUDENT"), parent = user("PARENT"), exam = UUID.randomUUID(), attempt = UUID.randomUUID();
        db.update("INSERT INTO exams(id,title,kind,status,created_by) VALUES (?,'Đề thử','EXAM','PUBLISHED',?)", exam, admin);
        db.update("INSERT INTO exam_attempts(id,exam_id,student_id,attempt_number,status,published_at) " +
            "VALUES (?,?,?,1,'GRADED',now())", attempt, exam, child);
        UUID request = UUID.randomUUID();
        db.update("INSERT INTO parent_link_requests(id,student_id,parent_email,requested_by,status,expires_at) " +
            "VALUES (?,?,?,?,'ACCEPTED',now()+interval '1 day')", request, child, parent + "@example.test", admin);
        db.update("INSERT INTO parent_student_links(parent_id,student_id,verified_request_id) VALUES (?,?,?)",
            parent, child, request);
        new NotificationEventWriter(db).resultPublished(attempt);
        assertEquals(2L, db.queryForObject("SELECT count(*) FROM email_logs WHERE source_id=?", Long.class, attempt));

        db.update("UPDATE parent_student_links SET revoked_at=now() WHERE parent_id=?", parent);
        NotificationSender mail = mock(NotificationSender.class);
        new EmailOutboxWorker(db, mail, true).deliverPending();
        verify(mail, times(1)).send(eq(child + "@example.test"), anyString(), anyString());
        verify(mail, never()).send(eq(parent + "@example.test"), anyString(), anyString());
        assertEquals("CANCELLED", db.queryForObject("SELECT e.status FROM email_logs e " +
            "JOIN notifications n ON n.id=e.notification_id WHERE n.recipient_id=?", String.class, parent));
        assertEquals("SENT", db.queryForObject("SELECT e.status FROM email_logs e " +
            "JOIN notifications n ON n.id=e.notification_id WHERE n.recipient_id=?", String.class, child));
        new EmailOutboxWorker(db, mail, true).deliverPending();
        verifyNoMoreInteractions(mail);
    }

    @Test
    void revokedAssignmentIsCancelledBeforeEmailDelivery() {
        UUID child = user("STUDENT"), exam = UUID.randomUUID(), assignment = UUID.randomUUID();
        db.update("INSERT INTO exams(id,title,kind,status,created_by) VALUES (?,'Đề thử','EXAM','PUBLISHED',?)", exam, admin);
        db.update("INSERT INTO assignments(id,exam_id,student_id,created_by) VALUES (?,?,?,?)",
            assignment, exam, child, admin);
        new NotificationEventWriter(db).assignmentCreated(assignment, "Bài tập");
        db.update("UPDATE assignments SET revoked_at=now() WHERE id=?", assignment);
        NotificationSender mail = mock(NotificationSender.class);
        new EmailOutboxWorker(db, mail, true).deliverPending();
        verifyNoInteractions(mail);
        assertEquals("CANCELLED", db.queryForObject("SELECT status FROM email_logs WHERE source_id=?",
            String.class, assignment));
    }

    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO users(id,email,full_name,role,status,password_hash) " +
            "VALUES (?,?,?,?,'ACTIVE','test-only-hash')", id, id + "@example.test", "Integration user", role);
        return id;
    }
}
