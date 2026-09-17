package com.verygana2.config.metrics;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.repositories.finance.PayoutRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Métricas de negocio del ciclo de payouts.
 *
 * Existe por un hueco concreto que {@link ScheduledJobMetrics} no puede cubrir:
 * {@code PayoutServiceImpl} atrapa la excepción de cada payout <i>dentro</i> del bucle
 * ({@code processScheduledPayouts}, {@code retryFailedPayouts}) y
 * {@code PayoutScheduler.checkWompiBalance} se traga la suya entera. Para
 * {@link ScheduledJobMetricsAspect} el job termina en SUCCESS aunque no se haya dispersado
 * un solo peso, así que {@code outcome=FAILURE} nunca dispara. Lo de acá es la única señal
 * de que el dinero no salió.
 *
 * <h2>Dos familias, y la diferencia importa</h2>
 *
 * <b>Contadores</b> — los alimenta el propio flujo en cada punto donde se decide algo sobre
 * dinero. Miden <i>eventos</i>: solo tienen sentido leídos con {@code rate()} o
 * {@code increase()}, y un reinicio los vuelve a cero (Prometheus lo compensa solo).
 *
 * <b>Gauges</b> — los refresca {@link #refreshPayoutGauges()} consultando la BD cada minuto.
 * Miden <i>estado</i>: cuánta plata está atascada y desde cuándo. Salen de la BD y no de
 * acumuladores en memoria justamente para que un despliegue no borre la señal — un payout que
 * lleva tres días en PROCESSING sigue llevando tres días después de reiniciar la app. Es la
 * diferencia entre enterarse de que algo se rompió y enterarse de que <i>sigue</i> roto.
 *
 * <h2>Qué detecta cada gauge</h2>
 * <ul>
 *   <li>{@code payout_oldest_age_seconds{status="PROCESSING"}} alto — se envió la
 *       transferencia a Wompi y el webhook nunca llegó. El dinero salió de la cuenta y el
 *       Payout quedó sin confirmar.</li>
 *   <li>{@code payout_oldest_age_seconds{status="SCHEDULED"}} alto — el payout se creó pero
 *       nunca se envió. El caso típico es el empresario sin método de pago verificado:
 *       {@code processOnePayout} lanza IllegalStateException, el bucle la atrapa y el payout
 *       se queda ahí. Nadie se entera nunca.</li>
 *   <li>{@code payout_pending_count{status="FAILED"}} sostenido — el reintento de las 04:30
 *       tampoco los sacó.</li>
 *   <li>{@code wompi_payout_balance_cents} bajo — la cuenta de dispersión no está fondeada y
 *       el ciclo de mañana va a fallar entero.</li>
 *   <li>{@code wompi_payout_balance_age_seconds} alto — no se ha podido <i>leer</i> el balance.
 *       Es el fail-open de {@code checkWompiBalance}: sin esto, un error permanente
 *       consultando a Wompi se ve exactamente igual que un balance sano.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayoutMetrics {

    // --- Contadores (eventos) ---
    static final String SCHEDULED = "payout.scheduled";
    static final String SCHEDULED_AMOUNT = "payout.scheduled.amount.cents";
    static final String WITHOUT_METHOD = "payout.without.method";
    static final String SENT = "payout.sent";
    static final String RETRIED = "payout.retried";
    static final String PROCESSING_ERRORS = "payout.processing.errors";
    static final String CONFIRMED = "payout.confirmed";
    static final String PAID_AMOUNT = "payout.paid.amount.cents";

    // --- Gauges (estado) ---
    static final String PENDING_COUNT = "payout.pending.count";
    static final String PENDING_AMOUNT = "payout.pending.amount.cents";
    static final String OLDEST_AGE = "payout.oldest.age";
    static final String BALANCE = "wompi.payout.balance.cents";
    static final String BALANCE_AGE = "wompi.payout.balance.age";

    /**
     * PAID queda por fuera a propósito: es el estado terminal sano y su conteo crece sin techo,
     * así que como gauge no dice nada. Lo que importa es lo que NO llegó a PAID.
     */
    private static final List<PayoutStatus> PENDING_STATUSES =
            List.of(PayoutStatus.SCHEDULED, PayoutStatus.PROCESSING, PayoutStatus.FAILED);

    private final MeterRegistry registry;
    private final PayoutRepository payoutRepository;

    /** Referencia fuerte al estado: Micrometer guarda los gauges con referencia débil. */
    private final Map<PayoutStatus, PendingState> pending = new EnumMap<>(PayoutStatus.class);

    private static final class PendingState {
        final AtomicLong count = new AtomicLong();
        final AtomicLong netCents = new AtomicLong();
        /** Epoch ms del payout más viejo en este estado. 0 = no hay ninguno. */
        final AtomicLong oldestEpochMs = new AtomicLong();
    }

    /**
     * Centinela: todavía no se ha logrado leer el balance. Se expone como NaN y no como 0
     * para que la alerta de "balance bajo" no dispare por no haber leído nunca — un 0 aquí
     * sería indistinguible de una cuenta vacía.
     */
    private static final long BALANCE_UNKNOWN = Long.MIN_VALUE;

    private final AtomicLong balanceCents = new AtomicLong(BALANCE_UNKNOWN);

    /**
     * Arranca en el instante de boot, igual que en ScheduledJobMetrics: así "hace cuánto que
     * no leo el balance" crece desde el arranque y la alerta funciona aunque la consulta
     * nunca haya tenido éxito.
     */
    private final AtomicLong lastBalanceReadEpochMs = new AtomicLong(System.currentTimeMillis());

    /**
     * Los gauges se pre-registran al arrancar, no en el primer evento: si esperáramos a que
     * exista un payout atascado, la serie no existiría y la alerta nunca se evaluaría.
     */
    @PostConstruct
    public void registerGauges() {
        for (PayoutStatus status : PENDING_STATUSES) {
            PendingState state = new PendingState();
            pending.put(status, state);
            String tag = status.name();

            Gauge.builder(PENDING_COUNT, state, s -> s.count.get())
                    .description("Payouts que siguen sin llegar a PAID")
                    .tag("status", tag)
                    .register(registry);

            Gauge.builder(PENDING_AMOUNT, state, s -> s.netCents.get())
                    .description("Dinero neto retenido en payouts que no han llegado a PAID")
                    .baseUnit("cents")
                    .tag("status", tag)
                    .register(registry);

            Gauge.builder(OLDEST_AGE, state, PayoutMetrics::oldestAgeSeconds)
                    .description("Antigüedad del payout más viejo en este estado")
                    .baseUnit("seconds")
                    .tag("status", tag)
                    .register(registry);
        }

        Gauge.builder(BALANCE, balanceCents, PayoutMetrics::balanceOrNaN)
                .description("Balance de la cuenta de dispersión de Wompi Payouts")
                .baseUnit("cents")
                .register(registry);

        Gauge.builder(BALANCE_AGE, lastBalanceReadEpochMs,
                        ts -> (System.currentTimeMillis() - ts.get()) / 1000.0)
                .description("Segundos desde la última lectura exitosa del balance de Wompi")
                .baseUnit("seconds")
                .register(registry);

        log.info("[METRICS] Gauges de payouts registrados para los estados {}", PENDING_STATUSES);
    }

    private static double oldestAgeSeconds(PendingState state) {
        long epochMs = state.oldestEpochMs.get();
        return epochMs == 0 ? 0d : (System.currentTimeMillis() - epochMs) / 1000.0;
    }

    private static double balanceOrNaN(AtomicLong cents) {
        long value = cents.get();
        return value == BALANCE_UNKNOWN ? Double.NaN : value;
    }

    // ─── Gauges: refresco desde la BD ─────────────────────────────────────────

    /**
     * Una sola consulta agregada por minuto en vez de leer la BD en cada scrapeo: el scrapeo
     * de Prometheus no debe poder colgarse esperando a MySQL, y a 15s de intervalo serían
     * cuatro veces más consultas para un dato que cambia una vez al día.
     */
    @Scheduled(fixedDelay = 60_000)
    public void refreshPayoutGauges() {
        // Cero primero: un estado que dejó de aparecer en el GROUP BY es un estado vacío,
        // no un estado que conserva su último valor. Sin esto, un FAILED que se resolvió
        // dejaría la alerta disparada para siempre.
        pending.values().forEach(state -> {
            state.count.set(0);
            state.netCents.set(0);
            state.oldestEpochMs.set(0);
        });

        for (PayoutRepository.PayoutStatusAggregate row : payoutRepository.aggregateByStatus(PENDING_STATUSES)) {
            PendingState state = pending.get(row.getStatus());
            if (state == null) {
                continue;
            }
            state.count.set(row.getTotal());
            state.netCents.set(row.getNetCents());
            state.oldestEpochMs.set(
                    row.getOldestScheduledAt() == null ? 0 : row.getOldestScheduledAt().toInstant().toEpochMilli());
        }
    }

    // ─── Contadores: los alimenta el flujo de payouts ─────────────────────────

    /** Fase 1: se creó un Payout SCHEDULED por {@code netCents}. */
    public void payoutScheduled(long netCents) {
        counter(SCHEDULED, "Payouts creados en estado SCHEDULED").increment();
        counter(SCHEDULED_AMOUNT, "Neto acumulado de los payouts creados").increment(netCents);
    }

    /**
     * El empresario no tiene método de pago verificado. El payout se crea igual y queda
     * en SCHEDULED sin transferencia posible: es plata que se le debe y que nunca sale.
     */
    public void payoutWithoutMethod() {
        counter(WITHOUT_METHOD, "Payouts creados para un empresario sin método de pago verificado")
                .increment();
    }

    /** Fase 2: Wompi aceptó o rechazó la solicitud de transferencia. */
    public void payoutSent(boolean accepted) {
        registry.counter(SENT, "outcome", accepted ? "ACCEPTED" : "REJECTED").increment();
    }

    /** Fase 3: un payout FAILED volvió a SCHEDULED para reintentarse. */
    public void payoutRetried() {
        counter(RETRIED, "Reintentos de payouts que habían quedado en FAILED").increment();
    }

    /**
     * La excepción que el bucle de {@code PayoutServiceImpl} atrapa y no propaga. Sin este
     * contador, el job reporta SUCCESS con todos sus payouts caídos.
     *
     * @param phase SCHEDULE, PROCESS o RETRY — en qué bucle ocurrió
     */
    public void payoutProcessingFailed(String phase, Throwable error) {
        registry.counter(PROCESSING_ERRORS,
                "phase", phase,
                "exception", error.getClass().getSimpleName()).increment();
    }

    /**
     * Resultado final que trae el webhook de Wompi.
     *
     * @param reason para un fallo, el {@code WompiTransactionStatus} — un enum, no el texto
     *               libre de {@code failureReason}: una etiqueta con mensajes de error
     *               arbitrarios haría explotar la cardinalidad de la serie.
     */
    public void payoutConfirmed(boolean paid, long netCents, String reason) {
        registry.counter(CONFIRMED,
                "outcome", paid ? "PAID" : "FAILED",
                "reason", reason).increment();
        if (paid) {
            counter(PAID_AMOUNT, "Neto efectivamente dispersado a los empresarios").increment(netCents);
        }
    }

    /** Lectura exitosa del balance de la cuenta de dispersión. */
    public void wompiBalanceRead(long cents) {
        balanceCents.set(cents);
        lastBalanceReadEpochMs.set(System.currentTimeMillis());
    }

    /**
     * No se pudo leer el balance. No se toca {@code balanceCents}: se deja el último valor
     * conocido (o NaN) y se deja envejecer {@code wompi_payout_balance_age_seconds}, que es
     * lo que delata que la consulta está fallando en silencio.
     */
    public void wompiBalanceUnavailable(Throwable error) {
        log.debug("[METRICS] Balance de Wompi ilegible: {}", error.toString());
    }

    private Counter counter(String name, String description) {
        return Counter.builder(name).description(description).register(registry);
    }
}
