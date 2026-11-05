package com.edutrack.assessment;

import com.edutrack.question.QuestionService;
import com.edutrack.notification.NotificationEventWriter;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExamAdminService {
    public enum Kind { EXAM, HOMEWORK, PRACTICE, PERIODIC, REVIEW, MOCK }
    public record Input(String title,String description,Kind kind,Instant startsAt,Instant endsAt,Integer durationMinutes,
                        Integer maxAttempts,Boolean showResultImmediately,Boolean showAnswersAfterSubmit,
                        Boolean shuffleQuestions,Boolean shuffleOptions,Boolean allowDraft,Boolean allowLate) {}
    public record ExamView(UUID id,String title,String description,Kind kind,String status,Instant startsAt,Instant endsAt,
                           Integer durationMinutes,int maxAttempts,boolean showResultImmediately,boolean showAnswersAfterSubmit,
                           boolean shuffleQuestions,boolean shuffleOptions,boolean allowDraft,boolean allowLate,
                           BigDecimal totalPoints,long questionCount,long version) {}
    public record QuestionView(UUID id,UUID sourceQuestionId,int position,String type,String body,
                               String content,String answerKey,String explanation,String rubric,BigDecimal points) {}
    public record Detail(ExamView exam,List<QuestionView> questions) {}
    public record AddQuestion(UUID questionId,BigDecimal points,Integer position) {}
    public record InlineQuestion(QuestionService.Input question,BigDecimal points,Integer position) {}
    public record QuestionOrder(List<UUID> questionIds) {}
    public record PointsInput(BigDecimal points) {}
    public record AssignmentInput(UUID classroomId,UUID studentId,Instant dueAt) {}
    public record AssignmentView(UUID id,UUID examId,UUID classroomId,UUID studentId,Instant dueAt) {}

    private final JdbcTemplate db;
    private final QuestionService questions;
    private final NotificationEventWriter notifications;
    public ExamAdminService(JdbcTemplate db,QuestionService questions,NotificationEventWriter notifications) {
        this.db=db;this.questions=questions;this.notifications=notifications;
    }

    @Transactional
    public ExamView create(Input input,UUID actor) {
        validate(input);
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO exams(id,title,description,kind,status,starts_at,ends_at,duration_minutes,max_attempts,show_result_immediately,show_answers_after_submit,shuffle_questions,shuffle_options,allow_draft,allow_late,created_by) VALUES (?,?,?,?,'DRAFT',?,?,?,?,?,?,?,?,?,?,?)",
            id,input.title().trim(),input.description()==null?"":input.description().trim(),input.kind().name(),time(input.startsAt()),time(input.endsAt()),
            input.durationMinutes(),input.maxAttempts()==null?1:input.maxAttempts(),bool(input.showResultImmediately()),
            bool(input.showAnswersAfterSubmit()),bool(input.shuffleQuestions()),bool(input.shuffleOptions()),
            input.allowDraft()==null || input.allowDraft(),bool(input.allowLate()),actor);
        return get(id);
    }

    @Transactional
    public ExamView update(UUID id,Input input,long expectedVersion) {
        validate(input);
        requireDraft(id);
        lockDraft(id);
        int changed=db.update("UPDATE exams SET title=?,description=?,kind=?,starts_at=?,ends_at=?,duration_minutes=?,max_attempts=?,show_result_immediately=?,show_answers_after_submit=?,shuffle_questions=?,shuffle_options=?,allow_draft=?,allow_late=?,updated_at=now(),version=version+1 WHERE id=? AND version=? AND status='DRAFT' AND deleted_at IS NULL",
            input.title().trim(),input.description()==null?"":input.description().trim(),input.kind().name(),time(input.startsAt()),time(input.endsAt()),
            input.durationMinutes(),input.maxAttempts()==null?1:input.maxAttempts(),bool(input.showResultImmediately()),
            bool(input.showAnswersAfterSubmit()),bool(input.shuffleQuestions()),bool(input.shuffleOptions()),
            input.allowDraft()==null || input.allowDraft(),bool(input.allowLate()),id,expectedVersion);
        if(changed==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đề đã thay đổi; tải lại trước khi lưu");
        return get(id);
    }

    @Transactional(readOnly=true)
    public ExamView get(UUID id) {
        return db.query(SELECT+" WHERE e.id=? AND e.deleted_at IS NULL",this::map,id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đề"));
    }
    @Transactional(readOnly=true)
    public List<ExamView> list() {
        return db.query(SELECT+" WHERE e.deleted_at IS NULL ORDER BY e.created_at DESC",this::map);
    }
    @Transactional(readOnly=true)
    public Detail detail(UUID id) {
        ExamView exam=get(id);
        var items=db.query("SELECT id,source_question_id,position,type,body,content,answer_key,explanation,rubric,points FROM exam_questions WHERE exam_id=? ORDER BY position",
            (rs,row)->new QuestionView(rs.getObject("id",UUID.class),rs.getObject("source_question_id",UUID.class),
                rs.getInt("position"),rs.getString("type"),rs.getString("body"),rs.getString("content"),
                rs.getString("answer_key"),rs.getString("explanation"),rs.getString("rubric"),rs.getBigDecimal("points")),id);
        return new Detail(exam,items);
    }

    @Transactional
    public Detail addQuestion(UUID examId,AddQuestion input) {
        requireDraft(examId);
        if(input==null || input.questionId()==null) throw bad("Thiếu câu hỏi");
        lockDraft(examId);
        var source=questions.get(input.questionId());
        BigDecimal points=input.points()==null?source.defaultPoints():input.points();
        if(points.compareTo(BigDecimal.ZERO)<=0 || points.compareTo(new BigDecimal("100"))>0) throw bad("Điểm câu hỏi không hợp lệ");
        int position=input.position()==null?nextPosition(examId):input.position();
        if(position<0 || position>nextPosition(examId)) throw bad("Vị trí câu hỏi không hợp lệ");
        if(position<nextPosition(examId))
            db.update("UPDATE exam_questions SET position=position+100000 WHERE exam_id=? AND position>=?",examId,position);
        db.update("INSERT INTO exam_questions(id,exam_id,source_question_id,position,type,body,content,answer_key,explanation,rubric,points) VALUES (?,?,?,?,?,?,cast(? as jsonb),cast(? as jsonb),?,?,?)",
            UUID.randomUUID(),examId,source.id(),position,source.type().name(),source.body(),source.content().toString(),
            source.answerKey().toString(),source.explanation(),source.rubric(),points);
        if(position<nextPosition(examId)-1)
            db.update("UPDATE exam_questions SET position=position-99999 WHERE exam_id=? AND position>=100000",examId);
        db.update("UPDATE exams SET total_points=(SELECT coalesce(sum(points),0) FROM exam_questions WHERE exam_id=?),updated_at=now(),version=version+1 WHERE id=?",examId,examId);
        return detail(examId);
    }

    @Transactional
    public Detail addInlineQuestion(UUID examId,InlineQuestion input,UUID actor) {
        requireDraft(examId);
        if(input==null || input.question()==null) throw bad("Thiếu câu hỏi");
        lockDraft(examId);
        var source=questions.create(input.question(),actor);
        return addQuestion(examId,new AddQuestion(source.id(),input.points(),input.position()));
    }

    @Transactional
    public Detail reorderQuestions(UUID examId,QuestionOrder input) {
        requireDraft(examId);
        if(input==null || input.questionIds()==null) throw bad("Thiếu thứ tự câu hỏi");
        lockDraft(examId);
        List<UUID> existing=questionIds(examId);
        List<UUID> requested=input.questionIds();
        if(requested.size()!=existing.size() || requested.stream().anyMatch(id->id==null) ||
            new HashSet<>(requested).size()!=requested.size() || !new HashSet<>(requested).equals(new HashSet<>(existing)))
            throw bad("Danh sách câu hỏi phải gồm đúng các câu hiện có");
        rewritePositions(examId,requested);
        touch(examId);
        return detail(examId);
    }

    @Transactional
    public Detail changePoints(UUID examId,UUID questionId,PointsInput input) {
        requireDraft(examId);
        if(input==null || !validPoints(input.points())) throw bad("Điểm câu hỏi không hợp lệ");
        lockDraft(examId);
        if(db.update("UPDATE exam_questions SET points=? WHERE exam_id=? AND id=?",input.points(),examId,questionId)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy câu hỏi trong đề");
        recalculate(examId);
        return detail(examId);
    }

    @Transactional
    public Detail removeQuestion(UUID examId,UUID questionId) {
        requireDraft(examId);
        lockDraft(examId);
        if(db.update("DELETE FROM exam_questions WHERE exam_id=? AND id=?",examId,questionId)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy câu hỏi trong đề");
        rewritePositions(examId,questionIds(examId));
        recalculate(examId);
        return detail(examId);
    }

    @Transactional
    public ExamView publish(UUID id) {
        var exam=requireDraft(id);
        if(exam.questionCount()<1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đề chưa có câu hỏi");
        lockDraft(id);
        Long currentCount=db.queryForObject("SELECT count(*) FROM exam_questions WHERE exam_id=?",Long.class,id);
        if(currentCount==null || currentCount<1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đề chưa có câu hỏi");
        db.update("UPDATE exams SET status='PUBLISHED',updated_at=now(),version=version+1 WHERE id=?",id);
        return get(id);
    }

    @Transactional
    public ExamView close(UUID id) {
        var exam=get(id);
        if(!"PUBLISHED".equals(exam.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đề chưa xuất bản");
        db.update("UPDATE exams SET status='CLOSED',updated_at=now(),version=version+1 WHERE id=?",id);
        return get(id);
    }

    @Transactional
    public AssignmentView assign(UUID examId,AssignmentInput input,UUID actor) {
        var exam=get(examId);
        if(!"PUBLISHED".equals(exam.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đề chưa xuất bản");
        if(input==null || (input.classroomId()==null)==(input.studentId()==null)) throw bad("Chọn đúng một lớp hoặc học sinh");
        if(input.classroomId()!=null) {
            Long count=db.queryForObject("SELECT count(*) FROM classrooms WHERE id=? AND deleted_at IS NULL",Long.class,input.classroomId());
            if(count==null || count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy lớp");
        } else {
            Long count=db.queryForObject("SELECT count(*) FROM users WHERE id=? AND role='STUDENT'",Long.class,input.studentId());
            if(count==null || count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy học sinh");
        }
        UUID id=UUID.randomUUID();
        try {
            db.update("INSERT INTO assignments(id,exam_id,classroom_id,student_id,due_at,created_by) VALUES (?,?,?,?,?,?)",
                id,examId,input.classroomId(),input.studentId(),time(input.dueAt()),actor);
        } catch(DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Đề đã được giao cho đối tượng này"); }
        notifications.assignmentCreated(id,exam.title());
        return new AssignmentView(id,examId,input.classroomId(),input.studentId(),input.dueAt());
    }

    private ExamView requireDraft(UUID id) {
        var exam=get(id);
        if(!"DRAFT".equals(exam.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ sửa được đề nháp");
        return exam;
    }
    private void lockDraft(UUID id) {
        String status=db.queryForObject("SELECT status FROM exams WHERE id=? AND deleted_at IS NULL FOR UPDATE",String.class,id);
        if(!"DRAFT".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ sửa được đề nháp");
    }
    private List<UUID> questionIds(UUID id) {
        return db.query("SELECT id FROM exam_questions WHERE exam_id=? ORDER BY position",(rs,row)->rs.getObject("id",UUID.class),id);
    }
    private void rewritePositions(UUID examId,List<UUID> ids) {
        // Two phases avoid the immediate UNIQUE(exam_id,position) constraint during swaps.
        Integer max=db.queryForObject("SELECT coalesce(max(position),-1) FROM exam_questions WHERE exam_id=?",Integer.class,examId);
        int offset=(max==null?0:max+1)+ids.size();
        db.update("UPDATE exam_questions SET position=position+? WHERE exam_id=?",offset,examId);
        for(int i=0;i<ids.size();i++)
            db.update("UPDATE exam_questions SET position=? WHERE exam_id=? AND id=?",i,examId,ids.get(i));
    }
    private void touch(UUID examId) {
        db.update("UPDATE exams SET updated_at=now(),version=version+1 WHERE id=?",examId);
    }
    private void recalculate(UUID examId) {
        db.update("UPDATE exams SET total_points=(SELECT coalesce(sum(points),0) FROM exam_questions WHERE exam_id=?),updated_at=now(),version=version+1 WHERE id=?",examId,examId);
    }
    private static boolean validPoints(BigDecimal points) {
        return points!=null && points.compareTo(BigDecimal.ZERO)>0 && points.compareTo(new BigDecimal("100"))<=0;
    }
    private int nextPosition(UUID id) {
        Integer value=db.queryForObject("SELECT coalesce(max(position)+1,0) FROM exam_questions WHERE exam_id=?",Integer.class,id);
        return value==null?0:value;
    }
    private void validate(Input input) {
        if(input==null || input.title()==null || input.title().isBlank() || input.title().length()>200 || input.kind()==null ||
            (input.description()!=null && input.description().length()>20000) ||
            (input.startsAt()!=null && input.endsAt()!=null && input.endsAt().isBefore(input.startsAt())) ||
            (input.durationMinutes()!=null && (input.durationMinutes()<1 || input.durationMinutes()>480)) ||
            (input.maxAttempts()!=null && (input.maxAttempts()<1 || input.maxAttempts()>20))) throw bad("Cấu hình đề không hợp lệ");
    }
    private static boolean bool(Boolean value) { return Boolean.TRUE.equals(value); }
    private static Timestamp time(Instant value) { return value==null?null:Timestamp.from(value); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static final String SELECT="SELECT e.id,e.title,e.description,e.kind,e.status,e.starts_at,e.ends_at,e.duration_minutes,e.max_attempts,e.show_result_immediately,e.show_answers_after_submit,e.shuffle_questions,e.shuffle_options,e.allow_draft,e.allow_late,e.total_points,e.version,(SELECT count(*) FROM exam_questions q WHERE q.exam_id=e.id) AS question_count FROM exams e";
    private ExamView map(ResultSet rs,int row) throws SQLException {
        return new ExamView(rs.getObject("id",UUID.class),rs.getString("title"),rs.getString("description"),Kind.valueOf(rs.getString("kind")),
            rs.getString("status"),timestamp(rs,"starts_at"),timestamp(rs,"ends_at"),rs.getObject("duration_minutes",Integer.class),
            rs.getInt("max_attempts"),rs.getBoolean("show_result_immediately"),rs.getBoolean("show_answers_after_submit"),
            rs.getBoolean("shuffle_questions"),rs.getBoolean("shuffle_options"),rs.getBoolean("allow_draft"),rs.getBoolean("allow_late"),
            rs.getBigDecimal("total_points"),rs.getLong("question_count"),rs.getLong("version"));
    }
    private static Instant timestamp(ResultSet rs,String column) throws SQLException {
        Timestamp value=rs.getTimestamp(column);return value==null?null:value.toInstant();
    }
}
