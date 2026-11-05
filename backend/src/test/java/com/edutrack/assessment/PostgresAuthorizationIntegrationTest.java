package com.edutrack.assessment;

import com.edutrack.classroom.SchoolController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.edutrack.notification.NotificationEventWriter;
import com.edutrack.file.FileStorage;
import com.edutrack.question.QuestionService;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/** Real PostgreSQL/Flyway tests. Set EDUTRACK_TEST_DATABASE_URL/USER/PASSWORD to use an existing test DB;
 * otherwise Testcontainers starts PostgreSQL when Docker is available. Each run owns an isolated schema. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresAuthorizationIntegrationTest {
    private PostgreSQLContainer<?> container;
    private JdbcTemplate db;
    private StudentExamService studentExams;
    private ParentResultService parentResults;
    private String schema;
    private String baseUrl;
    private String username;
    private String password;
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
        schema = "edutrack_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(baseUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        var source = new DriverManagerDataSource(withSchema(baseUrl, schema), username, password);
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
        db = new JdbcTemplate(source);
        studentExams = new StudentExamService(db, new ObjectMapper(), new NotificationEventWriter(db));
        parentResults = new ParentResultService(db);
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
    void studentCannotReadOrModifyAnotherStudentsAttemptAndWrongAnswerScoresZero() throws Exception {
        UUID owner = user("STUDENT"), other = user("STUDENT");
        UUID exam = exam(false, 2), question = question(exam);
        assign(exam, owner);
        UUID attempt = studentExams.start(exam, owner).attempt().id();
        var wrong = new StudentExamService.AnswerInput(new ObjectMapper().readTree("{\"selectedId\":\"B\"}"), false);

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> studentExams.get(attempt, other)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> studentExams.save(attempt, question, other, wrong)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> studentExams.submit(attempt, other, new StudentExamService.SubmitInput(UUID.randomUUID()))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> studentExams.result(attempt, other)).getStatusCode());

        studentExams.save(attempt, question, owner, wrong);
        studentExams.submit(attempt, owner, new StudentExamService.SubmitInput(UUID.randomUUID()));
        assertEquals(0, db.queryForObject("SELECT final_score FROM exam_attempts WHERE id=?", BigDecimal.class, attempt)
            .compareTo(BigDecimal.ZERO));
        assertEquals(0, db.queryForObject("SELECT automatic_score FROM grading_results WHERE attempt_id=?", BigDecimal.class, attempt)
            .compareTo(BigDecimal.ZERO));
    }

    @Test
    void parentSeesOnlyPublishedResultsWhileVerifiedLinkIsActive() {
        UUID child = user("STUDENT"), otherChild = user("STUDENT"), parent = user("PARENT");
        UUID exam = exam(false, 1), question = question(exam);
        UUID published = gradedAttempt(exam, question, child, true);
        UUID unpublished = gradedAttempt(exam, question, child, false);
        UUID otherAttempt = gradedAttempt(exam, question, otherChild, true);

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> parentResults.list(parent, child, 0, 20)).getStatusCode());
        link(parent, child);
        var page = parentResults.list(parent, child, 0, 20);
        assertEquals(1, page.total());
        assertEquals(published, page.items().getFirst().attemptId());
        assertEquals(1, parentResults.detail(parent, child, published).questions().size());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> parentResults.detail(parent, child, unpublished)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> parentResults.detail(parent, child, otherAttempt)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> parentResults.list(parent, otherChild, 0, 20)).getStatusCode());

        db.update("UPDATE parent_student_links SET revoked_at=now() WHERE parent_id=? AND student_id=?", parent, child);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> parentResults.list(parent, child, 0, 20)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> parentResults.detail(parent, child, published)).getStatusCode());
    }

    @Test
    void parentClassroomListRequiresCurrentVerifiedLink() {
        UUID child = user("STUDENT"), otherChild = user("STUDENT"), parent = user("PARENT");
        UUID classroom = UUID.randomUUID();
        db.update("INSERT INTO classrooms(id,code,name,subject,academic_year,status) VALUES (?,?,?,?,?,'ACTIVE')",
            classroom, "class-" + classroom.toString().substring(0, 32), "Lớp tích hợp", "Toán", "2026");
        db.update("INSERT INTO student_enrollments(id,classroom_id,student_id,enrolled_by) VALUES (?,?,?,?)",
            UUID.randomUUID(), classroom, child, admin);
        var principal = new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "none")
            .subject(parent.toString()).build());
        var school = new SchoolController(null, null, null, db);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> school.childClassrooms(child, principal)).getStatusCode());
        link(parent, child);
        assertEquals(classroom, school.childClassrooms(child, principal).getFirst().id());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> school.childClassrooms(otherChild, principal)).getStatusCode());
        db.update("UPDATE parent_student_links SET revoked_at=now() WHERE parent_id=? AND student_id=?", parent, child);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> school.childClassrooms(child, principal)).getStatusCode());
    }

    @Test
    void newClassAssignmentNotifiesOnlyActiveEnrolledStudents() {
        UUID enrolled=user("STUDENT"), departed=user("STUDENT"), unrelated=user("STUDENT");
        UUID classroom=UUID.randomUUID(), exam=exam(false,1);
        db.update("INSERT INTO classrooms(id,code,name,subject,academic_year,status) VALUES (?,?,?,?,?,'ACTIVE')",
            classroom,"class-"+classroom.toString().substring(0,32),"Lớp thông báo","Toán","2026");
        db.update("INSERT INTO student_enrollments(id,classroom_id,student_id,enrolled_by) VALUES (?,?,?,?)",
            UUID.randomUUID(),classroom,enrolled,admin);
        db.update("INSERT INTO student_enrollments(id,classroom_id,student_id,enrolled_by,left_at) VALUES (?,?,?,?,now())",
            UUID.randomUUID(),classroom,departed,admin);
        var service=new ExamAdminService(db,mock(QuestionService.class),new NotificationEventWriter(db));
        service.assign(exam,new ExamAdminService.AssignmentInput(classroom,null,null),admin);
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id=? AND type='ASSIGNMENT'",Long.class,enrolled));
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id IN (?,?) AND type='ASSIGNMENT'",Long.class,departed,unrelated));
    }

    @Test
    void immediateResultNotifiesStudentAndVerifiedParentExactlyOnceOnRetry() {
        UUID child=user("STUDENT"),otherChild=user("STUDENT"),parent=user("PARENT"),otherParent=user("PARENT");
        link(parent,child);link(otherParent,otherChild);
        UUID exam=exam(true,1);question(exam);assign(exam,child);
        UUID attempt=studentExams.start(exam,child).attempt().id(),key=UUID.randomUUID();
        studentExams.submit(attempt,child,new StudentExamService.SubmitInput(key));
        studentExams.submit(attempt,child,new StudentExamService.SubmitInput(key));
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id=? AND type='RESULT'",Long.class,child));
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id=? AND type='RESULT'",Long.class,parent));
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id IN (?,?) AND type='RESULT'",Long.class,otherChild,otherParent));
    }

    @Test
    void manualPublicationNotifiesOnceAndExcludesRevokedParent() {
        UUID child=user("STUDENT"),parent=user("PARENT"),exam=exam(false,1),question=question(exam);
        link(parent,child);
        db.update("UPDATE parent_student_links SET revoked_at=now() WHERE parent_id=? AND student_id=?",parent,child);
        UUID attempt=gradedAttempt(exam,question,child,false);
        var service=new AdminGradingService(db,new ObjectMapper(),mock(GeminiClient.class),mock(FileStorage.class),
            new DataSourceTransactionManager(db.getDataSource()),new NotificationEventWriter(db));
        service.publish(attempt);
        service.publish(attempt);
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id=? AND type='RESULT'",Long.class,child));
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id=? AND type='RESULT'",Long.class,parent));
    }

    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO users(id,email,full_name,role,status,password_hash) VALUES (?,?,? ,?,'ACTIVE',?)",
            id, id + "@example.test", "Integration user", role, "not-a-real-password-hash");
        return id;
    }

    private UUID exam(boolean immediate, int maxAttempts) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO exams(id,title,kind,status,max_attempts,show_result_immediately,total_points,created_by) " +
            "VALUES (?,'Integration exam','EXAM','PUBLISHED',?,?,2,?)", id, maxAttempts, immediate, admin);
        return id;
    }

    private UUID question(UUID exam) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO exam_questions(id,exam_id,position,type,body,content,answer_key,points) " +
            "VALUES (?,?,0,'SINGLE_CHOICE','Choose A',cast(? as jsonb),cast(? as jsonb),2)", id, exam,
            "{\"options\":[{\"id\":\"A\",\"text\":\"A\"},{\"id\":\"B\",\"text\":\"B\"}]}", "{\"correctId\":\"A\"}");
        return id;
    }

    private void assign(UUID exam, UUID student) {
        db.update("INSERT INTO assignments(id,exam_id,student_id,created_by) VALUES (?,?,?,?)",
            UUID.randomUUID(), exam, student, admin);
    }

    private UUID gradedAttempt(UUID exam, UUID question, UUID student, boolean published) {
        UUID id = UUID.randomUUID();
        Integer previous = db.queryForObject("SELECT count(*) FROM exam_attempts WHERE exam_id=? AND student_id=?",
            Integer.class, exam, student);
        db.update("INSERT INTO exam_attempts(id,exam_id,student_id,attempt_number,status,started_at,submitted_at,final_score,published_at) " +
            "VALUES (?,?,?,?,'GRADED',?,?,2,?)", id, exam, student, previous + 1,
            Timestamp.from(Instant.now().minusSeconds(60)), Timestamp.from(Instant.now()),
            published ? Timestamp.from(Instant.now()) : null);
        db.update("INSERT INTO grading_results(id,attempt_id,exam_question_id,status,automatic_score) " +
            "VALUES (?,?,?,'AUTO_GRADED',2)", UUID.randomUUID(), id, question);
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

    private static String withSchema(String url, String schema) {
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }
}
