package com.edutrack.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "family_id", nullable = false) private UUID familyId;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "replaced_by") private UUID replacedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "user_agent", length = 500) private String userAgent;
    protected RefreshSession() { }
    public RefreshSession(UUID userId, UUID familyId, String tokenHash, Instant expiresAt, String userAgent) {
        this.id = UUID.randomUUID(); this.userId = userId; this.familyId = familyId; this.tokenHash = tokenHash;
        this.expiresAt = expiresAt; this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 500));
        this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean active(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void rotate(UUID replacementId) { revokedAt = Instant.now(); lastUsedAt = revokedAt; replacedBy = replacementId; }
    public void revoke() { revokedAt = Instant.now(); }
}
