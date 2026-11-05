package com.edutrack.notification;

import com.edutrack.identity.UserAccountRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final UserAccountRepository users;

    public NotificationService(NotificationRepository repository, UserAccountRepository users) {
        this.repository = repository;
        this.users = users;
    }

    public record Notice(UUID id, NotificationType type, String title, String body, Instant createdAt, Instant readAt) {
        static Notice of(NotificationRecord record) {
            return new Notice(record.getId(), record.getType(), record.getTitle(), record.getBody(), record.getCreatedAt(), record.getReadAt());
        }
    }

    public record CreateRequest(@NotNull UUID recipientId, @NotNull NotificationType type,
                                @NotBlank @Size(max = 180) String title,
                                @NotBlank @Size(max = 2000) String body) { }

    @Transactional(readOnly = true)
    public Page<Notice> list(UUID recipientId, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang hoặc kích thước trang không hợp lệ");
        return repository.findByRecipientId(recipientId,
            PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
            .map(Notice::of);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return repository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Transactional
    public Notice markRead(UUID recipientId, UUID id) {
        NotificationRecord record = repository.findByIdAndRecipientId(id, recipientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));
        record.markRead(Instant.now());
        return Notice.of(record);
    }

    @Transactional
    public int markAllRead(UUID recipientId) {
        return repository.markAllRead(recipientId, Instant.now());
    }

    @Transactional
    public Notice create(CreateRequest request) {
        if (!users.existsById(request.recipientId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người nhận");
        return Notice.of(repository.save(new NotificationRecord(request.recipientId(), request.type(), request.title(), request.body())));
    }
}
