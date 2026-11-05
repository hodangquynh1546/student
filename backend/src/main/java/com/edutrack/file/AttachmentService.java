package com.edutrack.file;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttachmentService {
    public record Attachment(UUID id, UUID questionId, String mimeType, long byteSize, Instant createdAt) { }
    public record Content(byte[] bytes, String mimeType) { }
    private record Attempt(String status, Instant deadlineAt) { }
    private record Stored(UUID key, String mimeType, UUID studentId, Instant publishedAt) { }
    private final JdbcTemplate db;
    private final FileStorage files;
    private final long maxBytes;

    public AttachmentService(JdbcTemplate db, FileStorage files,
                             @Value("${edutrack.file.max-size-mb:10}") long maxMb) {
        this.db = db; this.files = files; this.maxBytes = Math.min(maxMb, 10) * 1024 * 1024;
    }

    @Transactional
    public Attachment upload(UUID attemptId, UUID questionId, UUID studentId, MultipartFile upload) {
        if (upload == null || upload.isEmpty() || upload.getSize() > maxBytes) throw bad("Tệp rỗng hoặc quá dung lượng");
        Attempt attempt = db.query("SELECT status,deadline_at FROM exam_attempts WHERE id=? AND student_id=? FOR UPDATE",
            (rs,row) -> new Attempt(rs.getString("status"), timestamp(rs.getTimestamp("deadline_at"))), attemptId, studentId)
            .stream().findFirst().orElseThrow(AttachmentService::notFound);
        if (!"IN_PROGRESS".equals(attempt.status()) || (attempt.deadlineAt() != null && !Instant.now().isBefore(attempt.deadlineAt())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài đã kết thúc");
        Boolean essay = db.queryForObject("SELECT EXISTS(SELECT 1 FROM exam_questions q JOIN exam_attempts a ON a.exam_id=q.exam_id " +
            "WHERE q.id=? AND a.id=? AND q.type='ESSAY')", Boolean.class, questionId, attemptId);
        if (!Boolean.TRUE.equals(essay)) throw bad("Chỉ câu tự luận được đính kèm bài làm");
        Integer count = db.queryForObject("SELECT count(*) FROM answer_attachments WHERE attempt_id=? AND exam_question_id=?",
            Integer.class, attemptId, questionId);
        if (count != null && count >= 5) throw bad("Mỗi câu tự luận tối đa 5 tệp");
        try {
            byte[] bytes = upload.getBytes();
            if (bytes.length == 0 || bytes.length > maxBytes) throw bad("Tệp rỗng hoặc quá dung lượng");
            String mime = detectMime(bytes);
            if (mime == null) throw bad("Chỉ hỗ trợ JPG, PNG, WEBP hoặc PDF");
            UUID key = UUID.randomUUID(), fileId = UUID.randomUUID(), attachmentId = UUID.randomUUID();
            files.put(key, bytes);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) try { files.delete(key); } catch (IOException ignored) { }
                }
            });
            db.update("INSERT INTO file_metadata(id,storage_key,mime_type,byte_size,uploaded_by) VALUES (?,?,?,?,?)",
                fileId,key,mime,bytes.length,studentId);
            db.update("INSERT INTO answer_attachments(id,attempt_id,exam_question_id,file_id) VALUES (?,?,?,?)",
                attachmentId,attemptId,questionId,fileId);
            return new Attachment(fileId,questionId,mime,bytes.length,Instant.now());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không lưu được tệp");
        }
    }

    @Transactional(readOnly = true)
    public List<Attachment> list(UUID attemptId, UUID questionId, UUID viewerId, String role) {
        if (!canView(attemptId, questionId, viewerId, role)) throw notFound();
        return db.query("SELECT f.id,f.mime_type,f.byte_size,f.created_at FROM answer_attachments a " +
            "JOIN file_metadata f ON f.id=a.file_id WHERE a.attempt_id=? AND a.exam_question_id=? ORDER BY a.created_at,a.id",
            (rs,row) -> new Attachment(rs.getObject("id",UUID.class),questionId,rs.getString("mime_type"),
                rs.getLong("byte_size"),timestamp(rs.getTimestamp("created_at"))),attemptId,questionId);
    }

    @Transactional(readOnly = true)
    public Content download(UUID fileId, UUID viewerId, String role) {
        Stored stored = db.query("SELECT f.storage_key,f.mime_type,t.student_id,t.published_at FROM file_metadata f " +
            "JOIN answer_attachments a ON a.file_id=f.id JOIN exam_attempts t ON t.id=a.attempt_id WHERE f.id=?",
            (rs,row) -> new Stored(rs.getObject("storage_key",UUID.class),rs.getString("mime_type"),
                rs.getObject("student_id",UUID.class),timestamp(rs.getTimestamp("published_at"))),fileId)
            .stream().findFirst().orElseThrow(AttachmentService::notFound);
        boolean allowed = "ADMIN_TEACHER".equals(role) ||
            ("STUDENT".equals(role) && viewerId.equals(stored.studentId())) ||
            ("PARENT".equals(role) && stored.publishedAt()!=null && linked(viewerId,stored.studentId()));
        if (!allowed) throw notFound();
        try { return new Content(files.get(stored.key()),stored.mimeType()); }
        catch (IOException e) { throw notFound(); }
    }

    private boolean canView(UUID attemptId, UUID questionId, UUID viewerId, String role) {
        var students = db.query("SELECT student_id,published_at FROM exam_attempts WHERE id=? AND " +
            "EXISTS(SELECT 1 FROM exam_questions q WHERE q.id=? AND q.exam_id=exam_attempts.exam_id)",
            (rs,row) -> new Object[]{rs.getObject("student_id",UUID.class),timestamp(rs.getTimestamp("published_at"))},attemptId,questionId);
        if (students.isEmpty()) return false;
        UUID student=(UUID)students.getFirst()[0];
        return "ADMIN_TEACHER".equals(role) || ("STUDENT".equals(role) && viewerId.equals(student)) ||
            ("PARENT".equals(role) && students.getFirst()[1]!=null && linked(viewerId,student));
    }
    private boolean linked(UUID parent, UUID student) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM parent_student_links l
              JOIN parent_link_requests r ON r.id=l.verified_request_id
              JOIN users p ON p.id=l.parent_id
              WHERE l.parent_id=? AND l.student_id=? AND l.revoked_at IS NULL
                AND r.student_id=l.student_id AND r.status='ACCEPTED'
                AND lower(r.parent_email)=lower(p.email) AND p.role='PARENT' AND p.status='ACTIVE')
            """,Boolean.class,parent,student));
    }
    static String detectMime(byte[] b) {
        if (b.length>=3 && (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255) return "image/jpeg";
        if (b.length>=8 && (b[0]&255)==137 && b[1]==80 && b[2]==78 && b[3]==71 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10) return "image/png";
        if (b.length>=12 && b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F' && b[8]=='W' && b[9]=='E' && b[10]=='B' && b[11]=='P') return "image/webp";
        if (b.length>=5 && b[0]=='%' && b[1]=='P' && b[2]=='D' && b[3]=='F' && b[4]=='-') return "application/pdf";
        return null;
    }
    private static Instant timestamp(Timestamp t) { return t==null?null:t.toInstant(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy tệp"); }
}
