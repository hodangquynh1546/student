package com.edutrack.file;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs against an isolated PostgreSQL schema and a temporary local file store. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttachmentPostgresIntegrationTest {
    private PostgreSQLContainer<?> container;
    private String baseUrl, username, password, schema;
    private Path fileRoot;
    private JdbcTemplate db;
    private TransactionTemplate tx;
    private AttachmentService service;
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
        schema = "edutrack_file_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(baseUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        var source = new DriverManagerDataSource(baseUrl + (baseUrl.contains("?") ? "&" : "?") +
            "currentSchema=" + schema, username, password);
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
        db = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        fileRoot = Files.createTempDirectory("edutrack-attachment-it-");
        service = new AttachmentService(db, new LocalFileStorage(fileRoot.toString()), 1);
        admin = user("ADMIN_TEACHER");
    }

    @AfterAll
    void tearDown() throws Exception {
        if (fileRoot != null) {
            try (var paths = Files.walk(fileRoot)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        if (schema != null) {
            try (var connection = DriverManager.getConnection(baseUrl, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
        if (container != null) container.stop();
    }

    @Test
    void ownerTeacherAndVerifiedParentHaveCorrectAccessBeforeAndAfterPublicationAndRevocation() {
        UUID owner = user("STUDENT"), other = user("STUDENT"), parent = user("PARENT");
        UUID exam = exam(), question = question(exam, "ESSAY"), attempt = attempt(exam, owner);
        byte[] image = new byte[]{(byte) 255, (byte) 216, (byte) 255, 1, 2};
        var attachment = tx.execute(status -> service.upload(attempt, question, owner,
            new MockMultipartFile("file", "work.jpg", "application/octet-stream", image)));
        assertNotNull(attachment);
        assertEquals("image/jpeg", attachment.mimeType());
        assertArrayEquals(image, service.download(attachment.id(), owner, "STUDENT").bytes());
        assertEquals(1, service.list(attempt, question, owner, "STUDENT").size());
        assertEquals(1, service.list(attempt, question, admin, "ADMIN_TEACHER").size());
        assertArrayEquals(image, service.download(attachment.id(), admin, "ADMIN_TEACHER").bytes());
        assertDenied(() -> service.list(attempt, question, other, "STUDENT"));
        assertDenied(() -> service.download(attachment.id(), other, "STUDENT"));

        link(parent, owner);
        assertDenied(() -> service.list(attempt, question, parent, "PARENT"));
        assertDenied(() -> service.download(attachment.id(), parent, "PARENT"));
        db.update("UPDATE exam_attempts SET status='GRADED',submitted_at=now(),published_at=now() WHERE id=?", attempt);
        assertEquals(1, service.list(attempt, question, parent, "PARENT").size());
        assertArrayEquals(image, service.download(attachment.id(), parent, "PARENT").bytes());
        db.update("UPDATE parent_student_links SET revoked_at=now() WHERE parent_id=? AND student_id=?", parent, owner);
        assertDenied(() -> service.list(attempt, question, parent, "PARENT"));
        assertDenied(() -> service.download(attachment.id(), parent, "PARENT"));
    }

    @Test
    void uploadRejectsInvalidSignatureOversizeOtherStudentAndNonEssay() {
        UUID owner = user("STUDENT"), other = user("STUDENT");
        UUID exam = exam(), essay = question(exam, "ESSAY"), choice = question(exam, "SINGLE_CHOICE");
        UUID attempt = attempt(exam, owner);
        int filesBefore = filesCount();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> tx.execute(status -> service.upload(attempt, essay, other, file(new byte[]{(byte) 255,(byte) 216,(byte) 255,1}))))
            .getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> tx.execute(status -> service.upload(attempt, essay, owner, file(new byte[]{'M','Z',0,1}))))
            .getStatusCode());
        byte[] tooLarge = new byte[1024 * 1024 + 1];
        tooLarge[0] = (byte) 255; tooLarge[1] = (byte) 216; tooLarge[2] = (byte) 255;
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> tx.execute(status -> service.upload(attempt, essay, owner, file(tooLarge))))
            .getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> tx.execute(status -> service.upload(attempt, choice, owner, file(new byte[]{(byte) 255,(byte) 216,(byte) 255,1}))))
            .getStatusCode());
        assertEquals(0, db.queryForObject("SELECT count(*) FROM answer_attachments WHERE attempt_id=?", Integer.class, attempt));
        assertEquals(filesBefore, filesCount());
    }

    @Test
    void uploadAllowsFiveFilesThenRejectsMoreAndRejectsFinishedAttempt() {
        UUID owner = user("STUDENT"), exam = exam(), essay = question(exam, "ESSAY"), attempt = attempt(exam, owner);
        byte[] image = new byte[]{(byte) 255, (byte) 216, (byte) 255, 1};
        for (int i = 0; i < 5; i++) {
            assertNotNull(tx.execute(status -> service.upload(attempt, essay, owner, file(image))));
        }
        assertEquals(5, service.list(attempt, essay, owner, "STUDENT").size());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> tx.execute(status -> service.upload(attempt, essay, owner, file(image)))).getStatusCode());
        db.update("UPDATE exam_attempts SET status='EXPIRED' WHERE id=?", attempt);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> tx.execute(status -> service.upload(attempt, essay, owner, file(image)))).getStatusCode());
    }

    private int filesCount() {
        try (var paths = Files.list(fileRoot)) { return (int) paths.count(); }
        catch (Exception e) { throw new AssertionError(e); }
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "test.bin", "image/jpeg", bytes);
    }

    private void assertDenied(org.junit.jupiter.api.function.Executable action) {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, action).getStatusCode());
    }

    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO users(id,email,full_name,role,status,password_hash) VALUES (?,?,?,?,'ACTIVE',?)",
            id, id + "@example.test", "Integration user", role, "not-a-real-password-hash");
        return id;
    }

    private UUID exam() {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO exams(id,title,kind,status,total_points,created_by) VALUES (?,'Attachment test','EXAM','PUBLISHED',2,?)",
            id, admin);
        return id;
    }

    private UUID question(UUID exam, String type) {
        UUID id = UUID.randomUUID();
        Integer position = db.queryForObject("SELECT count(*) FROM exam_questions WHERE exam_id=?", Integer.class, exam);
        db.update("INSERT INTO exam_questions(id,exam_id,position,type,body,content,answer_key,points) " +
            "VALUES (?,?,?,?,'Attachment question',cast('{}' as jsonb),cast('{}' as jsonb),2)", id, exam, position, type);
        return id;
    }

    private UUID attempt(UUID exam, UUID student) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO exam_attempts(id,exam_id,student_id,attempt_number,status) VALUES (?,?,?,1,'IN_PROGRESS')",
            id, exam, student);
        return id;
    }

    private void link(UUID parent, UUID child) {
        UUID request = UUID.randomUUID();
        db.update("INSERT INTO parent_link_requests(id,student_id,parent_email,requested_by,status,expires_at) " +
            "VALUES (?,?,?,?,'ACCEPTED',?)", request, child, parent + "@example.test", admin,
            Timestamp.from(Instant.now().plusSeconds(3600)));
        db.update("INSERT INTO parent_student_links(id,parent_id,student_id,verified_request_id) VALUES (?,?,?,?)",
            UUID.randomUUID(), parent, child, request);
    }
}
