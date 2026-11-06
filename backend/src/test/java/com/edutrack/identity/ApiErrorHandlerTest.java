package com.edutrack.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.ExceptionHandler;

class ApiErrorHandlerTest {
    @Test
    void mailFailureReturnsActionableMessageWithoutTransportDetails() throws NoSuchMethodException {
        var response = new ApiErrorHandler().mailUnavailable();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("message",
            "Không gửi được email kích hoạt. Vui lòng kiểm tra cấu hình SMTP và thử lại.");
        assertThat(ApiErrorHandler.class.getMethod("mailUnavailable").getAnnotation(ExceptionHandler.class).value())
            .contains(MailException.class);
    }
}
