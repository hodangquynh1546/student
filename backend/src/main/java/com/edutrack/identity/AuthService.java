package com.edutrack.identity;

import com.edutrack.security.JwtIssuer;
import com.edutrack.security.TokenCodec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class AuthService {
    public record UserView(UUID id, String email, String fullName, UserRole role, AccountStatus status) {
        static UserView of(UserAccount user) { return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getStatus()); }
    }
    public record IssuedSession(String accessToken, int expiresIn, String refreshToken, UserView user) { }
    public static class InvalidRefreshException extends RuntimeException { }
    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final ActionTokenRepository actions;
    private final PasswordEncoder passwords;
    private final JwtIssuer jwt;
    private final TokenCodec tokens;
    private final NotificationSender mail;
    private final int refreshDays;
    private final String frontendUrl;

    public AuthService(UserAccountRepository users, RefreshSessionRepository sessions,
                       ActionTokenRepository actions, PasswordEncoder passwords, JwtIssuer jwt,
                       TokenCodec tokens, NotificationSender mail,
                       @Value("${edutrack.refresh-days}") int refreshDays,
                       @Value("${edutrack.frontend-url}") String frontendUrl) {
        this.users = users; this.sessions = sessions; this.actions = actions; this.passwords = passwords;
        this.jwt = jwt; this.tokens = tokens; this.mail = mail; this.refreshDays = refreshDays;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        if (refreshDays < 1 || refreshDays > 90) throw new IllegalArgumentException("Refresh lifetime must be 1-90 days");
    }

    @Transactional
    public IssuedSession login(String email, String password, String userAgent) {
        var user = users.findByEmailIgnoreCase(UserAccount.normalizeEmail(email)).orElse(null);
        if (user == null || user.getStatus() != AccountStatus.ACTIVE || user.getPasswordHash() == null ||
            !passwords.matches(password, user.getPasswordHash())) throw new BadCredentialsException("Invalid credentials");
        return issue(user, UUID.randomUUID(), userAgent);
    }

    @Transactional(noRollbackFor = InvalidRefreshException.class)
    public IssuedSession refresh(String rawToken, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) throw new InvalidRefreshException();
        var session = sessions.findByTokenHash(tokens.hash(rawToken)).orElseThrow(InvalidRefreshException::new);
        if (!session.active(Instant.now())) {
            sessions.revokeFamily(session.getFamilyId(), Instant.now());
            throw new InvalidRefreshException();
        }
        var user = users.findById(session.getUserId()).orElseThrow(InvalidRefreshException::new);
        if (user.getStatus() != AccountStatus.ACTIVE) {
            sessions.revokeFamily(session.getFamilyId(), Instant.now());
            throw new InvalidRefreshException();
        }
        var replacement = issue(user, session.getFamilyId(), userAgent);
        var replacementSession = sessions.findByTokenHash(tokens.hash(replacement.refreshToken())).orElseThrow();
        session.rotate(replacementSession.getId());
        return replacement;
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        sessions.findByTokenHash(tokens.hash(rawToken)).ifPresent(RefreshSession::revoke);
    }

    @Transactional(readOnly = true)
    public UserView me(UUID id) {
        return users.findById(id).filter(u -> u.getStatus() == AccountStatus.ACTIVE)
            .map(UserView::of).orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
    }

    @Transactional
    public void requestReset(String email) {
        var found = users.findByEmailIgnoreCase(UserAccount.normalizeEmail(email));
        if (found.isEmpty() || found.get().getStatus() != AccountStatus.ACTIVE) return;
        var raw = tokens.generate();
        actions.save(new ActionToken(found.get().getId(), ActionPurpose.RESET_PASSWORD, tokens.hash(raw), null,
            Instant.now().plus(30, ChronoUnit.MINUTES)));
        mail.send(found.get().getEmail(), "Đặt lại mật khẩu EduTrack",
            "Mở liên kết để đặt lại mật khẩu (hết hạn sau 30 phút): " + frontendUrl + "/reset-password?token=" + raw);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        validatePassword(newPassword);
        var token = actions.findByTokenHashAndPurpose(tokens.hash(rawToken), ActionPurpose.RESET_PASSWORD)
            .filter(t -> t.usable(Instant.now())).orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Liên kết không hợp lệ hoặc đã hết hạn"));
        var user = users.findById(token.getUserId()).orElseThrow();
        user.changePassword(passwords.encode(newPassword)); token.consume();
        sessions.revokeAllForUser(user.getId(), Instant.now());
    }

    @Transactional
    public void activate(String rawToken, String newPassword) {
        validatePassword(newPassword);
        var token = actions.findByTokenHashAndPurpose(tokens.hash(rawToken), ActionPurpose.ACTIVATE)
            .filter(t -> t.usable(Instant.now())).orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Liên kết không hợp lệ hoặc đã hết hạn"));
        var user = users.findById(token.getUserId()).orElseThrow();
        if (user.getStatus() != AccountStatus.PENDING_ACTIVATION)
            throw new ResponseStatusException(CONFLICT, "Tài khoản không chờ kích hoạt");
        user.activate(passwords.encode(newPassword)); token.consume();
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        validatePassword(newPassword);
        var user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (!passwords.matches(currentPassword, user.getPasswordHash()))
            throw new ResponseStatusException(BAD_REQUEST, "Mật khẩu hiện tại không đúng");
        user.changePassword(passwords.encode(newPassword));
        sessions.revokeAllForUser(userId, Instant.now());
    }

    @Transactional
    public void sendActivation(UserAccount user) {
        var raw = tokens.generate();
        actions.save(new ActionToken(user.getId(), ActionPurpose.ACTIVATE, tokens.hash(raw), null,
            Instant.now().plus(48, ChronoUnit.HOURS)));
        mail.send(user.getEmail(), "Kích hoạt tài khoản EduTrack",
            "Mở liên kết để đặt mật khẩu (hết hạn sau 48 giờ): " + frontendUrl + "/activate?token=" + raw);
    }

    private IssuedSession issue(UserAccount user, UUID familyId, String userAgent) {
        var raw = tokens.generate();
        sessions.saveAndFlush(new RefreshSession(user.getId(), familyId, tokens.hash(raw),
            Instant.now().plus(refreshDays, ChronoUnit.DAYS), userAgent));
        return new IssuedSession(jwt.issue(user), jwt.accessSeconds(), raw, UserView.of(user));
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(BAD_REQUEST, "Mật khẩu cần 12–72 byte");
    }
}
