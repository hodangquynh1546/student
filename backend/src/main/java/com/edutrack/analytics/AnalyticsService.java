package com.edutrack.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalyticsService {
    public record ScorePoint(UUID attemptId, UUID examId, String examTitle, Instant publishedAt,
                             BigDecimal score, BigDecimal totalPoints, BigDecimal percentage) {}
    public record TopicPerformance(String topic, int questionCount, BigDecimal earnedPoints,
                                   BigDecimal possiblePoints, BigDecimal percentage) {}
    public record LearnerDashboard(UUID studentId, long assignedExams, long completedExams,
                                   long pendingExams, BigDecimal completionRate, long publishedResults,
                                   BigDecimal averagePercentage, BigDecimal highestPercentage,
                                   BigDecimal lowestPercentage, List<ScorePoint> scoreTrend,
                                   List<TopicPerformance> topics) {}
    public record AdminDashboard(long totalStudents, long totalClassrooms, long totalExams,
                                 long publishedExams, long pendingGradingAttempts,
                                 long upcomingExams, long assignedStudentExamPairs,
                                 long completedStudentExamPairs, BigDecimal completionRate,
                                 BigDecimal averagePublishedPercentage) {}

    private final JdbcTemplate db;
    public AnalyticsService(JdbcTemplate db) { this.db = db; }

    @Transactional(readOnly = true)
    public LearnerDashboard student(UUID studentId) {
        return learner(studentId, true);
    }

    @Transactional(readOnly = true)
    public LearnerDashboard parent(UUID parentId, UUID studentId) {
        Boolean linked = db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM parent_student_links l
              JOIN parent_link_requests r ON r.id=l.verified_request_id
              JOIN users p ON p.id=l.parent_id
              JOIN users s ON s.id=l.student_id
              WHERE l.parent_id=? AND l.student_id=? AND l.revoked_at IS NULL
                AND r.status='ACCEPTED' AND p.role='PARENT' AND p.status='ACTIVE'
                AND s.role='STUDENT')
            """, Boolean.class, parentId, studentId);
        if (!Boolean.TRUE.equals(linked))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy học sinh đã liên kết");
        // Parent metrics never include an unsubmitted or unpublished attempt.
        return learner(studentId, false);
    }

    private LearnerDashboard learner(UUID studentId, boolean includeAssignmentProgress) {
        long assigned = includeAssignmentProgress ? count("""
            SELECT count(DISTINCT e.id) FROM exams e JOIN assignments a ON a.exam_id=e.id
            WHERE e.status='PUBLISHED' AND e.deleted_at IS NULL AND a.revoked_at IS NULL
              AND (a.student_id=? OR EXISTS(SELECT 1 FROM student_enrollments se
                JOIN classrooms c ON c.id=se.classroom_id
                WHERE se.student_id=? AND se.classroom_id=a.classroom_id
                  AND se.left_at IS NULL AND c.deleted_at IS NULL))
            """, studentId, studentId) : 0;
        long completed = includeAssignmentProgress ? count("""
            SELECT count(DISTINCT e.id) FROM exams e JOIN assignments a ON a.exam_id=e.id
            WHERE e.status='PUBLISHED' AND e.deleted_at IS NULL AND a.revoked_at IS NULL
              AND (a.student_id=? OR EXISTS(SELECT 1 FROM student_enrollments se
                JOIN classrooms c ON c.id=se.classroom_id
                WHERE se.student_id=? AND se.classroom_id=a.classroom_id
                  AND se.left_at IS NULL AND c.deleted_at IS NULL))
              AND EXISTS(SELECT 1 FROM exam_attempts t WHERE t.exam_id=e.id
                AND t.student_id=? AND t.submitted_at IS NOT NULL)
            """, studentId, studentId, studentId) : 0;
        List<ScorePoint> trend = db.query("""
            SELECT a.id,a.exam_id,e.title,a.published_at,a.final_score,e.total_points
            FROM exam_attempts a JOIN exams e ON e.id=a.exam_id
            WHERE a.student_id=? AND a.published_at IS NOT NULL
              AND a.final_score IS NOT NULL AND e.deleted_at IS NULL
            ORDER BY a.published_at DESC,a.id DESC LIMIT 30
            """, (rs, row) -> new ScorePoint(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getBigDecimal(5),
                rs.getBigDecimal(6), percent(rs.getBigDecimal(5), rs.getBigDecimal(6))), studentId);
        List<BigDecimal> percentages = db.query("""
            SELECT a.final_score,e.total_points FROM exam_attempts a JOIN exams e ON e.id=a.exam_id
            WHERE a.student_id=? AND a.published_at IS NOT NULL
              AND a.final_score IS NOT NULL AND e.total_points>0 AND e.deleted_at IS NULL
            """, (rs, row) -> percent(rs.getBigDecimal(1), rs.getBigDecimal(2)), studentId);
        BigDecimal avg = percentages.isEmpty() ? null : percentages.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(percentages.size()), 2, RoundingMode.HALF_UP);
        BigDecimal high = percentages.stream().max(BigDecimal::compareTo).orElse(null);
        BigDecimal low = percentages.stream().min(BigDecimal::compareTo).orElse(null);
        List<TopicPerformance> topics = db.query("""
            SELECT q.topic,count(*)::integer,
                   coalesce(sum(coalesce(g.teacher_score,g.automatic_score)),0),
                   coalesce(sum(eq.points),0)
            FROM exam_attempts a JOIN grading_results g ON g.attempt_id=a.id
            JOIN exam_questions eq ON eq.id=g.exam_question_id
            JOIN questions q ON q.id=eq.source_question_id
            WHERE a.student_id=? AND a.published_at IS NOT NULL
              AND a.final_score IS NOT NULL
            GROUP BY q.topic ORDER BY q.topic LIMIT 100
            """, (rs, row) -> topic(rs), studentId);
        return new LearnerDashboard(studentId, assigned, completed,
            Math.max(0, assigned - completed), includeAssignmentProgress ? percent(BigDecimal.valueOf(completed),
                BigDecimal.valueOf(assigned)) : null, percentages.size(), avg, high, low, trend, topics);
    }

    @Transactional(readOnly = true)
    public AdminDashboard admin() {
        long students = count("SELECT count(*) FROM users WHERE role='STUDENT' AND status<>'DISABLED'");
        long classes = count("SELECT count(*) FROM classrooms WHERE deleted_at IS NULL");
        long exams = count("SELECT count(*) FROM exams WHERE deleted_at IS NULL");
        long publishedExams = count("SELECT count(*) FROM exams WHERE status='PUBLISHED' AND deleted_at IS NULL");
        long pending = count("SELECT count(*) FROM exam_attempts WHERE status='PENDING_GRADING'");
        long upcoming = count("""
            SELECT count(*) FROM exams WHERE status='PUBLISHED' AND deleted_at IS NULL
              AND starts_at>now() AND starts_at<=now()+interval '7 days'
            """);
        // Distinct student/exam pairs avoid counting overlapping class and direct assignments twice.
        long assignedPairs = count("""
            SELECT count(*) FROM (
              SELECT DISTINCT a.exam_id,u.id AS student_id FROM assignments a
              JOIN exams e ON e.id=a.exam_id AND e.status='PUBLISHED' AND e.deleted_at IS NULL
              JOIN users u ON u.role='STUDENT' AND u.status='ACTIVE'
              WHERE a.revoked_at IS NULL AND (a.student_id=u.id OR EXISTS(
                SELECT 1 FROM student_enrollments se JOIN classrooms c ON c.id=se.classroom_id
                WHERE se.student_id=u.id AND se.classroom_id=a.classroom_id
                  AND se.left_at IS NULL AND c.deleted_at IS NULL))
            ) assigned
            """);
        long completedPairs = count("""
            SELECT count(*) FROM (
              SELECT DISTINCT a.exam_id,u.id AS student_id FROM assignments a
              JOIN exams e ON e.id=a.exam_id AND e.status='PUBLISHED' AND e.deleted_at IS NULL
              JOIN users u ON u.role='STUDENT' AND u.status='ACTIVE'
              WHERE a.revoked_at IS NULL AND (a.student_id=u.id OR EXISTS(
                SELECT 1 FROM student_enrollments se JOIN classrooms c ON c.id=se.classroom_id
                WHERE se.student_id=u.id AND se.classroom_id=a.classroom_id
                  AND se.left_at IS NULL AND c.deleted_at IS NULL))
                AND EXISTS(SELECT 1 FROM exam_attempts t WHERE t.exam_id=a.exam_id
                  AND t.student_id=u.id AND t.submitted_at IS NOT NULL)
            ) completed
            """);
        List<BigDecimal> percentages = db.query("""
            SELECT a.final_score,e.total_points FROM exam_attempts a JOIN exams e ON e.id=a.exam_id
            WHERE a.published_at IS NOT NULL AND a.final_score IS NOT NULL
              AND e.total_points>0 AND e.deleted_at IS NULL
            """, (rs, row) -> percent(rs.getBigDecimal(1),rs.getBigDecimal(2)));
        BigDecimal average = percentages.isEmpty() ? null : percentages.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(percentages.size()), 2, RoundingMode.HALF_UP);
        return new AdminDashboard(students, classes, exams, publishedExams, pending, upcoming,
            assignedPairs, completedPairs, percent(BigDecimal.valueOf(completedPairs),
                BigDecimal.valueOf(assignedPairs)), average);
    }

    private long count(String sql, Object... args) {
        Long value = db.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
    private static TopicPerformance topic(ResultSet rs) throws SQLException {
        BigDecimal earned=rs.getBigDecimal(3), possible=rs.getBigDecimal(4);
        return new TopicPerformance(rs.getString(1),rs.getInt(2),earned,possible,percent(earned,possible));
    }
    static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (numerator==null || denominator==null || denominator.signum()<=0) return null;
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator,2,RoundingMode.HALF_UP);
    }
}
