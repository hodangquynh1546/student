package com.edutrack.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "action_tokens")
public class ActionToken {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ActionPurpose purpose;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "context_id") private UUID contextId;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected ActionToken() { }
    public ActionToken(UUID userId, ActionPurpose purpose, String tokenHash, UUID contextId, Instant expiresAt) {
        this.id = UUID.randomUUID(); this.userId = userId; this.purpose = purpose; this.tokenHash = tokenHash;
        this.contextId = contextId; this.expiresAt = expiresAt; this.createdAt = Instant.now();
    }
    public UUID getUserId() { return userId; }
    public ActionPurpose getPurpose() { return purpose; }
    public UUID getContextId() { return contextId; }
    public boolean usable(Instant now) { return consumedAt == null && expiresAt.isAfter(now); }
    public void consume() { if (!usable(Instant.now())) throw new IllegalStateException("Token expired or used"); consumedAt = Instant.now(); }
}
