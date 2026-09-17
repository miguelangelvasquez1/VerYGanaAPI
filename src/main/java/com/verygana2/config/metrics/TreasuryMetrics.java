package com.verygana2.config.metrics;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.models.records.IssuanceTotals;
import com.verygana2.models.records.KeyBacking;
import com.verygana2.models.records.TreasurySnapshot;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.surveys.SurveyRewardRepository;
import com.verygana2.services.finance.KeyBackingCalculator;
import com.verygana2.services.interfaces.finance.TreasuryService;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Métricas de solvencia del fondo de llaves.
 *
 * Existe por el mismo hueco que {@link PayoutMetrics}: {@code ReconciliationScheduler}
 * atrapa su propia excepción, así que para {@code ScheduledJobMetricsAspect} el job
 * termina en SUCCESS aunque la reconciliación haya encontrado un desastre. Y encima
 * corre una vez por semana: entre lunes y lunes, un desbalance existe solo en un log
 * que nadie lee. Estos gauges son la única señal continua.
 *
 * <h2>Por qué gauges de BD y no acumuladores</h2>
 * Miden estado, no eventos: "cuánto respaldo falta AHORA". Salen de la base en cada
 * refresco para que un despliegue no borre la señal — un déficit de respaldo sigue
 * ahí después de reiniciar, y esa es justo la diferencia entre enterarse de que algo
 * se rompió y enterarse de que sigue roto.
 *
 * <h2>Qué detecta cada uno</h2>
 * <ul>
 *   <li>{@code treasury_keys_backing_pct} &lt; 100 — hay llaves emitidas que
 *       KEYS_RESERVE no puede respaldar. Es LA métrica: el síntoma tardío es que
 *       convertKeysToPayoutPending empieza a rechazar copagos de toda la plataforma.</li>
 *   <li>{@code treasury_identity_drift_cents} ≠ 0 — entró o salió dinero del fondo sin
 *       dejar rastro contable. Negativo es déficit; positivo son llaves que salieron de
 *       circulación sin debitar el fondo.</li>
 *   <li>{@code key_issuance_oldest_unsettled_age_seconds} alto — el job de liquidación
 *       por lotes dejó de correr. El diferencial no se pierde, pero KEYS_RESERVE queda
 *       inflado mientras tanto y el resto de métricas se desvían con él.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TreasuryMetrics {

    static final String KEY_LIABILITY = "treasury.key.liability.cents";
    static final String BACKING_PCT = "treasury.keys.backing.pct";
    static final String IDENTITY_DRIFT = "treasury.identity.drift.cents";
    static final String UNSETTLED_DELTA = "key.issuance.unsettled.cents";
    static final String OLDEST_UNSETTLED_AGE = "key.issuance.oldest.unsettled.age";

    private final MeterRegistry registry;
    private final TreasuryService treasuryService;
    private final KeyBackingCalculator keyBackingCalculator;
    private final AdLikeRepository adLikeRepository;
    private final SurveyRewardRepository surveyRewardRepository;
    private final Clock clock;

    /** Referencia fuerte: Micrometer guarda los gauges con referencia débil. */
    private final AtomicLong keyLiabilityCents = new AtomicLong();
    private final AtomicLong identityDriftCents = new AtomicLong();
    private final AtomicLong unsettledDeltaCents = new AtomicLong();
    private final AtomicLong oldestUnsettledEpochMs = new AtomicLong();

    /**
     * 100 = todo lo emitido está respaldado. Arranca en 100 y no en 0 para que la
     * alerta no dispare en la ventana entre el arranque y el primer refresco.
     */
    private final AtomicLong backingPctMillis = new AtomicLong(100_000);

    /**
     * Pre-registrados al arrancar, no en el primer evento: si esperáramos a que exista
     * un desbalance, la serie no existiría y la alerta nunca se evaluaría.
     */
    @PostConstruct
    public void registerGauges() {
        Gauge.builder(KEY_LIABILITY, keyLiabilityCents, AtomicLong::get)
                .description("Valor en centavos de todas las llaves vivas en billeteras de consumidores")
                .baseUnit("cents")
                .register(registry);

        Gauge.builder(BACKING_PCT, backingPctMillis, v -> v.get() / 1000.0)
                .description("KEYS_RESERVE como porcentaje del pasivo de llaves. Por debajo de 100 hay llaves sin respaldo")
                .register(registry);

        Gauge.builder(IDENTITY_DRIFT, identityDriftCents, AtomicLong::get)
                .description("KEYS_RESERVE menos (pasivo + saldos de anunciantes + presupuesto comprometido)")
                .baseUnit("cents")
                .register(registry);

        Gauge.builder(UNSETTLED_DELTA, unsettledDeltaCents, AtomicLong::get)
                .description("Diferencial de emisión aún sin liquidar en tesorería")
                .baseUnit("cents")
                .register(registry);

        Gauge.builder(OLDEST_UNSETTLED_AGE, oldestUnsettledEpochMs,
                        v -> v.get() == 0 ? 0 : (System.currentTimeMillis() - v.get()) / 1000.0)
                .description("Antigüedad de la interacción más vieja pendiente de liquidar")
                .baseUnit("seconds")
                .register(registry);
    }

    @Scheduled(fixedDelay = 60_000)
    public void refreshTreasuryGauges() {
        TreasurySnapshot snap = treasuryService.getSnapshot();
        KeyBacking backing = keyBackingCalculator.compute(snap.keysReserveCents());

        keyLiabilityCents.set(backing.keyLiabilityCents());
        backingPctMillis.set(Math.round(backing.backingPct() * 1000));
        identityDriftCents.set(backing.driftCents());

        ZonedDateTime cutoff = ZonedDateTime.now(clock);
        IssuanceTotals ads = adLikeRepository.sumUnsettledIssuance(cutoff);
        IssuanceTotals surveys = surveyRewardRepository.sumUnsettledIssuance(cutoff);
        unsettledDeltaCents.set(
                (ads == null ? 0 : ads.deltaCents()) + (surveys == null ? 0 : surveys.deltaCents()));

        oldestUnsettledEpochMs.set(oldestUnsettledEpochMs());
    }

    /** Epoch ms de la interacción más vieja sin liquidar. 0 = no hay ninguna. */
    private long oldestUnsettledEpochMs() {
        ZonedDateTime oldestLike = adLikeRepository.findOldestUnsettledAt();
        ZonedDateTime oldestReward = surveyRewardRepository.findOldestUnsettledAt();

        if (oldestLike == null && oldestReward == null) {
            return 0;
        }
        if (oldestLike == null) {
            return oldestReward.toInstant().toEpochMilli();
        }
        if (oldestReward == null) {
            return oldestLike.toInstant().toEpochMilli();
        }
        return Math.min(oldestLike.toInstant().toEpochMilli(), oldestReward.toInstant().toEpochMilli());
    }
}
