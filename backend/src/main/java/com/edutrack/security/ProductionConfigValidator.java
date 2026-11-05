package com.edutrack.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionConfigValidator implements ApplicationRunner {
    private final Environment environment;
    private final String secret;
    private final String databasePassword;
    private final String smtpHost;
    private final String smtpUser;
    private final String smtpPassword;
    private final String mailFrom;
    private final String frontendUrl;
    private final String corsOrigin;
    private final String adminPassword;
    private final boolean smtpEnabled;

    public ProductionConfigValidator(Environment environment,
        @Value("${JWT_SECRET:}") String secret,
        @Value("${DATABASE_PASSWORD:}") String databasePassword,
        @Value("${SMTP_HOST:}") String smtpHost,
        @Value("${SMTP_USER:}") String smtpUser,
        @Value("${SMTP_PASSWORD:}") String smtpPassword,
        @Value("${MAIL_FROM:}") String mailFrom,
        @Value("${FRONTEND_URL:}") String frontendUrl,
        @Value("${CORS_ORIGIN:}") String corsOrigin,
        @Value("${ADMIN_PASSWORD:}") String adminPassword,
        @Value("${SMTP_ENABLED:false}") boolean smtpEnabled) {
        this.environment=environment;this.secret=secret;this.databasePassword=databasePassword;
        this.smtpHost=smtpHost;this.smtpUser=smtpUser;this.smtpPassword=smtpPassword;
        this.mailFrom=mailFrom;this.frontendUrl=frontendUrl;this.corsOrigin=corsOrigin;
        this.adminPassword=adminPassword;this.smtpEnabled=smtpEnabled;
    }

    @Override public void run(ApplicationArguments args) {
        if(!environment.acceptsProfiles(Profiles.of("prod"))) return;
        if(secret.getBytes(StandardCharsets.UTF_8).length<32 || placeholder(secret)) fail("JWT_SECRET");
        if(databasePassword.isBlank() || placeholder(databasePassword)) fail("DATABASE_PASSWORD");
        if(!smtpEnabled || smtpHost.isBlank() || smtpUser.isBlank() || smtpPassword.isBlank() ||
            mailFrom.isBlank() || placeholder(smtpPassword) || List.of("localhost","127.0.0.1","mailpit").contains(smtpHost)) fail("SMTP");
        if(!frontendUrl.startsWith("https://") || frontendUrl.contains("localhost") ||
            corsOrigin.isBlank() || !List.of(corsOrigin.split(",")).stream().allMatch(x -> x.trim().startsWith("https://")))
            fail("FRONTEND_URL/CORS_ORIGIN");
        if(!adminPassword.isBlank() && (adminPassword.length()<16 || placeholder(adminPassword))) fail("ADMIN_PASSWORD");
    }
    private boolean placeholder(String value) {
        String lower=value.toLowerCase();
        return lower.contains("change_me") || lower.contains("replace-with") || lower.contains("example") ||
            lower.contains("password123") || lower.chars().distinct().count()<8;
    }
    private void fail(String setting) { throw new IllegalStateException("Unsafe production configuration: "+setting); }
}
