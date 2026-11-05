package com.edutrack.analytics.ranking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Live ranking based only on results explicitly published by the teacher. */
@Service
public class RankingService {
    public enum Period { ALL, WEEK, MONTH, SEMESTER }
    public record RankingRow(UUID studentId, String studentName, int rank, int assignedCount,
                             int completedCount, BigDecimal averagePercent,
                             BigDecimal completionPercent, BigDecimal progressPercent,
                             BigDecimal rankingScore) { }
    public record TeacherRanking(UUID classroomId, Period period, UUID examId, int cohortSize,
                                 List<RankingRow> rows, String formula) { }
    public record PrivateRanking(UUID classroomId, Period period, UUID examId, int cohortSize,
                                 int rank, int assignedCount, int completedCount,
                                 BigDecimal averagePercent, BigDecimal completionPercent,
                                 BigDecimal progressPercent, BigDecimal rankingScore,
                                 String formula) { }

    private record Learner(UUID id, String name, Instant joinedAt) { }
    private record Exam(UUID id, Instant effectiveAt) { }
    record Result(UUID studentId, UUID examId, Instant publishedAt, BigDecimal percent) { }
    private static final String FORMULA = "70% điểm trung bình đã công bố + 20% tỷ lệ hoàn thành + 10% tiến bộ; "
        + "tiến bộ = 50 + (điểm trung bình nửa sau - nửa đầu)/2, giới hạn 0–100; "
        + "không có bài đã công bố thì tiến bộ = 0, một bài thì = 50. "
        + "Đồng hạng khi tất cả chỉ số bằng nhau; thứ tự ổn định theo mã học sinh.";
    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate namedDb;

    public RankingService(JdbcTemplate db, NamedParameterJdbcTemplate namedDb) {
        this.db = db; this.namedDb = namedDb;
    }

    @Transactional(readOnly = true)
    public TeacherRanking teacher(UUID classroomId, Period period, UUID examId) {
        requireClass(classroomId);
        List<RankingRow> rows = calculate(classroomId, period, examId);
        return new TeacherRanking(classroomId, period, examId, rows.size(), rows, FORMULA);
    }

    @Transactional(readOnly = true)
    public PrivateRanking student(UUID studentId, UUID classroomId, Period period, UUID examId) {
        requireActiveEnrollment(studentId, classroomId);
        return privateResult(studentId, classroomId, period, examId);
    }

