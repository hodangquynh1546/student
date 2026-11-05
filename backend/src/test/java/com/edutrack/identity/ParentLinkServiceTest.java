package com.edutrack.identity;

import com.edutrack.security.TokenCodec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ParentLinkServiceTest {
    private final UserAccountRepository users=mock(UserAccountRepository.class);
    private final AuthService auth=mock(AuthService.class);
    private final ActionTokenRepository actions=mock(ActionTokenRepository.class);
    private final TokenCodec tokens=new TokenCodec();
    private final NotificationSender mail=mock(NotificationSender.class);
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final ParentLinkService service=new ParentLinkService(users,auth,actions,tokens,mail,db,"http://localhost:5173");

    @Test void parentCannotAcceptInviteIssuedToAnotherParent() {
        var current=new UserAccount("current@example.test","Current",UserRole.PARENT,AccountStatus.ACTIVE,"hash");
        var invited=new UserAccount("invited@example.test","Invited",UserRole.PARENT,AccountStatus.ACTIVE,"hash");
        String raw="secret-link";
        var action=new ActionToken(invited.getId(),ActionPurpose.PARENT_LINK,tokens.hash(raw),UUID.randomUUID(),
            Instant.now().plus(1,ChronoUnit.DAYS));
        when(users.findById(current.getId())).thenReturn(Optional.of(current));
        when(actions.findByTokenHashAndPurpose(tokens.hash(raw),ActionPurpose.PARENT_LINK)).thenReturn(Optional.of(action));

        var error=assertThrows(ResponseStatusException.class,()->service.accept(current.getId(),raw));
        assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
        verifyNoInteractions(db);
    }

    @Test void unverifiedParentCannotAcceptInvite() {
        var pending=new UserAccount("pending@example.test","Pending",UserRole.PARENT,AccountStatus.PENDING_ACTIVATION,null);
        when(users.findById(pending.getId())).thenReturn(Optional.of(pending));

        var error=assertThrows(ResponseStatusException.class,()->service.accept(pending.getId(),"secret-link"));
        assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode());
        verifyNoInteractions(actions,db);
    }
}
