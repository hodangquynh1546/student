package com.edutrack.notification;

import com.edutrack.identity.NotificationSender;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class EmailOutboxWorkerTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final NotificationSender sender = mock(NotificationSender.class);

    @Test
    void disabledSmtpLeavesEventsPending() {
        new EmailOutboxWorker(db, sender, false).deliverPending();
        verifyNoInteractions(db, sender);
    }

    @Test
    void scanWithNoDueEventsDoesNotSendMail() {
        when(db.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class),
            anyInt(), anyInt())).thenReturn(List.of());
        new EmailOutboxWorker(db, sender, true).deliverPending();
        verifyNoInteractions(sender);
    }

    @Test
    void retryDelayIsBounded() {
        assertEquals(Duration.ofMinutes(1), EmailOutboxWorker.retryDelay(1));
        assertEquals(Duration.ofMinutes(2), EmailOutboxWorker.retryDelay(2));
        assertEquals(Duration.ofMinutes(16), EmailOutboxWorker.retryDelay(5));
        assertEquals(Duration.ofMinutes(16), EmailOutboxWorker.retryDelay(100));
    }
}