    @Transactional(readOnly = true)
    public PrivateRanking parent(UUID parentId, UUID studentId, UUID classroomId,
                                 Period period, UUID examId) {
        Boolean linked = db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM parent_student_links l
              JOIN parent_link_requests r ON r.id=l.verified_request_id
              JOIN users p ON p.id=l.parent_id
              WHERE l.parent_id=? AND l.student_id=? AND l.revoked_at IS NULL
                AND r.student_id=l.student_id AND lower(r.parent_email)=lower(p.email)
                AND r.status='ACCEPTED' AND p.role='PARENT' AND p.status='ACTIVE')
            """, Boolean.class, parentId, studentId);
        if (!Boolean.TRUE.equals(linked)) throw notFound();
        requireActiveEnrollment(studentId, classroomId);
        return privateResult(studentId, classroomId, period, examId);
    }

    private PrivateRanking privateResult(UUID studentId, UUID classroomId, Period period, UUID examId) {
        List<RankingRow> rows = calculate(classroomId, period, examId);
        RankingRow own = rows.stream().filter(row -> row.studentId().equals(studentId))
            .findFirst().orElseThrow(RankingService::notFound);
        return new PrivateRanking(classroomId, period, examId, rows.size(), own.rank(),
            own.assignedCount(), own.completedCount(), own.averagePercent(),
            own.completionPercent(), own.progressPercent(), own.rankingScore(), FORMULA);
    }

    private void requireClass(UUID classroomId) {
        Boolean exists = db.queryForObject("SELECT EXISTS(SELECT 1 FROM classrooms WHERE id=? AND deleted_at IS NULL)",
            Boolean.class, classroomId);
        if (!Boolean.TRUE.equals(exists)) throw notFound();
    }

    private void requireActiveEnrollment(UUID studentId, UUID classroomId) {
        Boolean exists = db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM student_enrollments se
              JOIN classrooms c ON c.id=se.classroom_id
              JOIN users u ON u.id=se.student_id
              WHERE se.student_id=? AND se.classroom_id=? AND se.left_at IS NULL
                AND c.deleted_at IS NULL AND u.role='STUDENT' AND u.status='ACTIVE')
            """, Boolean.class, studentId, classroomId);
        if (!Boolean.TRUE.equals(exists)) throw notFound();
    }

    private List<RankingRow> calculate(UUID classroomId, Period period, UUID examId) {
        List<Learner> learners = db.query("""
            SELECT u.id,u.full_name,se.joined_at FROM student_enrollments se
            JOIN users u ON u.id=se.student_id
            WHERE se.classroom_id=? AND se.left_at IS NULL
              AND u.role='STUDENT' AND u.status='ACTIVE'
            ORDER BY u.id
            """, (rs, row) -> new Learner(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getTimestamp(3).toInstant()), classroomId);
        Instant earliest = switch (period) {
            case ALL -> Instant.EPOCH;
            case WEEK -> Instant.now().minus(7, ChronoUnit.DAYS);
            case MONTH -> Instant.now().minus(30, ChronoUnit.DAYS);
            case SEMESTER -> Instant.now().minus(180, ChronoUnit.DAYS);
        };
        List<Exam> exams = db.query("""
            SELECT e.id,max(coalesce(a.due_at,e.ends_at,e.starts_at,a.created_at)) AS effective_at
            FROM assignments a JOIN exams e ON e.id=a.exam_id
            WHERE a.classroom_id=? AND a.revoked_at IS NULL
              AND e.status IN ('PUBLISHED','CLOSED') AND e.deleted_at IS NULL
              AND e.total_points>0
            GROUP BY e.id
            """, (rs, row) -> new Exam(rs.getObject(1, UUID.class),
                rs.getTimestamp(2).toInstant()), classroomId).stream()
            .filter(exam -> examId == null || exam.id().equals(examId))
            .filter(exam -> !exam.effectiveAt().isBefore(earliest))
            .toList();
        if (examId != null && exams.isEmpty()) throw notFound();
        List<Result> results = List.of();
        if (!learners.isEmpty() && !exams.isEmpty()) {
            Set<UUID> studentIds = learners.stream().map(Learner::id).collect(Collectors.toSet());
            Set<UUID> examIds = exams.stream().map(Exam::id).collect(Collectors.toSet());
            var params = new MapSqlParameterSource("studentIds", studentIds).addValue("examIds", examIds);
            results = namedDb.query("""
                SELECT DISTINCT ON (t.student_id,t.exam_id)
                    t.student_id,t.exam_id,t.published_at,
                    least(100,greatest(0,t.final_score*100/e.total_points)) AS percent
                FROM exam_attempts t JOIN exams e ON e.id=t.exam_id
                WHERE t.student_id IN (:studentIds) AND t.exam_id IN (:examIds)
                  AND t.published_at IS NOT NULL AND t.final_score IS NOT NULL
                  AND t.submitted_at IS NOT NULL AND e.total_points>0
                ORDER BY t.student_id,t.exam_id,t.attempt_number DESC
                """, params, (rs, row) -> new Result(rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant(), rs.getBigDecimal(4)));
        }
        Map<UUID, Map<UUID, Result>> byStudent = new HashMap<>();
        for (Result result : results)
            byStudent.computeIfAbsent(result.studentId(), id -> new HashMap<>())
                .put(result.examId(), result);
        List<RankingRow> rows = new ArrayList<>();
        for (Learner learner : learners) {
            List<Exam> eligible = exams.stream().filter(exam -> !exam.effectiveAt().isBefore(learner.joinedAt())).toList();
            Map<UUID, Result> own = byStudent.getOrDefault(learner.id(), Map.of());
            List<Result> published = eligible.stream().map(exam -> own.get(exam.id()))
                .filter(result -> result != null)
                .sorted(Comparator.comparing(Result::publishedAt).thenComparing(Result::examId)).toList();
            rows.add(score(learner.id(), learner.name(), eligible.size(), published));
        }
        rows.sort(Comparator.comparing(RankingRow::rankingScore).reversed()
            .thenComparing(RankingRow::averagePercent, Comparator.reverseOrder())
            .thenComparing(RankingRow::completionPercent, Comparator.reverseOrder())
            .thenComparing(RankingRow::progressPercent, Comparator.reverseOrder())
            .thenComparing(RankingRow::studentId));
        List<RankingRow> ranked = new ArrayList<>();
        RankingRow prior = null;
        int rank = 0;
        for (RankingRow row : rows) {
            if (prior == null || !sameMetrics(prior, row)) rank = ranked.size() + 1;
            ranked.add(new RankingRow(row.studentId(), row.studentName(), rank,
                row.assignedCount(), row.completedCount(), row.averagePercent(),
                row.completionPercent(), row.progressPercent(), row.rankingScore()));
            prior = row;
        }
        return ranked;
    }

    static RankingRow score(UUID studentId, String name, int assigned, List<Result> published) {
        int completed = published.size();
        BigDecimal average = completed == 0 ? BigDecimal.ZERO : published.stream()
            .map(Result::percent).reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP);
        BigDecimal completion = assigned == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(completed * 100L)
            .divide(BigDecimal.valueOf(assigned), 2, RoundingMode.HALF_UP);
        BigDecimal progress = progress(published);
        BigDecimal score = average.multiply(new BigDecimal("0.70"))
            .add(completion.multiply(new BigDecimal("0.20")))
            .add(progress.multiply(new BigDecimal("0.10")))
            .setScale(2, RoundingMode.HALF_UP);
        return new RankingRow(studentId, name, 0, assigned, completed,
            average, completion, progress, score);
    }

    private static BigDecimal progress(List<Result> published) {
        if (published.isEmpty()) return BigDecimal.ZERO;
        if (published.size() == 1) return new BigDecimal("50.00");
        int split = published.size() / 2;
        BigDecimal before = mean(published.subList(0, split));
        BigDecimal after = mean(published.subList(split, published.size()));
        BigDecimal value = new BigDecimal("50").add(after.subtract(before).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP));
        return value.max(BigDecimal.ZERO).min(new BigDecimal("100"));
    }

    private static BigDecimal mean(List<Result> results) {
        return results.stream().map(Result::percent).reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(results.size()), 2, RoundingMode.HALF_UP);
    }

    private static boolean sameMetrics(RankingRow a, RankingRow b) {
        return a.rankingScore().compareTo(b.rankingScore()) == 0
            && a.averagePercent().compareTo(b.averagePercent()) == 0
            && a.completionPercent().compareTo(b.completionPercent()) == 0
            && a.progressPercent().compareTo(b.progressPercent()) == 0;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bảng xếp hạng");
    }
}
