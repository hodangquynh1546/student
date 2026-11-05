package com.edutrack.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

@Service
public class NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE);
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
        try {
            var message = mail.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from); helper.setTo(to); helper.setSubject(subject);
            helper.setText(body, html(subject, body));
            mail.send(message);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException("Không tạo được email", e);
        }
    }

    static String html(String subject, String body) {
        String safeSubject = escape(subject);
        String safeBody = escape(body).replace("\n", "<br>");
        var match = URL.matcher(body);
        String action = "";
        if (match.find()) {
            String link = escape(match.group());
            action = "<p style='margin:28px 0'><a href='" + link + "' style='background:#2563eb;color:#ffffff;"
                + "text-decoration:none;padding:12px 20px;border-radius:8px;display:inline-block'>Mở EduTrack</a></p>";
        }
        return "<!doctype html><html lang='vi'><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "</head><body style='margin:0;background:#f3f6fb;font-family:Arial,sans-serif;color:#1e293b'>"
            + "<div style='max-width:600px;margin:32px auto;background:#ffffff;border-radius:12px;overflow:hidden'>"
            + "<div style='background:#1d4ed8;color:#ffffff;padding:24px 32px;font-size:22px;font-weight:bold'>EduTrack</div>"
            + "<div style='padding:28px 32px;line-height:1.6'><h1 style='font-size:20px;margin:0 0 18px'>"
            + safeSubject + "</h1><div>" + safeBody + "</div>" + action + "</div>"
            + "<div style='padding:16px 32px;background:#f8fafc;color:#64748b;font-size:12px'>"
            + "Thông báo tự động từ EduTrack.</div></div></body></html>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
