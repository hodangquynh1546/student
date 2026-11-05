package com.edutrack.assessment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class ParentResultServiceTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final ParentResultService service = new ParentResultService(db);

    @Test
    void unlinkedParentCannotListResults() {
        UUID parent = UUID.randomUUID(), student = UUID.randomUUID();
        when(db.queryForObject(any(String.class), eq(Boolean.class), eq(parent), eq(student))).thenReturn(false);
        assertThatThrownBy(() -> service.list(parent, student, 0, 20))
            .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                org.assertj.core.api.Assertions.assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(db).queryForObject(any(String.class), eq(Boolean.class), eq(parent), eq(student));
        verifyNoMoreInteractions(db);
    }

    @Test
    void revokedParentCannotReadAttemptDetail() {
        UUID parent = UUID.randomUUID(), student = UUID.randomUUID();
        when(db.queryForObject(any(String.class), eq(Boolean.class), eq(parent), eq(student))).thenReturn(false);
        assertThatThrownBy(() -> service.detail(parent, student, UUID.randomUUID()))
            .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                org.assertj.core.api.Assertions.assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(db).queryForObject(any(String.class), eq(Boolean.class), eq(parent), eq(student));
        verifyNoMoreInteractions(db);
    }
}
