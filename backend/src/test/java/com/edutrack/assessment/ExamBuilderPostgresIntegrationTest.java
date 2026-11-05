package com.edutrack.assessment;

import com.edutrack.notification.NotificationEventWriter;
import com.edutrack.question.QuestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.List;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExamBuilderPostgresIntegrationTest {
    private PostgreSQLContainer<?> container;
    private String baseUrl,username,password,schema;
    private JdbcTemplate db;
    private TransactionTemplate tx;
    private ExamAdminService exams;
    private QuestionService questions;
    private UUID admin;
    private final ObjectMapper json=new ObjectMapper();

    @BeforeAll void setup() throws Exception {
        baseUrl=System.getenv("EDUTRACK_TEST_DATABASE_URL");
        if(baseUrl==null || baseUrl.isBlank()) {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(),"Set EDUTRACK_TEST_DATABASE_URL or start Docker");
            container=new PostgreSQLContainer<>("postgres:16-alpine");container.start();
            baseUrl=container.getJdbcUrl();username=container.getUsername();password=container.getPassword();
        } else {
            username=System.getenv().getOrDefault("EDUTRACK_TEST_DATABASE_USER","edutrack");
            password=System.getenv().getOrDefault("EDUTRACK_TEST_DATABASE_PASSWORD","");
        }
        schema="exam_builder_it_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(baseUrl,username,password);
            var statement=connection.createStatement()) { statement.execute("CREATE SCHEMA "+schema); }
        var source=new DriverManagerDataSource(baseUrl+(baseUrl.contains("?")?"&":"?")+"currentSchema="+schema,username,password);
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
        db=new JdbcTemplate(source);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        questions=new QuestionService(db,json);
        exams=new ExamAdminService(db,questions,new NotificationEventWriter(db));
        admin=UUID.randomUUID();
        db.update("INSERT INTO users(id,email,full_name,role,status,password_hash) VALUES (?,?,?,'ADMIN_TEACHER','ACTIVE','test-only-hash')",
            admin,admin+"@example.test","Teacher");
    }

    @AfterAll void cleanup() throws Exception {
        if(schema!=null) try(var connection=DriverManager.getConnection(baseUrl,username,password);
            var statement=connection.createStatement()) { statement.execute("DROP SCHEMA "+schema+" CASCADE"); }
        if(container!=null) container.stop();
    }

    @Test void draftReorderRemoveAndPointOverridesKeepContiguousPositionsAndTotals() throws Exception {
        UUID examId=tx.execute(status->exams.create(input(),admin).id());
        var settings=new ExamAdminService.Input("Updated exam","Teacher instructions",ExamAdminService.Kind.HOMEWORK,
            null,null,60,2,true,true,true,true,true,false);
        var updated=tx.execute(status->exams.update(examId,settings,0));
        assertEquals("Updated exam",updated.title());
        assertEquals(1,updated.version());
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
            ()->tx.execute(status->exams.update(examId,settings,0))).getStatusCode());
        UUID first=tx.execute(status->exams.addInlineQuestion(examId,
            new ExamAdminService.InlineQuestion(question("First"),BigDecimal.valueOf(2),null),admin).questions().getFirst().id());
        UUID second=tx.execute(status->exams.addInlineQuestion(examId,
            new ExamAdminService.InlineQuestion(question("Second"),BigDecimal.valueOf(3),null),admin).questions().get(1).id());
        var reordered=tx.execute(status->exams.reorderQuestions(examId,new ExamAdminService.QuestionOrder(List.of(second,first))));
        assertEquals(List.of(second,first),reordered.questions().stream().map(ExamAdminService.QuestionView::id).toList());
        assertEquals(List.of(0,1),reordered.questions().stream().map(ExamAdminService.QuestionView::position).toList());
        var changed=tx.execute(status->exams.changePoints(examId,first,new ExamAdminService.PointsInput(BigDecimal.valueOf(4))));
        assertEquals(0,changed.exam().totalPoints().compareTo(BigDecimal.valueOf(7)));
        var removed=tx.execute(status->exams.removeQuestion(examId,second));
        assertEquals(List.of(first),removed.questions().stream().map(ExamAdminService.QuestionView::id).toList());
        assertEquals(0,removed.questions().getFirst().position());
        assertEquals(0,removed.exam().totalPoints().compareTo(BigDecimal.valueOf(4)));
        tx.execute(status->exams.publish(examId));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
            ()->tx.execute(status->exams.removeQuestion(examId,first))).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
            ()->tx.execute(status->exams.update(examId,settings,removed.exam().version()))).getStatusCode());
    }

    private ExamAdminService.Input input() {
        return new ExamAdminService.Input("Exam",null,ExamAdminService.Kind.EXAM,null,null,null,1,
            false,false,false,false,true,false);
    }
    private QuestionService.Input question(String body) {
        return new QuestionService.Input(QuestionService.Type.TRUE_FALSE,"Math",null,"Basics",null,null,
            QuestionService.Difficulty.EASY,body,json.createObjectNode(),json.createObjectNode().put("correct",true),
            null,null,BigDecimal.ONE);
    }
}
