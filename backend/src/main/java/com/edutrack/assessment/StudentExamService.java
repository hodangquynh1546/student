package com.edutrack.assessment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.edutrack.notification.NotificationEventWriter;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentExamService {
    public record ExamSummary(UUID id,String title,String kind,Instant startsAt,Instant endsAt,Instant dueAt,
                              int maxAttempts,int attemptsUsed,String attemptStatus,UUID openAttemptId) {}
    public record Page(List<ExamSummary> items,long total,int page,int size) {}
    public record PublicQuestion(UUID id,String type,String body,JsonNode content,BigDecimal points) {}
    public record SavedAnswer(UUID questionId,JsonNode answer,boolean markedForReview,Instant savedAt) {}
    public record AttemptView(UUID id,UUID examId,String status,Instant startedAt,Instant deadlineAt,Instant serverTime,
                              List<PublicQuestion> questions,List<SavedAnswer> answers) {}
    public record StartResult(boolean created,AttemptView attempt) {}
    public record AnswerInput(JsonNode answer,boolean markedForReview) {}
    public record SubmitInput(UUID submitKey) {}
    public record Submission(UUID id,UUID examId,String status,Instant submittedAt,Instant serverTime) {}
    public record Result(UUID id,UUID examId,String status,BigDecimal score,BigDecimal totalPoints,Instant publishedAt) {}
    private record Exam(UUID id,String status,Instant startsAt,Instant endsAt,Integer durationMinutes,int maxAttempts,
                        boolean showResultImmediately,boolean allowLate,BigDecimal totalPoints) {}
    private record Attempt(UUID id,UUID examId,UUID studentId,String status,Instant startedAt,Instant deadlineAt,
                           Instant submittedAt,UUID submitKey,BigDecimal finalScore,Instant publishedAt) {}
    private record GradingQuestion(UUID id,String type,JsonNode key,JsonNode answer,BigDecimal points) {}

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final NotificationEventWriter notifications;
    public StudentExamService(JdbcTemplate db,ObjectMapper json,NotificationEventWriter notifications) {
        this.db=db;this.json=json;this.notifications=notifications;
    }

    @Scheduled(fixedDelayString="${edutrack.exam-expiry-scan-ms:30000}")
    @Transactional
    public void expireOverdueAttempts() {
        Instant now=Instant.now();
        db.query("SELECT id,exam_id,student_id,status,started_at,deadline_at,submitted_at,submit_key,final_score,published_at " +
            "FROM exam_attempts WHERE status='IN_PROGRESS' AND deadline_at IS NOT NULL AND deadline_at<=? " +
            "ORDER BY deadline_at LIMIT 100 FOR UPDATE SKIP LOCKED",this::mapAttempt,Timestamp.from(now))
            .forEach(a->complete(a,null,now));
    }

    @Transactional(readOnly=true)
    public Page list(UUID student,int page,int size) {
        if(page<0 || size<1 || size>100) throw bad("Phân trang không hợp lệ");
        String eligible=" FROM exams e JOIN assignments a ON a.exam_id=e.id AND a.revoked_at IS NULL " +
            "WHERE e.status='PUBLISHED' AND e.deleted_at IS NULL AND (a.student_id=? OR EXISTS " +
            "(SELECT 1 FROM student_enrollments se JOIN classrooms c ON c.id=se.classroom_id " +
            "WHERE se.student_id=? AND se.classroom_id=a.classroom_id AND se.left_at IS NULL AND c.deleted_at IS NULL))";
        Long total=db.queryForObject("SELECT count(DISTINCT e.id)"+eligible,Long.class,student,student);
        String sql="SELECT e.id,e.title,e.kind,e.starts_at,e.ends_at,min(a.due_at) AS due_at,e.max_attempts," +
            "(SELECT count(*) FROM exam_attempts t WHERE t.exam_id=e.id AND t.student_id=?) AS attempts_used," +
            "(SELECT t.status FROM exam_attempts t WHERE t.exam_id=e.id AND t.student_id=? ORDER BY t.attempt_number DESC LIMIT 1) AS attempt_status," +
            "(SELECT t.id FROM exam_attempts t WHERE t.exam_id=e.id AND t.student_id=? AND t.status='IN_PROGRESS' LIMIT 1) AS open_attempt_id" +
            eligible+" GROUP BY e.id ORDER BY e.starts_at NULLS FIRST,e.created_at DESC LIMIT ? OFFSET ?";
        List<ExamSummary> items=db.query(sql,(rs,row)->new ExamSummary(rs.getObject("id",UUID.class),rs.getString("title"),
            rs.getString("kind"),time(rs,"starts_at"),time(rs,"ends_at"),time(rs,"due_at"),rs.getInt("max_attempts"),
            rs.getInt("attempts_used"),rs.getString("attempt_status"),rs.getObject("open_attempt_id",UUID.class)),
            student,student,student,student,student,size,(long)page*size);
        return new Page(items,total==null?0:total,page,size);
    }

    @Transactional(noRollbackFor=ResponseStatusException.class)
    public StartResult start(UUID examId,UUID student) {
        // Serialise all starts for this student, including requests for the same exam on different connections.
        if(db.query("SELECT id FROM users WHERE id=? AND role='STUDENT' AND status='ACTIVE' FOR UPDATE",
            (rs,row)->rs.getObject(1,UUID.class),student).isEmpty()) throw forbidden();
        Exam exam=exam(examId);
        Instant now=Instant.now();
        if(!"PUBLISHED".equals(exam.status()) || (exam.startsAt()!=null && now.isBefore(exam.startsAt())) ||
            (exam.endsAt()!=null && !now.isBefore(exam.endsAt()))) throw conflict("Bài kiểm tra chưa mở hoặc đã đóng");
        if(!eligible(examId,student,now,exam.allowLate())) throw forbidden();
        Attempt open=db.query(ATTEMPT_SQL+" AND status='IN_PROGRESS' FOR UPDATE",this::mapAttempt,examId,student)
            .stream().findFirst().orElse(null);
        if(open!=null) {
            if(expired(open,now)) complete(open,null,now);
            else return new StartResult(false,view(open,now));
        }
        Integer used=db.queryForObject("SELECT count(*) FROM exam_attempts WHERE exam_id=? AND student_id=?",Integer.class,examId,student);
        if(used!=null && used>=exam.maxAttempts()) throw conflict("Đã hết số lần làm bài");
        Instant deadline=exam.durationMinutes()==null?null:now.plus(exam.durationMinutes(),ChronoUnit.MINUTES);
        if(exam.endsAt()!=null && (deadline==null || exam.endsAt().isBefore(deadline))) deadline=exam.endsAt();
        if(!exam.allowLate()) {
            Instant due=dueAt(examId,student);
            if(due!=null && (deadline==null || due.isBefore(deadline))) deadline=due;
        }
        if(deadline!=null && !now.isBefore(deadline)) throw conflict("Đã quá hạn làm bài");
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO exam_attempts(id,exam_id,student_id,attempt_number,status,started_at,deadline_at) VALUES (?,?,?,?,'IN_PROGRESS',?,?)",
            id,examId,student,(used==null?0:used)+1,Timestamp.from(now),stamp(deadline));
        return new StartResult(true,view(attempt(id,student,false),now));
    }

    @Transactional(noRollbackFor=ResponseStatusException.class)
    public AttemptView get(UUID attemptId,UUID student) {
        Attempt attempt=attempt(attemptId,student,true);
        Instant now=Instant.now();
        if("IN_PROGRESS".equals(attempt.status()) && expired(attempt,now)) { complete(attempt,null,now);attempt=attempt(attemptId,student,false); }
        return view(attempt,now);
    }

    @Transactional(noRollbackFor=ResponseStatusException.class)
    public SavedAnswer save(UUID attemptId,UUID questionId,UUID student,AnswerInput input) {
        if(input==null || input.answer()==null || input.answer().isNull() || input.answer().toString().length()>50000)
            throw bad("Câu trả lời không hợp lệ");
        Attempt attempt=attempt(attemptId,student,true);
        Instant now=Instant.now();
        if(expired(attempt,now)) { complete(attempt,null,now);throw conflict("Đã hết thời gian làm bài"); }
        if(!"IN_PROGRESS".equals(attempt.status())) throw conflict("Bài đã nộp hoặc đã đóng");
        var questions=db.query("SELECT type,content FROM exam_questions WHERE id=? AND exam_id=?",
            (rs,row)->new PublicQuestion(questionId,rs.getString("type"),"",parse(rs.getString("content")),null),questionId,attempt.examId());
        if(questions.isEmpty()) throw notFound();
        AnswerValidator.validate(questions.getFirst().type(),questions.getFirst().content(),input.answer());
        db.update("INSERT INTO student_answers(id,attempt_id,exam_question_id,answer,marked_for_review,saved_at) " +
            "VALUES (?,?,?,cast(? as jsonb),?,?) ON CONFLICT(attempt_id,exam_question_id) DO UPDATE SET " +
            "answer=EXCLUDED.answer,marked_for_review=EXCLUDED.marked_for_review,saved_at=EXCLUDED.saved_at,version=student_answers.version+1",
            UUID.randomUUID(),attemptId,questionId,input.answer().toString(),input.markedForReview(),Timestamp.from(now));
        return new SavedAnswer(questionId,input.answer(),input.markedForReview(),now);
    }

    @Transactional(noRollbackFor=ResponseStatusException.class)
    public Submission submit(UUID attemptId,UUID student,SubmitInput input) {
        if(input==null || input.submitKey()==null) throw bad("Thiếu mã nộp bài");
        Attempt attempt=attempt(attemptId,student,true);
        Instant now=Instant.now();
        if(!"IN_PROGRESS".equals(attempt.status())) {
            if(input.submitKey().equals(attempt.submitKey()))
                return new Submission(attempt.id(),attempt.examId(),attempt.status(),attempt.submittedAt(),now);
            throw conflict("Bài đã nộp hoặc đã đóng");
        }
        if(expired(attempt,now)) { complete(attempt,null,now);throw conflict("Đã hết thời gian làm bài"); }
        String status=complete(attempt,input.submitKey(),now);
        return new Submission(attempt.id(),attempt.examId(),status,now,now);
    }

    private String complete(Attempt attempt,UUID submitKey,Instant now) {
        UUID attemptId=attempt.id();
        List<GradingQuestion> questions=db.query("SELECT q.id,q.type,q.answer_key,q.points,a.answer FROM exam_questions q " +
            "LEFT JOIN student_answers a ON a.exam_question_id=q.id AND a.attempt_id=? WHERE q.exam_id=? ORDER BY q.position",
            (rs,row)->new GradingQuestion(rs.getObject("id",UUID.class),rs.getString("type"),parse(rs.getString("answer_key")),
                rs.getString("answer")==null?null:parse(rs.getString("answer")),rs.getBigDecimal("points")),attemptId,attempt.examId());
        BigDecimal objective=BigDecimal.ZERO;
        boolean hasEssay=false;
        for(GradingQuestion q:questions) {
            boolean essay="ESSAY".equals(q.type());
            hasEssay|=essay;
            BigDecimal score=essay?null:ObjectiveGrader.score(q.type(),q.key(),q.answer(),q.points());
            if(score!=null) objective=objective.add(score);
            db.update("INSERT INTO grading_results(id,attempt_id,exam_question_id,status,automatic_score) VALUES (?,?,?,?,?)",
                UUID.randomUUID(),attemptId,q.id(),essay?"PENDING_GRADING":"AUTO_GRADED",score);
        }
        String status=hasEssay?"PENDING_GRADING":"GRADED";
        boolean publish=!hasEssay && exam(attempt.examId()).showResultImmediately();
        db.update("UPDATE exam_attempts SET status=?,submitted_at=?,submit_key=?,objective_score=?,final_score=?,graded_at=?,published_at=?,version=version+1 WHERE id=?",
            status,Timestamp.from(now),submitKey,objective,hasEssay?null:objective,
            hasEssay?null:Timestamp.from(now),publish?Timestamp.from(now):null,attemptId);
        if(publish) notifications.resultPublished(attemptId);
        return status;
    }

    @Transactional(readOnly=true)
    public Result result(UUID attemptId,UUID student) {
        Attempt a=attempt(attemptId,student,false);
        Exam e=exam(a.examId());
        if(a.submittedAt()==null || (a.publishedAt()==null && (!e.showResultImmediately() || a.finalScore()==null))) throw forbidden();
        return new Result(a.id(),a.examId(),a.status(),a.finalScore(),e.totalPoints(),a.publishedAt());
    }

    private AttemptView view(Attempt a,Instant now) {
        List<PublicQuestion> questions=db.query("SELECT id,type,body,content,points FROM exam_questions WHERE exam_id=? ORDER BY position",
            (rs,row)->new PublicQuestion(rs.getObject("id",UUID.class),rs.getString("type"),rs.getString("body"),
                publicContent(rs.getString("type"),parse(rs.getString("content"))),rs.getBigDecimal("points")),a.examId());
        List<SavedAnswer> answers=db.query("SELECT exam_question_id,answer,marked_for_review,saved_at FROM student_answers WHERE attempt_id=?",
            (rs,row)->new SavedAnswer(rs.getObject("exam_question_id",UUID.class),parse(rs.getString("answer")),
                rs.getBoolean("marked_for_review"),time(rs,"saved_at")),a.id());
        return new AttemptView(a.id(),a.examId(),a.status(),a.startedAt(),a.deadlineAt(),now,questions,answers);
    }
    // Use a per-type allowlist; unrecognised nested fields never cross the student API boundary.
    JsonNode publicContent(String type,JsonNode source) {
        ObjectNode safe=json.createObjectNode();
        switch(type) {
            case "SINGLE_CHOICE","MULTIPLE_CHOICE" -> copyItems(source,safe,"options");
            case "MATCHING" -> {copyItems(source,safe,"left");copyItems(source,safe,"right");}
            case "ORDERING" -> copyItems(source,safe,"items");
            case "FILL_IN_THE_BLANK" -> {copyItems(source,safe,"blanks");copyText(source,safe,"template");}
            default -> { }
        }
        return safe;
    }
    private void copyItems(JsonNode source,ObjectNode target,String key) {
        ArrayNode out=target.putArray(key);
        for(JsonNode item:source.path(key)) if(item.isObject()) {
            ObjectNode safe=out.addObject();copyText(item,safe,"id");copyText(item,safe,"text");
        }
    }
    private void copyText(JsonNode source,ObjectNode target,String key) {
        if(source.path(key).isTextual()) target.put(key,source.path(key).asText());
    }
    private boolean eligible(UUID examId,UUID student,Instant now,boolean allowLate) {
        String sql="SELECT EXISTS(SELECT 1 FROM assignments a WHERE a.exam_id=? AND a.revoked_at IS NULL " +
            "AND (? OR a.due_at IS NULL OR a.due_at>?) AND (a.student_id=? OR EXISTS " +
            "(SELECT 1 FROM student_enrollments se JOIN classrooms c ON c.id=se.classroom_id " +
            "WHERE se.student_id=? AND se.classroom_id=a.classroom_id AND se.left_at IS NULL AND c.deleted_at IS NULL)))";
        return Boolean.TRUE.equals(db.queryForObject(sql,Boolean.class,examId,allowLate,Timestamp.from(now),student,student));
    }
    private Instant dueAt(UUID examId,UUID student) {
        return db.query("SELECT min(a.due_at) AS due_at FROM assignments a WHERE a.exam_id=? AND a.revoked_at IS NULL " +
            "AND (a.student_id=? OR EXISTS(SELECT 1 FROM student_enrollments se WHERE se.classroom_id=a.classroom_id AND se.student_id=? AND se.left_at IS NULL))",
            (rs,row)->time(rs,"due_at"),examId,student,student).getFirst();
    }
    private Exam exam(UUID id) {
        return db.query("SELECT id,status,starts_at,ends_at,duration_minutes,max_attempts,show_result_immediately,allow_late,total_points FROM exams WHERE id=? AND deleted_at IS NULL",
            (rs,row)->new Exam(rs.getObject("id",UUID.class),rs.getString("status"),time(rs,"starts_at"),time(rs,"ends_at"),
                rs.getObject("duration_minutes",Integer.class),rs.getInt("max_attempts"),rs.getBoolean("show_result_immediately"),
                rs.getBoolean("allow_late"),rs.getBigDecimal("total_points")),id).stream().findFirst().orElseThrow(StudentExamService::notFound);
    }
    private Attempt attempt(UUID id,UUID student,boolean lock) {
        return db.query("SELECT id,exam_id,student_id,status,started_at,deadline_at,submitted_at,submit_key,final_score,published_at FROM exam_attempts WHERE id=? AND student_id=?"+(lock?" FOR UPDATE":""),
            this::mapAttempt,id,student).stream().findFirst().orElseThrow(StudentExamService::notFound);
    }
    private Attempt mapAttempt(ResultSet rs,int row) throws SQLException {
        return new Attempt(rs.getObject("id",UUID.class),rs.getObject("exam_id",UUID.class),rs.getObject("student_id",UUID.class),
            rs.getString("status"),time(rs,"started_at"),time(rs,"deadline_at"),time(rs,"submitted_at"),
            rs.getObject("submit_key",UUID.class),rs.getBigDecimal("final_score"),time(rs,"published_at"));
    }
    private static final String ATTEMPT_SQL="SELECT id,exam_id,student_id,status,started_at,deadline_at,submitted_at,submit_key,final_score,published_at FROM exam_attempts WHERE exam_id=? AND student_id=?";
    private static boolean expired(Attempt a,Instant now) { return a.deadlineAt()!=null && !now.isBefore(a.deadlineAt()); }
    private JsonNode parse(String value) throws SQLException {
        try { return json.readTree(value); } catch(JsonProcessingException e) {throw new SQLException("Invalid stored JSON",e);}
    }
    private static Timestamp stamp(Instant instant) {return instant==null?null:Timestamp.from(instant);}
    private static Instant time(ResultSet rs,String name) throws SQLException {Timestamp t=rs.getTimestamp(name);return t==null?null:t.toInstant();}
    private static ResponseStatusException bad(String s) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
    private static ResponseStatusException conflict(String s) {return new ResponseStatusException(HttpStatus.CONFLICT,s);}
    private static ResponseStatusException forbidden() {return new ResponseStatusException(HttpStatus.FORBIDDEN,"Không có quyền truy cập bài này");}
    private static ResponseStatusException notFound() {return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bài làm");}
}
