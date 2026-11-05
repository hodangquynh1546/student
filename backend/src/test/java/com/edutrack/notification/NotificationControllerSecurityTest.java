package com.edutrack.notification;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(NotificationControllerSecurityTest.MethodSecurity.class)
@ActiveProfiles("test")
class NotificationControllerSecurityTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity { }
    @Autowired MockMvc mvc;
    @MockitoBean NotificationService notifications;
    @MockitoBean JdbcTemplate db;

    @Test @WithMockUser(roles = "STUDENT")
    void studentCannotCreateNotificationForAnotherUser() throws Exception {
        mvc.perform(post("/api/v1/admin/notifications").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"recipientId\":\"" + UUID.randomUUID() + "\",\"type\":\"GENERAL\",\"title\":\"Hello\",\"body\":\"Hello\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(notifications);
    }

    @Test @WithMockUser(roles = "PARENT")
    void parentCannotCreateNotificationForAnotherUser() throws Exception {
        mvc.perform(post("/api/v1/admin/notifications").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"recipientId\":\"" + UUID.randomUUID() + "\",\"type\":\"GENERAL\",\"title\":\"Hello\",\"body\":\"Hello\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(notifications);
    }
}
