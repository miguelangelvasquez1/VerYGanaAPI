package com.verygana2.models.finance.plans;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.models.finance.plans.Plan.PlanCode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la entidad {@link Plan}: lectura de features dinámicas
 * (booleanas/de límite) con su valor por defecto cuando la feature no está
 * configurada para ese plan.
 */
@DisplayName("Plan (entidad)")
class PlanTest {

    private Feature feature(String code) {
        Feature f = new Feature();
        f.setCode(code);
        return f;
    }

    @Test
    @DisplayName("isMonthlySubscription: true solo para BASIC")
    void isMonthlySubscription_trueOnlyForBasic() {
        assertThat(Plan.builder().code(PlanCode.BASIC).build().isMonthlySubscription()).isTrue();
        assertThat(Plan.builder().code(PlanCode.STANDARD).build().isMonthlySubscription()).isFalse();
        assertThat(Plan.builder().code(PlanCode.PREMIUM).build().isMonthlySubscription()).isFalse();
    }

    @Test
    @DisplayName("getBoolFeature: retorna el valor configurado si existe la feature")
    void getBoolFeature_returnsConfiguredValue() {
        PlanFeature pf = PlanFeature.builder().feature(feature("CAN_ADVERTISE")).boolValue(true).build();
        Plan plan = Plan.builder().features(List.of(pf)).build();

        assertThat(plan.getBoolFeature("CAN_ADVERTISE", false)).isTrue();
    }

    @Test
    @DisplayName("getBoolFeature: retorna el default si la feature no está configurada para este plan")
    void getBoolFeature_returnsDefaultWhenMissing() {
        Plan plan = Plan.builder().features(List.of()).build();

        assertThat(plan.getBoolFeature("CAN_USE_GAMES", false)).isFalse();
        assertThat(plan.getBoolFeature("CAN_USE_GAMES", true)).isTrue();
    }

    @Test
    @DisplayName("getIntFeature: retorna el valor configurado, o el default si falta")
    void getIntFeature_returnsConfiguredOrDefault() {
        PlanFeature pf = PlanFeature.builder().feature(feature("MAX_PRODUCTS")).intValue(50).build();
        Plan plan = Plan.builder().features(List.of(pf)).build();

        assertThat(plan.getIntFeature("MAX_PRODUCTS", 10)).isEqualTo(50);
        assertThat(plan.getIntFeature("MAX_ADS", 5)).isEqualTo(5);
    }
}
