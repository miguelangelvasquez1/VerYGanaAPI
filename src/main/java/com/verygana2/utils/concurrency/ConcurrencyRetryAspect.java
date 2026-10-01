package com.verygana2.utils.concurrency;

import java.util.concurrent.ThreadLocalRandom;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementa {@link RetryOnConcurrencyConflict}: re-ejecuta el método cuando
 * lanza un {@link TransientDataAccessException} (deadlock 1213, lock-wait
 * timeout 1205, fallo de bloqueo optimista), con backoff exponencial y jitter.
 *
 * <p><b>Orden {@code 10}.</b> Queda:
 * <ul>
 *   <li>por dentro de {@code AuditAspect} ({@code @Order(1)}) — una operación
 *       que se reintenta 3 veces produce un único registro de auditoría, no
 *       tres;</li>
 *   <li>por fuera del interceptor de {@code @Transactional} y de
 *       {@code PlanGuardAspect} (ambos {@code LOWEST_PRECEDENCE}) — así cada
 *       reintento entra con una transacción nueva y re-valida el plan.</li>
 * </ul>
 *
 * <p>Cada conflicto interceptado incrementa la métrica
 * {@code db.concurrency.conflict} ({@code db_concurrency_conflict_total} en
 * Prometheus) con tags {@code method} y {@code outcome}
 * ({@code recovered} / {@code exhausted} / {@code not_retried}) — sustituye a
 * revisar {@code SHOW ENGINE INNODB STATUS} a mano y funciona igual en MySQL y
 * en PostgreSQL.
 */
@Aspect
@Component
@Order(10)
@Slf4j
@RequiredArgsConstructor
public class ConcurrencyRetryAspect {

    /** Tope del factor de backoff: base · 2^6 ≈ 64·base como espera máxima. */
    private static final int MAX_BACKOFF_SHIFT = 6;

    static final String CONFLICT_METRIC = "db.concurrency.conflict";

    private final MeterRegistry meterRegistry;

    @Around("@annotation(retryable) && within(com.verygana2..*)")
    public Object retryOnConflict(ProceedingJoinPoint joinPoint, RetryOnConcurrencyConflict retryable)
            throws Throwable {

        String target = joinPoint.getSignature().toShortString();

        // Si ya hay una transacción externa en curso, un deadlock la deja
        // marcada rollback-only: reintentar aquí no serviría de nada. El
        // reintento corresponde al límite transaccional más externo.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            try {
                return joinPoint.proceed();
            } catch (TransientDataAccessException ex) {
                countConflict(target, "not_retried");
                throw ex;
            }
        }

        int maxAttempts = Math.max(1, retryable.maxAttempts());
        int attempt = 1;

        while (true) {
            try {
                Object result = joinPoint.proceed();
                if (attempt > 1) {
                    countConflict(target, "recovered");
                }
                return result;
            } catch (TransientDataAccessException ex) {
                if (attempt >= maxAttempts) {
                    countConflict(target, "exhausted");
                    log.warn("{}: conflicto de concurrencia sin resolver tras {} intentos: {}",
                            target, maxAttempts, ex.toString());
                    throw ex;
                }
                long delayMillis = backoffMillis(attempt, retryable.baseDelayMillis());
                log.info("{}: conflicto de concurrencia (intento {}/{}), reintentando en {} ms — {}",
                        target, attempt, maxAttempts, delayMillis, ex.getMostSpecificCause().getMessage());
                sleep(delayMillis);
                attempt++;
            }
        }
    }

    private void countConflict(String method, String outcome) {
        Counter.builder(CONFLICT_METRIC)
                .description("Conflictos de concurrencia de BD (deadlock, lock-wait, lock optimista) "
                        + "interceptados por @RetryOnConcurrencyConflict")
                .tag("method", method)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    /** Backoff exponencial (base · 2^(n-1)) con jitter pleno para dispersar a las víctimas. */
    private long backoffMillis(int attempt, long baseDelayMillis) {
        long base = Math.max(1L, baseDelayMillis);
        long ceiling = base << Math.min(attempt - 1, MAX_BACKOFF_SHIFT);
        return ThreadLocalRandom.current().nextLong(base, ceiling + 1);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reintento de concurrencia interrumpido", e);
        }
    }
}
