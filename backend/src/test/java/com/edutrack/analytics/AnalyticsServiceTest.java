package com.edutrack.analytics;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {
    @Test
    void parentWithoutVerifiedActiveLinkCannotReadChildDashboard() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        UUID parent=UUID.randomUUID(), child=UUID.randomUUID();
        when(db.queryForObject(anyString(),eq(Boolean.class),eq(parent),eq(child))).thenReturn(false);
        var service=new AnalyticsService(db);

        var error=assertThrows(ResponseStatusException.class,()->service.parent(parent,child));

        assertEquals(HttpStatus.NOT_FOUND,error.getStatusCode());
        verify(db).queryForObject(anyString(),eq(Boolean.class),eq(parent),eq(child));
        verifyNoMoreInteractions(db);
    }

    @Test
    void parentLinkCheckRequiresAcceptedRequestAndNonRevokedLink() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        UUID parent=UUID.randomUUID(), child=UUID.randomUUID();
        when(db.queryForObject(anyString(),eq(Boolean.class),eq(parent),eq(child))).thenAnswer(call -> {
            String sql=call.getArgument(0);
            assertTrue(sql.contains("l.revoked_at IS NULL"));
            assertTrue(sql.contains("r.status='ACCEPTED'"));
            return false;
        });
        assertThrows(ResponseStatusException.class,()->new AnalyticsService(db).parent(parent,child));
    }

    @Test
    void percentageHasExplicitEmptyDenominatorAndTwoDecimalPrecision() {
        assertEquals(new BigDecimal("33.33"),AnalyticsService.percent(BigDecimal.ONE,BigDecimal.valueOf(3)));
        assertEquals(null,AnalyticsService.percent(BigDecimal.ONE,BigDecimal.ZERO));
    }
}
