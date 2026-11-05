package com.edutrack.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id private UUID id;
    @Column(nullable = false, length = 254) private String email;
    @Column(name = "full_name", nullable = false, length = 120) private String fullName;
    @Column(length = 30) private String phone;
    @Column(name = "date_of_birth") private LocalDate dateOfBirth;
    @Column(name = "avatar_url", length = 2048) private String avatarUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private UserRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AccountStatus status;
    @Column(name = "password_hash", length = 255) private String passwordHash;
    @Column(name = "email_verified_at") private Instant emailVerifiedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected UserAccount() { }
    public UserAccount(String email, String fullName, UserRole role, AccountStatus status, String passwordHash) {
        this.id = UUID.randomUUID(); this.email = normalizeEmail(email); this.fullName = fullName.trim();
        this.role = role; this.status = status; this.passwordHash = passwordHash;
        this.createdAt = Instant.now(); this.updatedAt = createdAt;
        if (status == AccountStatus.ACTIVE) this.emailVerifiedAt = createdAt;
    }
    public static String normalizeEmail(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getAvatarUrl() { return avatarUrl; }
    public UserRole getRole() { return role; }
    public AccountStatus getStatus() { return status; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public void activate(String encodedPassword) { this.passwordHash = encodedPassword; this.status = AccountStatus.ACTIVE; this.emailVerifiedAt = Instant.now(); touch(); }
    public void changePassword(String encodedPassword) { this.passwordHash = encodedPassword; touch(); }
    public void setStatus(AccountStatus value) { this.status = value; touch(); }
    public void setProfile(String fullName, String phone, LocalDate birthDate, String avatarUrl) {
        this.fullName = fullName.trim(); this.phone = phone; this.dateOfBirth = birthDate; this.avatarUrl = avatarUrl; touch();
    }
    private void touch() { this.updatedAt = Instant.now(); }
}
