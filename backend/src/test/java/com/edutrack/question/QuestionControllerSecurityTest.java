package com.edutrack.question;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuestionController.class)
@Import(QuestionControllerSecurityTest.MethodSecurity.class)
@ActiveProfiles("test")
class QuestionControllerSecurityTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity { }
    @Autowired MockMvc mvc;
    @MockitoBean QuestionService questions;
    @MockitoBean JdbcTemplate db;

    @Test @WithMockUser(roles="STUDENT")
    void studentCannotReadAdminQuestionWithAnswerKey() throws Exception {
        mvc.perform(get("/api/v1/admin/questions/"+UUID.randomUUID())).andExpect(status().isForbidden());
        verifyNoInteractions(questions);
    }

    @Test @WithMockUser(roles="PARENT")
    void parentCannotReadAdminQuestionWithAnswerKey() throws Exception {
        mvc.perform(get("/api/v1/admin/questions/"+UUID.randomUUID())).andExpect(status().isForbidden());
        verifyNoInteractions(questions);
    }
}
