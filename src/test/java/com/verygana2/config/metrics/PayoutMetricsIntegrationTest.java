package com.verygana2.config.metrics;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.User;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.finance.Payout;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.PayoutRepository;
import com.verygana2.repositories.finance.PayoutRepository.PayoutStatusAggregate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo único que los tests con Mockito no pueden probar: que la JPQL de
 * {@link PayoutRepository#aggregateByStatus} existe, compila en Hibernate y que sus alias
 * se enlazan de verdad con los getters de la proyección. Un mock del repositorio devuelve
 * lo que uno le diga aunque la consulta esté mal escrita — este test corre contra H2.
 */
@DataJpaTest(properties = {
        // Perfil vacío: evita cargar application-dev.yml (llaves RSA, R2, etc.)
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:payout-metrics-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PayoutMetrics (integración H2)")
class PayoutMetricsIntegrationTest {

    @Autowired private PayoutRepository payoutRepository;
    @Autowired private EntityManager em;

    private PayoutMetrics metrics;
    private SimpleMeterRegistry registry;
    private long seq = 1;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new PayoutMetrics(registry, payoutRepository);
        metrics.registerGauges();
    }

    private CommercialDetails persistCommercial() {
        long n = seq++;
        User user = new User();
        user.setEmail("empresario" + n + "@test.com");
        user.setPhoneNumber("310000" + String.format("%04d", n));
        user.setPassword("hash");
        user.setRole(Role.COMMERCIAL);
        user.setUserState(UserState.ACTIVE);
        user.setRegisteredDate(ZonedDateTime.now());
        em.persist(user);

        CommercialDetails commercial = new CommercialDetails();
        commercial.setUser(user);
        commercial.setCompanyName("Tienda " + n);
        commercial.setNit("900000" + n);
        em.persist(commercial);
        return commercial;
    }

    private Payout persistPayout(PayoutStatus status, long netCents, ZonedDateTime scheduledAt) {
        Payout payout = Payout.builder()
                .commercial(persistCommercial())
                .grossAmountCents(netCents + 10_000L)
                .commissionCents(10_000L)
                .netAmountCents(netCents)
                .commissionPctApplied(10)
                .status(status)
                .scheduledAt(scheduledAt)
                .periodStart(scheduledAt.minusDays(1))
                .periodEnd(scheduledAt)
                .build();
        em.persist(payout);
        return payout;
    }

    @Test
    @DisplayName("la consulta agregada devuelve conteo, monto y el más viejo por estado")
    void aggregateByStatus_returnsProjectedColumns() {
        ZonedDateTime ahora = ZonedDateTime.now(ZoneOffset.UTC);
        persistPayout(PayoutStatus.SCHEDULED, 90_000L, ahora.minusDays(3));
        persistPayout(PayoutStatus.SCHEDULED, 60_000L, ahora.minusDays(1));
        persistPayout(PayoutStatus.PROCESSING, 25_000L, ahora.minusHours(5));
        persistPayout(PayoutStatus.PAID, 999_000L, ahora.minusDays(10));   // no debe aparecer
        em.flush();

        List<PayoutStatusAggregate> filas = payoutRepository.aggregateByStatus(
                List.of(PayoutStatus.SCHEDULED, PayoutStatus.PROCESSING, PayoutStatus.FAILED));

        assertThat(filas).hasSize(2);

        PayoutStatusAggregate scheduled = filas.stream()
                .filter(f -> f.getStatus() == PayoutStatus.SCHEDULED).findFirst().orElseThrow();
        assertThat(scheduled.getTotal()).isEqualTo(2L);
        assertThat(scheduled.getNetCents()).isEqualTo(150_000L);
        // MIN(scheduledAt): el más viejo de los dos, no el más reciente.
        assertThat(scheduled.getOldestScheduledAt()).isCloseTo(ahora.minusDays(3),
                within(2, java.time.temporal.ChronoUnit.SECONDS));

        // PAID queda fuera del filtro: es el estado terminal sano.
        assertThat(filas).noneMatch(f -> f.getStatus() == PayoutStatus.PAID);
    }

    @Test
    @DisplayName("el refresco de gauges traduce el agregado a las series de Prometheus")
    void refreshPayoutGauges_populatesSeries() {
        ZonedDateTime ahora = ZonedDateTime.now(ZoneOffset.UTC);
        persistPayout(PayoutStatus.PROCESSING, 25_000L, ahora.minusDays(2));
        em.flush();

        metrics.refreshPayoutGauges();

        assertThat(gauge("payout.pending.count", "PROCESSING")).isEqualTo(1d);
        assertThat(gauge("payout.pending.amount.cents", "PROCESSING")).isEqualTo(25_000d);
        // ~2 días esperando el webhook de Wompi que nunca llegó.
        assertThat(gauge("payout.oldest.age", "PROCESSING")).isGreaterThan(2 * 24 * 3600 - 60d);

        // Los estados sin filas existen y valen 0: si no existieran, la alerta que los
        // consulta nunca se evaluaría.
        assertThat(gauge("payout.pending.count", "SCHEDULED")).isZero();
        assertThat(gauge("payout.pending.count", "FAILED")).isZero();
        assertThat(gauge("payout.oldest.age", "SCHEDULED")).isZero();
    }

    @Test
    @DisplayName("sin payouts pendientes todas las series quedan en cero, no ausentes")
    void refreshPayoutGauges_withNoRows_keepsSeriesAtZero() {
        metrics.refreshPayoutGauges();

        for (String status : List.of("SCHEDULED", "PROCESSING", "FAILED")) {
            assertThat(gauge("payout.pending.count", status)).isZero();
            assertThat(gauge("payout.pending.amount.cents", status)).isZero();
            assertThat(gauge("payout.oldest.age", status)).isZero();
        }
    }

    private double gauge(String name, String status) {
        return registry.get(name).tag("status", status).gauge().value();
    }

    private static org.assertj.core.data.TemporalUnitOffset within(
            long amount, java.time.temporal.TemporalUnit unit) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(amount, unit);
    }
}
