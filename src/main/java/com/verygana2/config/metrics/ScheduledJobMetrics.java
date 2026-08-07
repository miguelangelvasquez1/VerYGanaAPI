package com.verygana2.config.metrics;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Estado y métricas de los jobs {@code @Scheduled}. Lo alimenta
 * {@link ScheduledJobMetricsAspect}; acá vive el registro en Micrometer.
 *
 * Métricas expuestas (nombres Prometheus entre paréntesis):
 * <ul>
 *   <li><b>scheduled.job.duration</b> ({@code scheduled_job_duration_seconds}) — Timer con
 *       tags {@code job}, {@code outcome} (SUCCESS/FAILURE) y {@code exception}.</li>
 *   <li><b>scheduled.job.last.success.age</b> ({@code scheduled_job_last_success_age_seconds}) —
 *       segundos desde la última ejecución exitosa. Es la métrica que detecta el fallo más
 *       caro y más silencioso: <i>el job que dejó de correr</i>. Un job diario con este valor
 *       en 90.000s no disparó en 25 horas.</li>
 *   <li><b>scheduled.job.active</b> ({@code scheduled_job_active}) — ejecuciones en curso.
 *       Sostenido en 1 durante horas = job colgado (típicamente en una llamada HTTP externa
 *       sin timeout).</li>
 * </ul>
 *
 * Los gauges se pre-registran al arrancar escaneando los beans en busca de métodos
 * {@code @Scheduled}, no en la primera ejecución: si esperáramos a que el job corra, un job
 * que nunca corre nunca publicaría la serie y la alerta jamás se evaluaría.
 *
 * <b>Ojo con los jobs que atrapan sus propias excepciones</b> (p. ej.
 * {@code ReconciliationScheduler}, {@code PayoutScheduler#checkWompiBalance}): para el aspecto
 * terminan en SUCCESS porque no propagan nada. La duración y el "último éxito" siguen siendo
 * correctos, pero {@code outcome=FAILURE} no los cubre — ahí hace falta una métrica de negocio
 * dentro del propio job.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobMetrics {

    static final String DURATION = "scheduled.job.duration";
    static final String LAST_SUCCESS_AGE = "scheduled.job.last.success.age";
    static final String ACTIVE = "scheduled.job.active";

    private final MeterRegistry registry;
    private final ConfigurableListableBeanFactory beanFactory;

    /** Referencia fuerte al estado: Micrometer guarda los gauges con referencia débil. */
    private final Map<String, JobState> states = new ConcurrentHashMap<>();

    private static final class JobState {
        /**
         * Arranca en el instante de boot, no en 0: así "segundos desde el último éxito" crece
         * desde el arranque y la alerta funciona igual si el job nunca llegó a correr. El
         * contador se reinicia en cada despliegue — asumido: los umbrales se fijan sobre el
         * intervalo del job, que siempre es mayor que la ventana de un deploy.
         */
        final AtomicLong lastSuccessEpochMs = new AtomicLong(System.currentTimeMillis());
        final AtomicInteger active = new AtomicInteger();
    }

    static String jobName(Class<?> targetClass, Method method) {
        return ClassUtils.getUserClass(targetClass).getSimpleName() + "." + method.getName();
    }

    /**
     * Descubre los {@code @Scheduled} sin instanciar beans ({@code getType} con
     * {@code allowFactoryBeanInit=false}) y les crea los gauges.
     */
    @EventListener(ApplicationReadyEvent.class)
    void registerDeclaredJobs() {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> type;
            try {
                type = beanFactory.getType(beanName, false);
            } catch (Exception e) {
                continue;   // bean no resoluble en este punto: no es un scheduler
            }
            if (type == null) {
                continue;
            }
            Class<?> userClass = ClassUtils.getUserClass(type);
            ReflectionUtils.doWithMethods(userClass, method -> {
                // getAnnotationsByType cubre @Scheduled repetido (contenedor @Schedules)
                if (method.getAnnotationsByType(Scheduled.class).length > 0) {
                    stateFor(jobName(userClass, method));
                }
            }, ReflectionUtils.USER_DECLARED_METHODS);
        }

        log.info("[METRICS] {} jobs @Scheduled instrumentados: {}",
                states.size(), new TreeSet<>(states.keySet()));
    }

    void jobStarted(String job) {
        stateFor(job).active.incrementAndGet();
    }

    void jobSucceeded(String job, long durationNanos) {
        stateFor(job).lastSuccessEpochMs.set(System.currentTimeMillis());
        timer(job, "SUCCESS", "none").record(durationNanos, TimeUnit.NANOSECONDS);
    }

    void jobFailed(String job, long durationNanos, Throwable error) {
        timer(job, "FAILURE", error.getClass().getSimpleName())
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    void jobFinished(String job) {
        stateFor(job).active.decrementAndGet();
    }

    private Timer timer(String job, String outcome, String exception) {
        return Timer.builder(DURATION)
                .description("Duración de cada ejecución de un job @Scheduled")
                .tag("job", job)
                .tag("outcome", outcome)
                .tag("exception", exception)
                .register(registry);
    }

    private JobState stateFor(String job) {
        return states.computeIfAbsent(job, name -> {
            JobState state = new JobState();

            Gauge.builder(LAST_SUCCESS_AGE, state,
                            s -> (System.currentTimeMillis() - s.lastSuccessEpochMs.get()) / 1000.0)
                    .description("Segundos transcurridos desde la última ejecución exitosa")
                    .baseUnit("seconds")
                    .tag("job", name)
                    .register(registry);

            Gauge.builder(ACTIVE, state, s -> s.active.get())
                    .description("Ejecuciones del job actualmente en curso")
                    .tag("job", name)
                    .register(registry);

            return state;
        });
    }
}