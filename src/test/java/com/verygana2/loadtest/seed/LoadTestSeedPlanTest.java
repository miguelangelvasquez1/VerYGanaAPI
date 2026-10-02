package com.verygana2.loadtest.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoadTestSeedPlan - mezcla de roles y volumen")
class LoadTestSeedPlanTest {

    @Test
    @DisplayName("escenario A: 940 consumidores, 50 comerciales, 5 diseñadores, 3 admins, 2 de cumplimiento")
    void scenarioAHasRoleMix() {
        LoadTestSeedPlan plan = LoadTestSeedPlan.forTotalUsers(1_000);

        assertThat(plan.consumers()).isEqualTo(940);
        assertThat(plan.commercials()).isEqualTo(50);
        assertThat(plan.designers()).isEqualTo(5);
        assertThat(plan.admins()).isEqualTo(3);
        assertThat(plan.complianceOfficers()).isEqualTo(2);
    }

    @Test
    @DisplayName("escenario B: 9.490 / 500 / 5 / 3 / 2")
    void scenarioBHasRoleMix() {
        LoadTestSeedPlan plan = LoadTestSeedPlan.forTotalUsers(10_000);

        assertThat(plan.consumers()).isEqualTo(9_490);
        assertThat(plan.commercials()).isEqualTo(500);
        assertThat(plan.designers()).isEqualTo(5);
        assertThat(plan.admins()).isEqualTo(3);
        assertThat(plan.complianceOfficers()).isEqualTo(2);
    }

    @Test
    @DisplayName("el personal interno es el mismo en A y en B")
    void internalStaffIsFixedInBothScenarios() {
        LoadTestSeedPlan a = LoadTestSeedPlan.forTotalUsers(1_000);
        LoadTestSeedPlan b = LoadTestSeedPlan.forTotalUsers(10_000);

        assertThat(b.designers()).isEqualTo(a.designers());
        assertThat(b.admins()).isEqualTo(a.admins());
        assertThat(b.complianceOfficers()).isEqualTo(a.complianceOfficers());
        assertThat(a.internalStaff()).isEqualTo(10);
    }

    @Test
    @DisplayName("el historial por consumidor y por comercial es el mismo en A y en B")
    void scenarioBHistoryIsProportionalToA() {
        LoadTestSeedPlan a = LoadTestSeedPlan.forTotalUsers(1_000);
        LoadTestSeedPlan b = LoadTestSeedPlan.forTotalUsers(10_000);

        // Mismos multiplicadores: lo que cambia es cuántos dueños hay.
        assertThat(b.multipliers()).isEqualTo(a.multipliers());

        Map<String, Long> rowsA = a.expectedHistoryRows();
        Map<String, Long> rowsB = b.expectedHistoryRows();
        assertThat(rowsB.keySet()).isEqualTo(rowsA.keySet());
        // Filas por consumidor: ad_watch_session
        long perConsumer = a.multipliers().get("ad_watch_sessions");
        assertThat(rowsA.get("ad_watch_session")).isEqualTo(perConsumer * 940);
        assertThat(rowsB.get("ad_watch_session")).isEqualTo(perConsumer * 9_490);
        // Filas por comercial: ads
        long adsPerCommercial = a.multipliers().get("ads");
        assertThat(rowsA.get("ads")).isEqualTo(adsPerCommercial * 50);
        assertThat(rowsB.get("ads")).isEqualTo(adsPerCommercial * 500);
        // Y son del orden que dice el plan: ~450 filas por consumidor
        assertThat(rowsB.values().stream().mapToLong(Long::longValue).sum()).isBetween(3_500_000L, 6_000_000L);
    }

    @Test
    @DisplayName("los roles siempre suman el total")
    void rolesSumToTotal() {
        for (int total : new int[] {10, 11, 50, 200, 333, 999, 1_000, 1_234, 2_000, 9_999, 10_000, 25_000}) {
            LoadTestSeedPlan plan = LoadTestSeedPlan.forTotalUsers(total);
            assertThat(plan.consumers() + plan.commercials() + plan.designers() + plan.admins()
                    + plan.complianceOfficers()).as("total %d", total).isEqualTo(total);
        }
    }

