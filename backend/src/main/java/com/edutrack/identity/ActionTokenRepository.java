package com.edutrack.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ActionTokenRepository extends JpaRepository<ActionToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ActionToken> findByTokenHashAndPurpose(String tokenHash, ActionPurpose purpose);
    @Modifying
    @Query(value="UPDATE action_tokens SET consumed_at=:now WHERE user_id=:userId AND purpose='RESET_PASSWORD' AND consumed_at IS NULL",nativeQuery=true)
    int consumeResetTokens(@Param("userId") UUID userId,@Param("now") Instant now);
}
