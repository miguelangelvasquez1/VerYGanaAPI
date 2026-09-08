package com.verygana2.utils.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Verifica la mecánica de reintento de {@link ConcurrencyRetryAspect}: cuántas
 * veces re-ejecuta el método, con qué excepciones, cuándo NO reintenta, y la
 * métrica {@code db.concurrency.conflict} que emite.
 */
@DisplayName("ConcurrencyRetryAspect — reintento ante conflictos de concurrencia")
class ConcurrencyRetryAspectTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ConcurrencyRetryAspect aspect = new ConcurrencyRetryAspect(meterRegistry);

    @AfterEach
    void clearTxState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private double conflictCount(String outcome) {
        return meterRegistry.find(ConcurrencyRetryAspect.CONFLICT_METRIC)
                .tag("outcome", outcome)
                .counters().stream()
                .mapToDouble(c -> c.count())
                .sum();
    }

    private RetryOnConcurrencyConflict spec(int maxAttempts) {
        RetryOnConcurrencyConflict retryable = mock(RetryOnConcurrencyConflict.class);
        when(retryable.maxAttempts()).thenReturn(maxAttempts);
        when(retryable.baseDelayMillis()).thenReturn(1L);
        return retryable;
    }

    private ProceedingJoinPoint joinPoint() {
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.toShortString()).thenReturn("TestBean.testMethod(..)");
        when(jp.getSignature()).thenReturn(sig);
        return jp;
    }

    @Test
    @DisplayName("éxito al primer intento: ejecuta el método una sola vez, sin métrica")
    void succeedsFirstTry() throws Throwable {
        ProceedingJoinPoint jp = joinPoint();
        when(jp.proceed()).thenReturn("ok");

        Object result = aspect.retryOnConflict(jp, spec(4));

        assertThat(result).isEqualTo("ok");
        verify(jp, times(1)).proceed();
        assertThat(meterRegistry.find(ConcurrencyRetryAspect.CONFLICT_METRIC).counter()).isNull();
    }

    @Test
    @DisplayName("deadlock y luego éxito: reintenta, devuelve el resultado y cuenta outcome=recovered")
    void retriesThenSucceeds() throws Throwable {
        ProceedingJoinPoint jp = joinPoint();
        when(jp.proceed())
                .thenThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"))
                .thenReturn("ok");

        Object result = aspect.retryOnConflict(jp, spec(4));

        assertThat(result).isEqualTo("ok");
        verify(jp, times(2)).proceed();
        assertThat(conflictCount("recovered")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("conflicto persistente: agota maxAttempts, propaga y cuenta outcome=exhausted")
    void givesUpAfterMaxAttempts() throws Throwable {
        ProceedingJoinPoint jp = joinPoint();
        when(jp.proceed()).thenThrow(new ObjectOptimisticLockingFailureException("stale", new RuntimeException()));

        assertThatThrownBy(() -> aspect.retryOnConflict(jp, spec(3)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(jp, times(3)).proceed();
        assertThat(conflictCount("exhausted")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("excepción no transitoria: se propaga sin reintentar ni contar")
    void doesNotRetryNonTransient() throws Throwable {
        ProceedingJoinPoint jp = joinPoint();
        when(jp.proceed()).thenThrow(new DataIntegrityViolationException("constraint"));

        assertThatThrownBy(() -> aspect.retryOnConflict(jp, spec(4)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(jp, times(1)).proceed();
        assertThat(meterRegistry.find(ConcurrencyRetryAspect.CONFLICT_METRIC).counter()).isNull();
    }

    @Test
    @DisplayName("con transacción externa activa: no reintenta, propaga y cuenta outcome=not_retried")
    void doesNotRetryWithinOuterTransaction() throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        ProceedingJoinPoint jp = joinPoint();
        when(jp.proceed()).thenThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"));

        assertThatThrownBy(() -> aspect.retryOnConflict(jp, spec(4)))
                .isInstanceOf(CannotAcquireLockException.class);

        verify(jp, times(1)).proceed();
        assertThat(conflictCount("not_retried")).isEqualTo(1.0);
    }
}
