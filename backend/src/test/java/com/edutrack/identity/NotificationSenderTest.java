package com.edutrack.identity;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class NotificationSenderTest {
    @Test
    void emailTemplateEscapesUntrustedTextAndKeepsActionLink() {
        String html = NotificationSender.html("Kích hoạt <script>",
            "Xin chào <img src=x onerror=alert(1)>\nhttps://example.test/activate?token=a&lang=vi");
        assertThat(html).contains("Kích hoạt &lt;script&gt;");
        assertThat(html).contains("&lt;img src=x onerror=alert(1)&gt;");
        assertThat(html).doesNotContain("<script>", "<img src=x");
        assertThat(html).contains("href='https://example.test/activate?token=a&amp;lang=vi'");
    }
}
