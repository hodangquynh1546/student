package com.edutrack.identity;

import com.edutrack.security.JwtIssuer;
import com.edutrack.security.TokenCodec;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserAccountRepository users=mock(UserAccountRepository.class);
    private final RefreshSessionRepository sessions=mock(RefreshSessionRepository.class);
    private final ActionTokenRepository actions=mock(ActionTokenRepository.class);
    private final PasswordEncoder passwords=mock(PasswordEncoder.class);
    private final JwtIssuer jwt=mock(JwtIssuer.class);
    private final NotificationSender mail=mock(NotificationSender.class);
    private final TokenCodec tokens=new TokenCodec();
    private AuthService service;

    @BeforeEach void setup() {
        service=new AuthService(users,sessions,actions,passwords,jwt,tokens,mail,7,"http://localhost:5173");
    }

    @Test void lockedAccountCannotLoginEvenWithCorrectPassword() {
        var user=new UserAccount("learner@example.test","Learner",UserRole.STUDENT,AccountStatus.LOCKED,"stored-hash");
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        assertThrows(BadCredentialsException.class,()->service.login(user.getEmail(),"correct","browser"));
        verifyNoInteractions(sessions,jwt);
    }

    @Test void replayOfRotatedRefreshTokenRevokesWholeFamily() {
        UUID userId=UUID.randomUUID(), familyId=UUID.randomUUID();
        String raw="already-rotated-token";
        var old=new RefreshSession(userId,familyId,tokens.hash(raw),Instant.now().plus(1,ChronoUnit.DAYS),"browser");
        old.rotate(UUID.randomUUID());
        when(sessions.findByTokenHash(tokens.hash(raw))).thenReturn(Optional.of(old));

        assertThrows(AuthService.InvalidRefreshException.class,()->service.refresh(raw,"browser"));
        verify(sessions).revokeFamily(eq(familyId),any(Instant.class));
        verifyNoInteractions(users,jwt);
    }

    @Test void refreshRejectsLockedAccountAndRevokesFamily() {
        UUID userId=UUID.randomUUID(), familyId=UUID.randomUUID();
        String raw="valid-refresh-token";
        var session=new RefreshSession(userId,familyId,tokens.hash(raw),Instant.now().plus(1,ChronoUnit.DAYS),"browser");
        var locked=new UserAccount("learner@example.test","Learner",UserRole.STUDENT,AccountStatus.LOCKED,"hash");
        when(sessions.findByTokenHash(tokens.hash(raw))).thenReturn(Optional.of(session));
        when(users.findById(userId)).thenReturn(Optional.of(locked));

        assertThrows(AuthService.InvalidRefreshException.class,()->service.refresh(raw,"browser"));
        verify(sessions).revokeFamily(eq(familyId),any(Instant.class));
        verifyNoInteractions(jwt);
    }

    @Test void profileUpdateOnlyChangesOwnProfileFields() {
        var user = new UserAccount("learner@example.test", "Learner", UserRole.STUDENT, AccountStatus.ACTIVE, "hash");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        var birthDate = LocalDate.of(2012, 4, 3);

        var result = service.updateProfile(user.getId(), " New Name ", " 0123 456 789 ", birthDate);

        assertEquals(user.getId(), result.id());
        assertEquals("New Name", result.fullName());
        assertEquals("0123 456 789", result.phone());
        assertEquals(birthDate, result.dateOfBirth());
        assertEquals("learner@example.test", result.email());
        assertEquals(UserRole.STUDENT, result.role());
    }

    @Test void profileUpdateRejectsInactiveAccountAndFutureBirthDate() {
        var locked = new UserAccount("learner@example.test", "Learner", UserRole.STUDENT, AccountStatus.LOCKED, "hash");
        when(users.findById(locked.getId())).thenReturn(Optional.of(locked));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.updateProfile(locked.getId(), "New Name", null, null));
        locked.setStatus(AccountStatus.ACTIVE);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.updateProfile(locked.getId(), "New Name", null, LocalDate.now().plusDays(1)));
    }
}
