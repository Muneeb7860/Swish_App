package com.platform.core.checkout.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AiEvaluationTimeoutSweeperTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AiEvaluationTimeoutSweeper sweeper;

    @Test
    public void testSweepStalledEvaluations_Success() {
        when(jdbcTemplate.update(anyString())).thenReturn(5);

        sweeper.sweepStalledEvaluations();

        verify(jdbcTemplate, times(1)).update(anyString());
    }

    @Test
    public void testSweepStalledEvaluations_NoUpdates() {
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        sweeper.sweepStalledEvaluations();

        verify(jdbcTemplate, times(1)).update(anyString());
    }

    @Test
    public void testSweepStalledEvaluations_BadSqlGrammarException() {
        when(jdbcTemplate.update(anyString())).thenThrow(new BadSqlGrammarException("task", "sql", new SQLException()));

        sweeper.sweepStalledEvaluations();

        verify(jdbcTemplate, times(1)).update(anyString());
    }

    @Test
    public void testSweepStalledEvaluations_GenericException() {
        when(jdbcTemplate.update(anyString())).thenThrow(new RuntimeException("DB down"));

        sweeper.sweepStalledEvaluations();

        verify(jdbcTemplate, times(1)).update(anyString());
    }
}
