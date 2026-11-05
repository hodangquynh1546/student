package com.edutrack.analytics.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class RankingServiceTest {
    private final UUID student = UUID.randomUUID();
    private final UUID exam1 = UUID.randomUUID();
    private final UUID exam2 = UUID.randomUUID();

    @Test
    void scoreExplainsAverageCompletionAndImprovement() {
        var first = new RankingService.Result(student, exam1, Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("60"));
        var second = new RankingService.Result(student, exam2, Instant.parse("2026-02-01T00:00:00Z"), new BigDecimal("80"));
        var row = RankingService.score(student, "A", 4, List.of(first, second));
        assertEquals(new BigDecimal("70.00"), row.averagePercent());
        assertEquals(new BigDecimal("50.00"), row.completionPercent());
        assertEquals(new BigDecimal("60.00"), row.progressPercent());
        assertEquals(new BigDecimal("65.00"), row.rankingScore());
    }

    @Test
    void noPublishedResultCannotGainScore() {
        var row = RankingService.score(student, "A", 3, List.of());
        assertEquals(0, row.rankingScore().compareTo(BigDecimal.ZERO));
        assertEquals(0, row.completedCount());
    }

    @Test
    void revokedParentLinkCannotSeeRanking() {
        JdbcTemplate db = org.mockito.Mockito.mock(JdbcTemplate.class);
        NamedParameterJdbcTemplate namedDb = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        when(db.queryForObject(any(String.class), eq(Boolean.class), any(), any())).thenReturn(false);
        var service = new RankingService(db, namedDb);
        var error = assertThrows(ResponseStatusException.class,
            () -> service.parent(UUID.randomUUID(), student, UUID.randomUUID(), RankingService.Period.ALL, null));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }
}
