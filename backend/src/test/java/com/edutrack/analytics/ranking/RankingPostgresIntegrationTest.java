package com.edutrack.analytics.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RankingPostgresIntegrationTest {
    private PostgreSQLContainer<?> container;
    private JdbcTemplate db;
    private RankingService ranking;
    private String baseUrl, username, password, schema;
    private UUID admin, classroom, first, second, parent, exam;

    @BeforeAll
    void setUp() throws Exception {
        baseUrl = System.getenv("EDUTRACK_TEST_DATABASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Set EDUTRACK_TEST_DATABASE_URL or start Docker");
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            baseUrl = container.getJdbcUrl(); username = container.getUsername(); password = container.getPassword();
        } else {
            username = System.getenv().getOrDefault("EDUTRACK_TEST_DATABASE_USER", "edutrack");
            password = System.getenv().getOrDefault("EDUTRACK_TEST_DATABASE_PASSWORD", "");
        }
        schema = "ranking_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(baseUrl, username, password);
             var statement = connection.createStatement()) { statement.execute("CREATE SCHEMA " + schema); }
        var source = new DriverManagerDataSource(baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema,
            username, password);
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
        db = new JdbcTemplate(source);
        ranking = new RankingService(db, new NamedParameterJdbcTemplate(source));
        admin = user("ADMIN_TEACHER"); first = user("STUDENT"); second = user("STUDENT"); parent = user("PARENT");
        classroom = UUID.randomUUID();
        db.update("INSERT INTO classrooms(id,code,name,subject,academic_year,status) VALUES (?,?,?,?,?,'ACTIVE')",
            classroom, classroom.toString(), "Ranking class", "Math", "2026-2027");
        enroll(first); enroll(second);
        exam = UUID.randomUUID();
        db.update("INSERT INTO exams(id,title,kind,status,total_points,created_by) VALUES (?,'Ranking exam','EXAM','PUBLISHED',10,?)",
            exam, admin);
        db.update("INSERT INTO assignments(id,exam_id,classroom_id,created_by) VALUES (?,?,?,?)",
            UUID.randomUUID(), exam, classroom, admin);
        // A direct assignment of the same exam must not double-count the class assignment.
        db.update("INSERT INTO assignments(id,exam_id,student_id,created_by) VALUES (?,?,?,?)",
            UUID.randomUUID(), exam, first, admin);
    }

    @AfterAll
    void tearDown() throws Exception {
        if (schema != null) try (var connection = DriverManager.getConnection(baseUrl, username, password);
            var statement = connection.createStatement()) { statement.execute("DROP SCHEMA " + schema + " CASCADE"); }
        if (container != null) container.stop();
    }

    @Test
    void unpublishedScoresNeverChangeRankingAndPrivateViewsContainNoClassmateDetails() throws Exception {
        attempt(first, new BigDecimal("8"), true);
        attempt(second, new BigDecimal("10"), false);
        var teacher = ranking.teacher(classroom, RankingService.Period.ALL, null);
        assertEquals(2, teacher.cohortSize());
        assertEquals(1, teacher.rows().getFirst().assignedCount());
        assertEquals(1, ranking.teacher(classroom, RankingService.Period.ALL, exam).rows().getFirst().assignedCount());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> ranking.teacher(classroom, RankingService.Period.ALL, UUID.randomUUID())).getStatusCode());
        assertEquals(first, teacher.rows().getFirst().studentId());
        assertEquals(BigDecimal.ZERO, teacher.rows().get(1).averagePercent());

        var own = ranking.student(first, classroom, RankingService.Period.ALL, null);
        assertEquals(1, own.rank());
        assertEquals(2, own.cohortSize());
        var json = new ObjectMapper().writeValueAsString(own);
        assertEquals(false, json.contains("studentName"));
        assertEquals(false, json.contains(second.toString()));
        assertEquals(false, json.contains("rows"));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> ranking.student(UUID.randomUUID(), classroom, RankingService.Period.ALL, null)).getStatusCode());
    }

    @Test
    void parentMustHaveVerifiedUnrevokedLink() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> ranking.parent(parent, first, classroom, RankingService.Period.ALL, null)).getStatusCode());
        UUID request = UUID.randomUUID();
        db.update("INSERT INTO parent_link_requests(id,student_id,parent_email,requested_by,status,expires_at) "
            + "VALUES (?,?,?,?,'ACCEPTED',?)", request, first, parent + "@example.test", admin,
            Timestamp.from(Instant.now().plusSeconds(3600)));
        db.update("INSERT INTO parent_student_links(id,parent_id,student_id,verified_request_id) VALUES (?,?,?,?)",
            UUID.randomUUID(), parent, first, request);
        assertEquals(1, ranking.parent(parent, first, classroom, RankingService.Period.ALL, null).rank());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> ranking.parent(parent, second, classroom, RankingService.Period.ALL, null)).getStatusCode());
        db.update("UPDATE parent_student_links SET revoked_at=now() WHERE parent_id=? AND student_id=?", parent, first);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> ranking.parent(parent, first, classroom, RankingService.Period.ALL, null)).getStatusCode());
    }

    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO users(id,email,full_name,role,status,password_hash) VALUES (?,?,?,?,'ACTIVE',?)",
            id, id + "@example.test", role + " learner", role, "test-only-hash");
        return id;
    }

    private void enroll(UUID studentId) {
        db.update("INSERT INTO student_enrollments(id,classroom_id,student_id,enrolled_by) VALUES (?,?,?,?)",
            UUID.randomUUID(), classroom, studentId, admin);
    }

    private void attempt(UUID studentId, BigDecimal score, boolean published) {
        Instant now = Instant.now();
        db.update("INSERT INTO exam_attempts(id,exam_id,student_id,attempt_number,status,started_at,submitted_at,final_score,published_at) "
            + "VALUES (?,?,?,1,'GRADED',?,?,?,?)", UUID.randomUUID(), exam, studentId,
            Timestamp.from(now.minusSeconds(60)), Timestamp.from(now), score,
            published ? Timestamp.from(now) : null);
    }
}
