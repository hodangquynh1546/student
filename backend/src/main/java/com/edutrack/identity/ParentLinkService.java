package com.edutrack.identity;

import com.edutrack.security.TokenCodec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ParentLinkService {
    public record ChildView(UUID id, String fullName, String email) {}
    public record LinkView(UUID id, UUID studentId, String studentName, String parentEmail, String status, Instant expiresAt) {}
    private final UserAccountRepository users;
    private final AuthService auth;
    private final ActionTokenRepository actions;
    private final TokenCodec tokens;
    private final NotificationSender mail;
    private final JdbcTemplate db;
    private final String frontendUrl;

    public ParentLinkService(UserAccountRepository users, AuthService auth, ActionTokenRepository actions,
                             TokenCodec tokens, NotificationSender mail, JdbcTemplate db,
                             @Value("${edutrack.frontend-url}") String frontendUrl) {
        this.users=users; this.auth=auth; this.actions=actions; this.tokens=tokens; this.mail=mail;
        this.db=db; this.frontendUrl=frontendUrl.replaceAll("/+$", "");
    }

    @Transactional
    public LinkView invite(UUID studentId, String parentEmail, UUID adminId) {
        var student=users.findById(studentId).filter(u -> u.getRole()==UserRole.STUDENT)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy học sinh"));
        String email=UserAccount.normalizeEmail(parentEmail);
        var parent=users.findByEmailIgnoreCase(email).orElse(null);
        if (parent!=null && parent.getRole()!=UserRole.PARENT)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email thuộc vai trò khác");
        if (parent!=null && parent.getStatus()==AccountStatus.DISABLED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài khoản phụ huynh đã vô hiệu hóa");
        if (parent==null) {
            String placeholderName=("Phụ huynh của " + student.getFullName());
            parent=users.saveAndFlush(new UserAccount(email, placeholderName.substring(0,Math.min(placeholderName.length(),120)),
                UserRole.PARENT, AccountStatus.PENDING_ACTIVATION, null));
            auth.sendActivation(parent);
        }
        Long linked=db.queryForObject("SELECT count(*) FROM parent_student_links WHERE parent_id=? AND student_id=? AND revoked_at IS NULL",
            Long.class,parent.getId(),studentId);
        if (linked!=null && linked>0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Đã liên kết phụ huynh");
        Long pending=db.queryForObject("SELECT count(*) FROM parent_link_requests WHERE student_id=? AND lower(parent_email)=? AND status='PENDING' AND expires_at>now()",
            Long.class,studentId,email);
        if (pending!=null && pending>0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Đã có lời mời còn hiệu lực");
        UUID requestId=UUID.randomUUID();
        Instant expires=Instant.now().plus(7,ChronoUnit.DAYS);
        db.update("INSERT INTO parent_link_requests(id,student_id,parent_email,requested_by,status,expires_at) VALUES (?,?,?,?,'PENDING',?)",
            requestId,studentId,email,adminId,Timestamp.from(expires));
        String raw=tokens.generate();
        actions.save(new ActionToken(parent.getId(),ActionPurpose.PARENT_LINK,tokens.hash(raw),requestId,expires));
        mail.send(email,"Xác nhận liên kết học sinh EduTrack",
            "Giáo viên mời bạn liên kết với học sinh " + student.getFullName() +
            ". Hãy đăng nhập tài khoản phụ huynh và xác nhận trong 7 ngày: " + frontendUrl + "/parent/link?token=" + raw);
        return new LinkView(requestId,studentId,student.getFullName(),email,"PENDING",expires);
    }

    @Transactional
    public void accept(UUID parentId,String rawToken) {
        if(rawToken==null || rawToken.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thiếu mã xác nhận");
        var parent=users.findById(parentId).filter(u -> u.getRole()==UserRole.PARENT && u.getStatus()==AccountStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        var action=actions.findByTokenHashAndPurpose(tokens.hash(rawToken),ActionPurpose.PARENT_LINK)
            .filter(t -> t.usable(Instant.now()) && t.getUserId().equals(parentId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lời mời không hợp lệ hoặc hết hạn"));
        var requests=db.query("SELECT student_id,parent_email FROM parent_link_requests WHERE id=? AND status='PENDING' AND expires_at>now() FOR UPDATE",
            (rs,row) -> new Object[]{rs.getObject("student_id",UUID.class),rs.getString("parent_email")},action.getContextId());
        if(requests.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lời mời không còn hiệu lực");
        UUID studentId=(UUID)requests.getFirst()[0];
        String email=(String)requests.getFirst()[1];
        if(!parent.getEmail().equalsIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        db.update("INSERT INTO parent_student_links(id,parent_id,student_id,verified_request_id) VALUES (?,?,?,?)",
            UUID.randomUUID(),parentId,studentId,action.getContextId());
        db.update("UPDATE parent_link_requests SET status='ACCEPTED',responded_at=now() WHERE id=?",action.getContextId());
        action.consume();
    }

    @Transactional
    public void revoke(UUID requestId) {
        var links=db.query("SELECT id FROM parent_student_links WHERE verified_request_id=? AND revoked_at IS NULL FOR UPDATE",
            (rs,row)->rs.getObject("id",UUID.class),requestId);
        if(links.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy liên kết đang hoạt động");
        db.update("UPDATE parent_student_links SET revoked_at=now() WHERE id=?",links.getFirst());
        db.update("UPDATE parent_link_requests SET status='REVOKED',responded_at=now() WHERE id=?",requestId);
    }

    @Transactional(readOnly=true)
    public List<ChildView> children(UUID parentId) {
        return db.query("SELECT u.id,u.full_name,u.email FROM users u JOIN parent_student_links l ON l.student_id=u.id WHERE l.parent_id=? AND l.revoked_at IS NULL ORDER BY u.full_name",
            (rs,row)->new ChildView(rs.getObject("id",UUID.class),rs.getString("full_name"),rs.getString("email")),parentId);
    }

    @Transactional(readOnly=true)
    public List<LinkView> links(UUID studentId) {
        return db.query("SELECT r.id,r.student_id,u.full_name,r.parent_email,r.status,r.expires_at FROM parent_link_requests r JOIN users u ON u.id=r.student_id WHERE r.student_id=? ORDER BY r.created_at DESC",
            (rs,row)->new LinkView(rs.getObject("id",UUID.class),rs.getObject("student_id",UUID.class),
                rs.getString("full_name"),rs.getString("parent_email"),rs.getString("status"),rs.getTimestamp("expires_at").toInstant()),studentId);
    }
}
