package com.edutrack.notification;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Persists domain notifications in the caller's database transaction. */
@Component
public class NotificationEventWriter {
    private final JdbcTemplate db;

    public NotificationEventWriter(JdbcTemplate db) { this.db = db; }

    public void assignmentCreated(UUID assignmentId, String examTitle) {
        // Resolve recipients from the stored assignment, so a caller cannot choose arbitrary users.
        db.update("WITH inserted AS (INSERT INTO notifications(id,recipient_id,type,title,body) " +
            "SELECT gen_random_uuid(),recipient_id,'ASSIGNMENT',?,? FROM (" +
            "SELECT a.student_id AS recipient_id FROM assignments a JOIN users u ON u.id=a.student_id " +
            "WHERE a.id=? AND a.revoked_at IS NULL AND a.student_id IS NOT NULL AND u.role='STUDENT' AND u.status='ACTIVE' " +
            "UNION SELECT se.student_id AS recipient_id FROM assignments a " +
            "JOIN student_enrollments se ON se.classroom_id=a.classroom_id AND se.left_at IS NULL " +
            "JOIN users u ON u.id=se.student_id AND u.role='STUDENT' AND u.status='ACTIVE' " +
            "JOIN classrooms c ON c.id=a.classroom_id AND c.deleted_at IS NULL " +
            "WHERE a.id=? AND a.revoked_at IS NULL AND a.classroom_id IS NOT NULL) recipients RETURNING id) " +
            "INSERT INTO email_logs(id,notification_id,source_kind,source_id) " +
            "SELECT gen_random_uuid(),id,'ASSIGNMENT',? FROM inserted",
            "Bài học mới được giao", "Có bài mới: " + limited(examTitle) + ".", assignmentId, assignmentId, assignmentId);
    }

    public void resultPublished(UUID attemptId) {
        // The parent query uses only verified, non-revoked links; notification text contains no score.
        db.update("WITH inserted AS (INSERT INTO notifications(id,recipient_id,type,title,body) " +
            "SELECT gen_random_uuid(),recipient_id,'RESULT',?,? FROM (" +
            "SELECT a.student_id AS recipient_id FROM exam_attempts a JOIN users u ON u.id=a.student_id " +
            "WHERE a.id=? AND a.published_at IS NOT NULL AND u.role='STUDENT' AND u.status='ACTIVE' " +
            "UNION SELECT l.parent_id AS recipient_id FROM exam_attempts a " +
            "JOIN parent_student_links l ON l.student_id=a.student_id AND l.revoked_at IS NULL " +
            "JOIN parent_link_requests r ON r.id=l.verified_request_id AND r.status='ACCEPTED' " +
            "AND r.student_id=l.student_id " +
            "JOIN users u ON u.id=l.parent_id AND u.role='PARENT' AND u.status='ACTIVE' " +
            "WHERE a.id=? AND a.published_at IS NOT NULL AND lower(r.parent_email)=lower(u.email)) recipients RETURNING id) " +
            "INSERT INTO email_logs(id,notification_id,source_kind,source_id) " +
            "SELECT gen_random_uuid(),id,'RESULT',? FROM inserted",
            "Kết quả học tập đã công bố", "Có kết quả học tập mới. Mở trang kết quả để xem chi tiết.",
            attemptId, attemptId, attemptId);
    }

    private static String limited(String title) {
        String value=title==null?"bài học":title.trim();
        return value.length()>180?value.substring(0,180):value;
    }
}
