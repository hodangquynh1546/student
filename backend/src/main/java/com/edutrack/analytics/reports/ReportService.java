package com.edutrack.analytics.reports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {
    public enum Period { WEEK, MONTH }
    public record Topic(String name, int questionCount, BigDecimal earnedPoints,
                        BigDecimal possiblePoints, BigDecimal percentage) {}
    public record Report(UUID id, UUID studentId, Period period, LocalDate start, LocalDate end,
                         Integer assignedCount, Integer completedCount, BigDecimal completionRate,
                         int publishedResultCount, BigDecimal averagePercent, BigDecimal progressPoints,
                         List<Topic> topics, Instant generatedAt) {}

    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    public ReportService(JdbcTemplate db, ObjectMapper mapper) { this.db = db; this.mapper = mapper; }

    @Transactional
    public Report generate(UUID studentId, Period period, LocalDate start) {
        if (start == null || period == null || !start.equals(normalize(period, start)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu kỳ báo cáo không hợp lệ");
        LocalDate end = end(period, start);
        if (end.isAfter(LocalDate.now(java.time.Clock.systemUTC())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ tổng hợp kỳ đã kết thúc");
        Boolean student = db.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND role='STUDENT')",
            Boolean.class, studentId);
        if (!Boolean.TRUE.equals(student)) throw notFound();

        int assigned = count("""
            SELECT count(DISTINCT a.exam_id) FROM assignments a JOIN exams e ON e.id=a.exam_id
            WHERE e.status IN ('PUBLISHED','CLOSED') AND e.deleted_at IS NULL
              AND a.revoked_at IS NULL AND a.due_at >= ? AND a.due_at < ?
              AND (a.student_id=? OR EXISTS(SELECT 1 FROM student_enrollments se
                WHERE se.classroom_id=a.classroom_id AND se.student_id=?
                  AND se.joined_at<=a.created_at AND (se.left_at IS NULL OR se.left_at>=a.created_at)))
            """, Date.valueOf(start), Date.valueOf(end), studentId, studentId);
        int completed = count("""
            SELECT count(DISTINCT a.exam_id) FROM assignments a JOIN exams e ON e.id=a.exam_id
            WHERE e.status IN ('PUBLISHED','CLOSED') AND e.deleted_at IS NULL
              AND a.revoked_at IS NULL AND a.due_at >= ? AND a.due_at < ?
              AND (a.student_id=? OR EXISTS(SELECT 1 FROM student_enrollments se
                WHERE se.classroom_id=a.classroom_id AND se.student_id=?
                  AND se.joined_at<=a.created_at AND (se.left_at IS NULL OR se.left_at>=a.created_at)))
              AND EXISTS(SELECT 1 FROM exam_attempts t WHERE t.exam_id=a.exam_id
                AND t.student_id=? AND t.submitted_at IS NOT NULL)
            """, Date.valueOf(start), Date.valueOf(end), studentId, studentId, studentId);
        List<BigDecimal> scores = scores(studentId, start, end);
        BigDecimal average = average(scores);
        LocalDate previous = period == Period.WEEK ? start.minusWeeks(1) : start.minusMonths(1);
        BigDecimal previousAverage = average(scores(studentId, previous, start));
        BigDecimal progress = average == null || previousAverage == null ? null : average.subtract(previousAverage);
        List<Topic> topics = topics(studentId, start, end);
        String topicJson;
        try { topicJson = mapper.writeValueAsString(topics); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Không mã hóa được báo cáo", ex); }
        UUID id = UUID.randomUUID();
        db.update("""
            INSERT INTO report_snapshots (id,student_id,period_type,period_start,period_end,
              assigned_count,completed_count,published_result_count,average_percent,progress_points,topics)
            VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb)
            ON CONFLICT (student_id,period_type,period_start) DO UPDATE SET
              period_end=EXCLUDED.period_end,assigned_count=EXCLUDED.assigned_count,
              completed_count=EXCLUDED.completed_count,published_result_count=EXCLUDED.published_result_count,
              average_percent=EXCLUDED.average_percent,progress_points=EXCLUDED.progress_points,
              topics=EXCLUDED.topics,updated_at=now(),generated_at=now()
            """, id, studentId, period.name(), Date.valueOf(start), Date.valueOf(end), assigned,
            completed, scores.size(), average, progress, topicJson);
        return one(studentId, period, start);
    }

    @Transactional(readOnly = true)
    public List<Report> adminList(UUID studentId) { return list(studentId); }

    @Transactional(readOnly = true)
    public List<Report> studentList(UUID studentId) { return list(studentId); }

    @Transactional(readOnly = true)
    public List<Report> parentList(UUID parentId, UUID studentId) {
        requireParentLink(parentId, studentId);
        return list(studentId);
    }

    @Transactional(readOnly = true)
    public Report adminReport(UUID studentId, UUID reportId) { return byId(studentId, reportId); }

    @Transactional(readOnly = true)
    public Report studentReport(UUID studentId, UUID reportId) { return byId(studentId, reportId); }

    @Transactional(readOnly = true)
    public Report parentReport(UUID parentId, UUID studentId, UUID reportId) {
        requireParentLink(parentId, studentId);
        return byId(studentId, reportId);
    }

    private void requireParentLink(UUID parentId, UUID studentId) {
        Boolean linked = db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM parent_student_links l
              JOIN parent_link_requests r ON r.id=l.verified_request_id
              JOIN users p ON p.id=l.parent_id
              WHERE l.parent_id=? AND l.student_id=? AND l.revoked_at IS NULL
                AND r.status='ACCEPTED' AND p.role='PARENT' AND p.status='ACTIVE')
            """, Boolean.class, parentId, studentId);
        if (!Boolean.TRUE.equals(linked)) throw notFound();
    }

    private Report byId(UUID studentId, UUID reportId) {
        return db.query("SELECT * FROM report_snapshots WHERE id=? AND student_id=?",
            (rs, row) -> map(rs), reportId, studentId).stream().findFirst()
            .orElseThrow(ReportService::notFound);
    }

    private List<Report> list(UUID studentId) {
        return db.query("""
            SELECT * FROM report_snapshots WHERE student_id=?
            ORDER BY period_start DESC,period_type LIMIT 100
            """, (rs, row) -> map(rs), studentId);
    }

    private Report one(UUID studentId, Period period, LocalDate start) {
        return db.query("""
            SELECT * FROM report_snapshots WHERE student_id=? AND period_type=? AND period_start=?
            """, (rs, row) -> map(rs), studentId, period.name(), Date.valueOf(start))
            .stream().findFirst().orElseThrow(ReportService::notFound);
    }

    private Report map(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<Topic> topics;
        try { topics = mapper.readValue(rs.getString("topics"), new TypeReference<List<Topic>>() {}); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Báo cáo lưu không hợp lệ", ex); }
        int assigned = rs.getInt("assigned_count"), completed = rs.getInt("completed_count");
        Timestamp generated = rs.getTimestamp("generated_at");
        return new Report(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
            Period.valueOf(rs.getString("period_type")), rs.getDate("period_start").toLocalDate(),
            rs.getDate("period_end").toLocalDate(), assigned,
            completed, percent(completed, assigned),
            rs.getInt("published_result_count"), rs.getBigDecimal("average_percent"),
            rs.getBigDecimal("progress_points"), topics, generated.toInstant());
    }

    private List<BigDecimal> scores(UUID studentId, LocalDate start, LocalDate end) {
        return db.query("""
            SELECT DISTINCT ON (a.exam_id) a.final_score,e.total_points
            FROM exam_attempts a JOIN exams e ON e.id=a.exam_id
            WHERE a.student_id=? AND a.submitted_at>=? AND a.submitted_at<?
              AND a.published_at IS NOT NULL AND a.final_score IS NOT NULL
              AND e.total_points>0 AND e.deleted_at IS NULL
            ORDER BY a.exam_id,a.final_score DESC,a.published_at DESC
            """, (rs, row) -> rs.getBigDecimal(1).multiply(BigDecimal.valueOf(100))
                .divide(rs.getBigDecimal(2), 2, RoundingMode.HALF_UP),
            studentId, Date.valueOf(start), Date.valueOf(end));
    }

    private List<Topic> topics(UUID studentId, LocalDate start, LocalDate end) {
        return db.query("""
            SELECT coalesce(q.topic,'Chưa phân loại'),count(*)::integer,
              coalesce(sum(coalesce(g.teacher_score,g.automatic_score)),0),coalesce(sum(eq.points),0)
            FROM exam_attempts a JOIN grading_results g ON g.attempt_id=a.id
              JOIN exam_questions eq ON eq.id=g.exam_question_id
              LEFT JOIN questions q ON q.id=eq.source_question_id
            WHERE a.student_id=? AND a.submitted_at>=? AND a.submitted_at<?
              AND a.published_at IS NOT NULL AND a.final_score IS NOT NULL
            GROUP BY coalesce(q.topic,'Chưa phân loại') ORDER BY 1 LIMIT 100
            """, (rs, row) -> new Topic(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3),
                rs.getBigDecimal(4), percent(rs.getBigDecimal(3), rs.getBigDecimal(4))),
            studentId, Date.valueOf(start), Date.valueOf(end));
    }

    private int count(String sql, Object... args) {
        Integer count = db.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
    static BigDecimal average(List<BigDecimal> values) {
        return values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
    static BigDecimal percent(int numerator, int denominator) {
        return denominator == 0 ? null : BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
    static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        return numerator == null || denominator == null || denominator.signum() <= 0 ? null
            : numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }
    static LocalDate normalize(Period period, LocalDate date) {
        return period == Period.WEEK ? date.with(java.time.DayOfWeek.MONDAY) : date.withDayOfMonth(1);
    }
    static LocalDate end(Period period, LocalDate start) {
        return period == Period.WEEK ? start.plusWeeks(1) : start.plusMonths(1);
    }
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy báo cáo");
    }
}
