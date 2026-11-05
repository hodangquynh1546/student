package com.edutrack.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final Environment environment;
    private final String email;
    private final String password;
    private final String name;
    public AdminBootstrap(UserAccountRepository users, PasswordEncoder passwords, Environment environment,
                          @Value("${ADMIN_EMAIL:}") String email, @Value("${ADMIN_PASSWORD:}") String password,
                          @Value("${ADMIN_NAME:}") String name) {
        this.users = users; this.passwords = passwords; this.environment = environment;
        this.email = email; this.password = password; this.name = name;
    }
    @Override @Transactional
    public void run(ApplicationArguments arguments) {
        if (users.existsByRole(UserRole.ADMIN_TEACHER)) return;
        if (email.isBlank() || password.isBlank() || name.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("dev", "test"))) return;
            throw new IllegalStateException("ADMIN_EMAIL, ADMIN_PASSWORD and ADMIN_NAME are required for initial setup");
        }
        if (!email.contains("@") || name.length() > 120 || password.length() < 16)
            throw new IllegalStateException("Initial admin settings are invalid");
        if (users.findByEmailIgnoreCase(UserAccount.normalizeEmail(email)).isPresent())
            throw new IllegalStateException("Initial admin email is already used by another role");
        users.save(new UserAccount(email, name, UserRole.ADMIN_TEACHER, AccountStatus.ACTIVE, passwords.encode(password)));
    }
}
