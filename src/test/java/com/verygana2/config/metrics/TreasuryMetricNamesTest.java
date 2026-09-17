package com.verygana2.config.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.verygana2.models.records.IssuanceTotals;
import com.verygana2.models.records.KeyBacking;
import com.verygana2.models.records.TreasurySnapshot;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.surveys.SurveyRewardRepository;
import com.verygana2.services.finance.KeyBackingCalculator;
import com.verygana2.services.interfaces.finance.TreasuryService;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Fija los nombres que {@link TreasuryMetrics} expone en {@code /actuator/prometheus}.
 *
 * Hermano de {@link PayoutMetricNamesTest} y por la misma razón: los nombres traducidos
 * (punto → guion bajo, sufijo de unidad en gauges) están escritos a mano en
 * {@code monitoring/prometheus/alert.rules.yml}, que vive fuera del compilador. Renombrar
 * una constante de arriba no rompe la compilación: rompe, en silencio, la alerta que avisa
 * que hay llaves emitidas sin respaldo.
 *
 * El segundo test cierra el círculo en la otra dirección: lee las reglas reales y exige que
 * cada métrica que nombran exista de verdad en el scrapeo. Así una regla escrita contra un
 * nombre inventado tampoco pasa.
 */
@DisplayName("nombres Prometheus de las métricas de tesorería")
class TreasuryMetricNamesTest {

    private static final Path RULES = Path.of("monitoring/prometheus/alert.rules.yml");

    private PrometheusMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        TreasuryService treasuryService = Mockito.mock(TreasuryService.class);
        KeyBackingCalculator calculator = Mockito.mock(KeyBackingCalculator.class);
        AdLikeRepository adLikeRepository = Mockito.mock(AdLikeRepository.class);
        SurveyRewardRepository surveyRewardRepository = Mockito.mock(SurveyRewardRepository.class);

        when(treasuryService.getSnapshot())
                .thenReturn(new TreasurySnapshot(10_000L, 0L, 0L, 0L, 10_000L));
        when(calculator.compute(anyLong()))
                .thenReturn(new KeyBacking(10_000L, 8_000L, 1_000L, 500L, 200L, 200L, 100L));
        when(adLikeRepository.sumUnsettledIssuance(any())).thenReturn(new IssuanceTotals(1_000L, 700L));
        when(surveyRewardRepository.sumUnsettledIssuance(any())).thenReturn(new IssuanceTotals(0L, 0L));

        TreasuryMetrics metrics = new TreasuryMetrics(
                registry, treasuryService, calculator,
                adLikeRepository, surveyRewardRepository,
                Clock.fixed(Instant.parse("2026-09-16T10:00:00Z"), ZoneOffset.UTC));

        metrics.registerGauges();
        metrics.refreshTreasuryGauges();
    }

    @Test
    @DisplayName("los cinco gauges se exponen con el nombre exacto que usan las alertas")
    void gaugeNames() {
        assertThat(scrape()).contains(
                "treasury_keys_backing_pct",
                "treasury_key_liability_cents",
                "treasury_identity_drift_cents",
                "key_issuance_unsettled_cents",
                "key_issuance_oldest_unsettled_age_seconds");
    }

    @Test
    @DisplayName("toda métrica de tesorería nombrada en alert.rules.yml existe en el scrapeo")
    void alertRulesReferenceExistingMetrics() throws IOException {
        String scrape = scrape();

        // Cualquier token con el prefijo de este módulo que aparezca en las reglas.
        Matcher m = Pattern.compile("\\b((?:treasury|key_issuance)_[a-z0-9_]+)\\b")
                .matcher(Files.readString(RULES));

        Set<String> referenced = m.results()
                .map(r -> r.group(1))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        // Si esto falla, o alguien renombró un gauge sin tocar las reglas, o escribió una
        // regla contra una métrica que no existe. Las dos rompen la alerta en silencio.
        assertThat(referenced)
                .as("las reglas deben nombrar al menos las métricas de solvencia")
                .contains("treasury_keys_backing_pct", "treasury_identity_drift_cents");

        assertThat(referenced).allSatisfy(metric ->
                assertThat(scrape)
                        .as("alert.rules.yml usa '%s' pero TreasuryMetrics no lo expone", metric)
                        .contains(metric));
    }

    @Test
    @DisplayName("sin etiquetas propias: una serie por gauge, cardinalidad fija")
    void boundedCardinality() {
        // Estas métricas son totales de plataforma, no por usuario ni por comercial.
        // Una etiqueta con consumerId o commercialId multiplicaría las series sin techo.
        List<String> series = scrape().lines()
                .filter(l -> !l.startsWith("#"))
                .filter(l -> l.startsWith("treasury_") || l.startsWith("key_issuance_"))
                .toList();

        assertThat(series).hasSize(5);
        assertThat(series).noneMatch(l -> l.matches(".*(consumer|commercial|wallet)_?id=.*"));
    }

    private String scrape() {
        return registry.scrape();
    }
}
