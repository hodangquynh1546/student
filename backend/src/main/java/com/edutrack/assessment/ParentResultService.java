package com.edutrack.assessment;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ParentResultService {
    public record ResultSummary(UUID attemptId, UUID examId, String examTitle, String kind,
                                BigDecimal score, BigDecimal totalPoints, Instant submittedAt, Instant publishedAt) { }
    public record ResultPage(List<ResultSummary> items, long total, int page, int size) { }
    public record QuestionScore(UUID questionId, String type, String body, BigDecimal maxPoints,
                                BigDecimal score, String teacherComment) { }
    public record ResultDetail(ResultSummary result, List<QuestionScore> questions) { }
    private final JdbcTemplate db;

    public ParentResultService(JdbcTemplate db) { this.db = db; }

    @Transactional(readOnly = true)
    public ResultPage list(UUID parentId, UUID studentId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ");
        requireLink(parentId, studentId);
        Long total = db.queryForObject("SELECT count(*) FROM exam_attempts WHERE student_id=? AND published_at IS NOT NULL", Long.class, studentId);
        List<ResultSummary> items = db.query("SELECT a.id AS attempt_id,a.exam_id,e.title,e.kind,a.final_score,e.total_points,a.submitted_at,a.published_at " +
            "FROM exam_attempts a JOIN exams e ON e.id=a.exam_id WHERE a.student_id=? AND a.published_at IS NOT NULL " +
            "ORDER BY a.published_at DESC,a.id LIMIT ? OFFSET ?", (rs, row) -> new ResultSummary(
                rs.getObject("attempt_id", UUID.class), rs.getObject("exam_id", UUID.class), rs.getString("title"),
                rs.getString("kind"), rs.getBigDecimal("final_score"), rs.getBigDecimal("total_points"),
                instant(rs.getTimestamp("submitted_at")), instant(rs.getTimestamp("published_at"))),
            studentId, size, (long) page * size);
        return new ResultPage(items, total == null ? 0 : total, page, size);
    }

    @Transactional(readOnly = true)
    public ResultDetail detail(UUID parentId, UUID studentId, UUID attemptId) {
        requireLink(parentId, studentId);
        ResultSummary summary = db.query("SELECT a.id AS attempt_id,a.exam_id,e.title,e.kind,a.final_score,e.total_points,a.submitted_at,a.published_at " +
            "FROM exam_attempts a JOIN exams e ON e.id=a.exam_id WHERE a.id=? AND a.student_id=? AND a.published_at IS NOT NULL",
            (rs, row) -> new ResultSummary(rs.getObject("attempt_id", UUID.class), rs.getObject("exam_id", UUID.class),
                rs.getString("title"), rs.getString("kind"), rs.getBigDecimal("final_score"), rs.getBigDecimal("total_points"),
                instant(rs.getTimestamp("submitted_at")), instant(rs.getTimestamp("published_at"))), attemptId, studentId)
            .stream().findFirst().orElseThrow(ParentResultService::notFound);
        List<QuestionScore> questions = db.query("SELECT q.id,q.type,q.body,q.points,g.automatic_score,g.teacher_score,g.teacher_comment " +
            "FROM exam_questions q LEFT JOIN grading_results g ON g.exam_question_id=q.id AND g.attempt_id=? " +
            "WHERE q.exam_id=? ORDER BY q.position", (rs, row) -> new QuestionScore(
                rs.getObject("id", UUID.class), rs.getString("type"), rs.getString("body"), rs.getBigDecimal("points"),
                rs.getBigDecimal("teacher_score") == null ? rs.getBigDecimal("automatic_score") : rs.getBigDecimal("teacher_score"),
                rs.getString("teacher_comment")), attemptId, summary.examId());
        return new ResultDetail(summary, questions);
    }

    private void requireLink(UUID parentId, UUID studentId) {
        Boolean allowed = db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM parent_student_links l
              JOIN parent_link_requests r ON r.id=l.verified_request_id
              JOIN users p ON p.id=l.parent_id
              WHERE l.parent_id=? AND l.student_id=? AND l.revoked_at IS NULL
                AND r.student_id=l.student_id AND r.status='ACCEPTED'
                AND lower(r.parent_email)=lower(p.email) AND p.role='PARENT' AND p.status='ACTIVE')
            """, Boolean.class, parentId, studentId);
        if (!Boolean.TRUE.equals(allowed)) throw notFound();
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy kết quả"); }
}
