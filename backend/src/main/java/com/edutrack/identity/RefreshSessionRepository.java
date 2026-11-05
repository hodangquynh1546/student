package com.edutrack.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshSession> findByTokenHash(String tokenHash);
    @Modifying
    @Query("update RefreshSession s set s.revokedAt = :now where s.familyId = :familyId and s.revokedAt is null")
    int revokeFamily(UUID familyId, Instant now);
    @Modifying
    @Query("update RefreshSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(UUID userId, Instant now);
}
