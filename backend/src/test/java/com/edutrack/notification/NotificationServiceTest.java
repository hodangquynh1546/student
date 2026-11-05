package com.edutrack.notification;

import com.edutrack.identity.UserAccountRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final NotificationService service = new NotificationService(repository, users);

    @Test
    void anotherUsersNotificationIsNeverReturnedOrMarkedRead() {
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        NotificationRecord notice = new NotificationRecord(owner, NotificationType.RESULT, "Điểm mới", "Kết quả đã công bố");
        when(repository.findByIdAndRecipientId(notice.getId(), other)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.markRead(other, notice.getId()));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertNull(notice.getReadAt());
        verify(repository, never()).findById(any());
    }

    @Test
    void markReadIsIdempotentAndUsesRecipientScope() {
        UUID recipient = UUID.randomUUID();
        NotificationRecord notice = new NotificationRecord(recipient, NotificationType.ASSIGNMENT, "Bài mới", "Có bài mới cho bạn");
        when(repository.findByIdAndRecipientId(notice.getId(), recipient)).thenReturn(Optional.of(notice));

        Instant first = service.markRead(recipient, notice.getId()).readAt();
        Instant second = service.markRead(recipient, notice.getId()).readAt();
        assertNotNull(first);
        assertEquals(first, second);
    }

    @Test
    void markAllReadIsScopedToAuthenticatedRecipient() {
        UUID recipient = UUID.randomUUID();
        when(repository.markAllRead(eq(recipient), any(Instant.class))).thenReturn(3);
        assertEquals(3, service.markAllRead(recipient));
        verify(repository).markAllRead(eq(recipient), any(Instant.class));
    }

    @Test
    void createRequiresExistingRecipient() {
        UUID missing = UUID.randomUUID();
        NotificationService.CreateRequest request = new NotificationService.CreateRequest(missing, NotificationType.GENERAL, "Thông báo", "Nội dung");
        when(users.existsById(missing)).thenReturn(false);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.create(request));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(repository, never()).save(any());
    }
}
