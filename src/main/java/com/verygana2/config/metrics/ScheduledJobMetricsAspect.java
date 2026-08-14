package com.verygana2.config.metrics;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Mide toda ejecución de un método {@code @Scheduled} sin tocar los 20 schedulers del
 * proyecto. Funciona porque {@code ScheduledAnnotationBeanPostProcessor} invoca el método
 * sobre el proxy del bean, no sobre la instancia cruda — el mismo motivo por el que
 * {@code @Transactional} funciona en un job.
 *
 * {@code @Order(0)} lo deja por fuera de {@code AuditAspect} ({@code @Order(1)}) y de las
 * transacciones, así el tiempo medido incluye commit y rollback.
 */
@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class ScheduledJobMetricsAspect {

    private final ScheduledJobMetrics metrics;

    /** El segundo caso cubre {@code @Scheduled} repetido, que Spring envuelve en {@code @Schedules}. */
    @Pointcut("@annotation(org.springframework.scheduling.annotation.Scheduled)"
            + " || @annotation(org.springframework.scheduling.annotation.Schedules)")
    void scheduledMethod() {
    }

    @Around("scheduledMethod()")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String job = ScheduledJobMetrics.jobName(joinPoint.getTarget().getClass(), method);

        long startNanos = System.nanoTime();
        metrics.jobStarted(job);
        try {
            Object result = joinPoint.proceed();
            metrics.jobSucceeded(job, System.nanoTime() - startNanos);
            return result;
        } catch (Throwable error) {
            metrics.jobFailed(job, System.nanoTime() - startNanos, error);
            throw error;
        } finally {
            metrics.jobFinished(job);
        }
    }
}