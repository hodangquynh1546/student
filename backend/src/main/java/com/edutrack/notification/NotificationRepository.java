package com.edutrack.notification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<NotificationRecord, UUID> {
    Page<NotificationRecord> findByRecipientId(UUID recipientId, Pageable pageable);
    Optional<NotificationRecord> findByIdAndRecipientId(UUID id, UUID recipientId);
    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    @Modifying
    @Query("update NotificationRecord n set n.readAt = :at where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("at") Instant at);
}
