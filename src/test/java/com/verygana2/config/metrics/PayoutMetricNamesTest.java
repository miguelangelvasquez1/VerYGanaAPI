package com.verygana2.config.metrics;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.verygana2.repositories.finance.PayoutRepository;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fija los nombres que {@link PayoutMetrics} expone en {@code /actuator/prometheus}.
 *
 * No es un test de ceremonia: los nombres traducidos (punto → guion bajo, sufijo {@code _total}
 * en contadores, sufijo de unidad en gauges) están escritos a mano en las reglas de
 * {@code monitoring/prometheus/alert.rules.yml} y en los paneles de los dashboards de Grafana,
 * que viven fuera del compilador. Renombrar una constante de arriba no rompe la compilación:
 * rompe, en silencio, la alerta que avisa que el dinero no salió. Este test es lo único que
 * conecta las dos mitades.
 */
@DisplayName("nombres Prometheus de las métricas de payouts")
class PayoutMetricNamesTest {

    private PrometheusMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        PayoutMetrics metrics = new PayoutMetrics(registry, Mockito.mock(PayoutRepository.class));
        metrics.registerGauges();

        // Un evento de cada tipo: un contador no aparece en el scrapeo hasta que se registra.
        metrics.payoutScheduled(90_000L);
        metrics.payoutWithoutMethod();
        metrics.payoutSent(true);
        metrics.payoutSent(false);
        metrics.payoutRetried();
        metrics.payoutProcessingFailed("PROCESS", new IllegalStateException("sin método"));
        metrics.payoutConfirmed(true, 90_000L, "none");
        metrics.payoutConfirmed(false, 50_000L, "DECLINED");
        metrics.wompiBalanceRead(7_500_000L);
    }

    @Test
    @DisplayName("los gauges de estado se exponen con sus etiquetas de status")
    void gaugeNames() {
        assertThat(scrape()).contains(
                "payout_pending_count{status=\"SCHEDULED\"}",
                "payout_pending_count{status=\"PROCESSING\"}",
                "payout_pending_count{status=\"FAILED\"}",
                "payout_pending_amount_cents{status=\"SCHEDULED\"}",
                "payout_oldest_age_seconds{status=\"PROCESSING\"}",
                "wompi_payout_balance_cents",
                "wompi_payout_balance_age_seconds");
    }

    @Test
    @DisplayName("los contadores llevan sufijo _total y las etiquetas que usan las alertas")
    void counterNames() {
        assertThat(scrape()).contains(
                "payout_scheduled_total",
                "payout_scheduled_amount_cents_total",
                "payout_without_method_total",
                "payout_retried_total",
                "payout_paid_amount_cents_total",
                "payout_sent_total{outcome=\"ACCEPTED\"}",
                "payout_sent_total{outcome=\"REJECTED\"}",
                "payout_confirmed_total{outcome=\"PAID\",reason=\"none\"}",
                "payout_confirmed_total{outcome=\"FAILED\",reason=\"DECLINED\"}",
                "payout_processing_errors_total{exception=\"IllegalStateException\",phase=\"PROCESS\"}");
    }

    @Test
    @DisplayName("la cardinalidad se mantiene acotada: nada de identificadores en las etiquetas")
    void boundedCardinality() {
        // status, outcome, reason, phase y exception son enums o constantes del código. Si
        // alguna vez se etiqueta con un payoutId o con failureReason (texto libre de Wompi),
        // la serie se multiplica sin techo y tumba a Prometheus.
        List<String> series = scrape().lines()
                .filter(l -> !l.startsWith("#"))
                .filter(l -> l.startsWith("payout") || l.startsWith("wompi"))
                .toList();

        // 11 gauges (3 estados x 3 series + balance + antigüedad del balance) + 10 contadores,
        // contando las dos variantes de payout_sent y las dos de payout_confirmed.
        assertThat(series).hasSize(21);
        assertThat(series).noneMatch(l -> l.matches(".*[0-9a-f]{8}-[0-9a-f]{4}-.*"));   // UUIDs
    }

    private String scrape() {
        return registry.scrape();
    }
}
