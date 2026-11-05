package com.edutrack.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ActionTokenRepository extends JpaRepository<ActionToken, UUID> {
    Optional<ActionToken> findByTokenHashAndPurpose(String tokenHash, ActionPurpose purpose);
}