    @Test
    @DisplayName("un total menor al personal interno es un error de configuración")
    void totalBelowInternalStaffIsRejected() {
        assertThatThrownBy(() -> LoadTestSeedPlan.forTotalUsers(5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la mezcla de VUs sigue la misma regla (200 y 2.000)")
    void vuMixFollowsSameRule() {
        LoadTestSeedPlan vus200 = LoadTestSeedPlan.forTotalUsers(200);
        assertThat(new int[] {vus200.consumers(), vus200.commercials(), vus200.designers(), vus200.admins(),
                vus200.complianceOfficers()}).containsExactly(180, 10, 5, 3, 2);

        LoadTestSeedPlan vus2000 = LoadTestSeedPlan.forTotalUsers(2_000);
        assertThat(new int[] {vus2000.consumers(), vus2000.commercials(), vus2000.designers(), vus2000.admins(),
                vus2000.complianceOfficers()}).containsExactly(1_890, 100, 5, 3, 2);
    }

    @Test
    @DisplayName("comerciales 40/40/20 BASIC/STANDARD/PREMIUM (A: 20/20/10; B: 200/200/100)")
    void commercialPlansSplit40_40_20() {
        LoadTestSeedPlan a = LoadTestSeedPlan.forTotalUsers(1_000);
        assertThat(a.commercialsByPlan()).containsEntry("BASIC", 20).containsEntry("STANDARD", 20)
                .containsEntry("PREMIUM", 10);

        LoadTestSeedPlan b = LoadTestSeedPlan.forTotalUsers(10_000);
        assertThat(b.commercialsByPlan()).containsEntry("BASIC", 200).containsEntry("STANDARD", 200)
                .containsEntry("PREMIUM", 100);
    }

    @Test
    @DisplayName("el plan de un comercial depende solo de su número: al crecer de A a B no cambia")
    void planOfACommercialIsStableWhenGrowing() {
        // Si dependiera de rangos (primeros 40 % BASIC...), el comercial 21 sería STANDARD en A
        // y BASIC en B, y la siembra incremental dejaría la mezcla torcida.
        for (int n = 1; n <= 50; n++) {
            assertThat(LoadTestSeedPlan.planOfCommercial(n)).isEqualTo(LoadTestSeedPlan.planOfCommercial(n + 50 * 5));
        }
    }

    @Test
    @DisplayName("las variables de sesión llevan los conteos y los multiplicadores del plan")
    void sessionVariablesCarryCountsAndMultipliers() {
        Map<String, Object> vars = LoadTestSeedPlan.forTotalUsers(1_000).sessionVariables();

        assertThat(vars).containsEntry("lt_total_users", 1_000)
                .containsEntry("lt_consumers", 940)
                .containsEntry("lt_commercials", 50)
                .containsEntry("lt_designers", 5)
                .containsEntry("lt_admins", 3)
                .containsEntry("lt_compliance", 2)
                .containsKey("lt_m_ad_watch_sessions");
    }

    @Test
    @DisplayName("el fondeo de tesorería sale de multiplicadores del plan, así que B es proporcional a A")
    void treasuryFundingMultipliersAreProvidedToScripts() {
        Map<String, Object> vars = LoadTestSeedPlan.forTotalUsers(1_000).sessionVariables();

        assertThat(vars).containsKeys("lt_m_treasury_keys_reserve_cents_per_consumer",
                "lt_m_treasury_payouts_pending_cents_per_commercial",
                "lt_m_treasury_operations_cents_per_commercial", "lt_m_impact_stories");
        // El saldo de llaves cubre el máximo que el sembrado le da a un consumidor (5.000.000 centavos)
        assertThat((Integer) vars.get("lt_m_treasury_keys_reserve_cents_per_consumer"))
                .isGreaterThanOrEqualTo(5_000_000);
    }
}
