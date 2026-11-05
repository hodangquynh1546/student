package com.edutrack.notification;

import com.edutrack.identity.NotificationSender;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Delivers committed notification events. A crash after SMTP accepts a message can cause a retry;
 * delivery is therefore at least once, while each event is queued once. */
@Component
public class EmailOutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxWorker.class);
    private static final int MAX_ATTEMPTS = 5;
    // SMTP connect/read/write timeouts are each 10s by default. Five deliveries fit inside a 5m lease.
    private static final int BATCH_SIZE = 5;
    private final JdbcTemplate db;
    private final NotificationSender sender;
    private final boolean smtpEnabled;

    public EmailOutboxWorker(JdbcTemplate db, NotificationSender sender,
                             @Value("${edutrack.mail.enabled:false}") boolean smtpEnabled) {
        this.db = db;
        this.sender = sender;
        this.smtpEnabled = smtpEnabled;
    }

    @Scheduled(fixedDelayString = "${edutrack.mail.outbox-scan-ms:30000}")
    public void deliverPending() {
        // Development without SMTP keeps the outbox intact; no email is falsely marked sent.
        if (!smtpEnabled) return;
        db.update("UPDATE email_logs SET status='FAILED', lease_until=NULL, updated_at=now() " +
            "WHERE status='PROCESSING' AND lease_until < now() AND attempts >= ?", MAX_ATTEMPTS);
        List<UUID> claimed = db.query("WITH due AS (SELECT id FROM email_logs " +
                "WHERE ((status='PENDING' AND next_attempt_at <= now()) OR " +
                "(status='PROCESSING' AND lease_until < now())) AND attempts < ? " +
                "ORDER BY next_attempt_at,id LIMIT ? FOR UPDATE SKIP LOCKED) " +
                "UPDATE email_logs e SET status='PROCESSING',attempts=e.attempts+1," +
                "lease_until=now()+interval '5 minutes',updated_at=now() " +
                "FROM due WHERE e.id=due.id RETURNING e.id",
            (rs, row) -> rs.getObject(1, UUID.class), MAX_ATTEMPTS, BATCH_SIZE);
        for (UUID id : claimed) deliver(id);
    }

    private void deliver(UUID id) {
        List<Delivery> rows = db.query("SELECT e.id,e.source_kind,e.source_id,e.attempts," +
                "n.recipient_id,n.title,n.body,u.email FROM email_logs e " +
                "JOIN notifications n ON n.id=e.notification_id JOIN users u ON u.id=n.recipient_id " +
                "WHERE e.id=? AND e.status='PROCESSING'",
            (rs, row) -> new Delivery(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getObject(3, UUID.class), rs.getInt(4), rs.getObject(5, UUID.class),
                rs.getString(6), rs.getString(7), rs.getString(8)), id);
        if (rows.isEmpty()) return;
        Delivery item = rows.getFirst();
        if (!eligible(item)) {
            db.update("UPDATE email_logs SET status='CANCELLED',lease_until=NULL,updated_at=now() " +
                "WHERE id=? AND status='PROCESSING'", id);
            return;
        }
        try {
            sender.send(item.email(), item.title(), item.body());
            db.update("UPDATE email_logs SET status='SENT',sent_at=now(),lease_until=NULL," +
                "last_error=NULL,updated_at=now() WHERE id=? AND status='PROCESSING'", id);
        } catch (RuntimeException failure) {
            boolean exhausted = item.attempts() >= MAX_ATTEMPTS;
            Duration delay = retryDelay(item.attempts());
            db.update("UPDATE email_logs SET status=?,lease_until=NULL,next_attempt_at=?," +
                "last_error=?,updated_at=now() WHERE id=? AND status='PROCESSING'",
                exhausted ? "FAILED" : "PENDING", Instant.now().plus(delay),
                failure.getClass().getSimpleName(), id);
            log.warn("Email delivery failed for event {} (attempt {}/{})", id, item.attempts(), MAX_ATTEMPTS);
        }
    }

    static Duration retryDelay(int attempt) {
        return Duration.ofMinutes(1L << Math.min(Math.max(attempt - 1, 0), 4));
    }

    private boolean eligible(Delivery item) {
        String source = item.sourceKind();
        if ("ASSIGNMENT".equals(source)) {
            return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM assignments a " +
                "JOIN users u ON u.id=? AND u.role='STUDENT' AND u.status='ACTIVE' " +
                "LEFT JOIN classrooms c ON c.id=a.classroom_id AND c.deleted_at IS NULL " +
                "LEFT JOIN student_enrollments se ON se.classroom_id=c.id AND se.student_id=u.id " +
                "AND se.left_at IS NULL WHERE a.id=? AND a.revoked_at IS NULL " +
                "AND (a.student_id=u.id OR se.id IS NOT NULL))",
                Boolean.class, item.recipientId(), item.sourceId()));
        }
        if ("RESULT".equals(source)) {
            return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM exam_attempts a " +
                "JOIN users u ON u.id=? AND u.status='ACTIVE' " +
                "LEFT JOIN parent_student_links l ON l.student_id=a.student_id AND l.parent_id=u.id " +
                "AND l.revoked_at IS NULL " +
                "LEFT JOIN parent_link_requests r ON r.id=l.verified_request_id " +
                "AND r.status='ACCEPTED' AND r.student_id=a.student_id " +
                "AND lower(r.parent_email)=lower(u.email) " +
                "WHERE a.id=? AND a.published_at IS NOT NULL AND " +
                "((u.role='STUDENT' AND a.student_id=u.id) OR " +
                "(u.role='PARENT' AND r.id IS NOT NULL)))",
                Boolean.class, item.recipientId(), item.sourceId()));
        }
        return false;
    }

    private record Delivery(UUID id, String sourceKind, UUID sourceId, int attempts,
                            UUID recipientId, String title, String body, String email) { }
}
