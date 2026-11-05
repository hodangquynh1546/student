package com.edutrack.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);
    private final JavaMailSender mail;
    private final Environment environment;
    private final boolean enabled;
    private final String from;
    public NotificationSender(JavaMailSender mail, Environment environment,
                              @Value("${edutrack.mail.enabled}") boolean enabled,
                              @Value("${edutrack.mail.from}") String from) {
        this.mail = mail; this.environment = environment; this.enabled = enabled; this.from = from;
    }
    public void send(String to, String subject, String body) {
        if (!enabled) {
            if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
                log.info("Development mail to {}: {} — {}", to, subject, body);
                return;
            }
            throw new IllegalStateException("SMTP is not configured");
        }
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(to); message.setSubject(subject); message.setText(body);
        mail.send(message);
    }
}
