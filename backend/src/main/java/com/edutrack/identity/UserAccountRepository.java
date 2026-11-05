package com.edutrack.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByRole(UserRole role);
    List<UserAccount> findByRoleOrderByCreatedAtDesc(UserRole role);
}
