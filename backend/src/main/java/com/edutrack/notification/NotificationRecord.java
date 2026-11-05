package com.edutrack.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationRecord {
    @Id private UUID id;
    @Column(name = "recipient_id", nullable = false) private UUID recipientId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private NotificationType type;
    @Column(nullable = false, length = 180) private String title;
    @Column(nullable = false, length = 2000) private String body;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "read_at") private Instant readAt;

    protected NotificationRecord() { }

    public NotificationRecord(UUID recipientId, NotificationType type, String title, String body) {
        this.id = UUID.randomUUID();
        this.recipientId = recipientId;
        this.type = type;
        this.title = title.trim();
        this.body = body.trim();
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRecipientId() { return recipientId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public void markRead(Instant at) { if (readAt == null) readAt = at; }
}
