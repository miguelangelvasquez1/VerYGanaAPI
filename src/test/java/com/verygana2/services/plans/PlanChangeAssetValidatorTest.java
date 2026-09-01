package com.verygana2.services.plans;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.finance.plans.responses.PlanChangeBlockerDTO;
import com.verygana2.models.finance.plans.Feature;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.PlanFeature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Tests de {@link PlanChangeAssetValidator}: dado el número de activos que tiene un
 * comercial (mockeado vía {@link PlanFeatureGuard}) y el plan al que quiere cambiarse,
 * lista qué debe eliminar para que quepa. Cubre las tres formas del problema:
 * el plan destino permite menos, el plan destino no permite nada de ese tipo, y
 * el plan destino no impone límite.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanChangeAssetValidator")
class PlanChangeAssetValidatorTest {

    private static final Long COMMERCIAL_ID = 7L;

    @Mock
    private PlanFeatureGuard planFeatureGuard;

    private PlanChangeAssetValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PlanChangeAssetValidator(planFeatureGuard);
    }

    /** Activos que tiene el comercial ahora mismo. */
    private void currentAssets(long products, long ads, long brandedGames, long surveys) {
        lenient().when(planFeatureGuard.countSlotOccupyingProducts(COMMERCIAL_ID)).thenReturn(products);
        lenient().when(planFeatureGuard.countSlotOccupyingAds(COMMERCIAL_ID)).thenReturn(ads);
        lenient().when(planFeatureGuard.countSlotOccupyingBrandedGames(COMMERCIAL_ID)).thenReturn(brandedGames);
        lenient().when(planFeatureGuard.countSlotOccupyingSurveys(COMMERCIAL_ID)).thenReturn(surveys);
    }

    private PlanFeature boolFeature(String code, boolean value) {
        Feature f = new Feature();
        f.setCode(code);
        return PlanFeature.builder().feature(f).boolValue(value).build();
    }

    private PlanFeature intFeature(String code, int value) {
        Feature f = new Feature();
        f.setCode(code);
        return PlanFeature.builder().feature(f).intValue(value).build();
    }

    private Plan standardPlan() {
        List<PlanFeature> features = new ArrayList<>(List.of(
                boolFeature("CAN_SELL_DIRECTLY", true), intFeature("MAX_PRODUCTS", 50),
                boolFeature("CAN_ADVERTISE", true), intFeature("MAX_ADS", 10),
                boolFeature("CAN_USE_GAMES", true), intFeature("MAX_BRANDED_GAMES", 5),
                boolFeature("CAN_USE_SURVEYS", true), intFeature("MAX_SURVEYS", 10)));
        return Plan.builder().code(PlanCode.STANDARD).features(features).build();
    }

    private Plan premiumPlan() {
        List<PlanFeature> features = new ArrayList<>(List.of(
                boolFeature("CAN_SELL_DIRECTLY", false), intFeature("MAX_PRODUCTS", 0),
                boolFeature("CAN_ADVERTISE", true), intFeature("MAX_ADS", 50),
                boolFeature("CAN_USE_GAMES", true), intFeature("MAX_BRANDED_GAMES", 20),
                boolFeature("CAN_USE_SURVEYS", true), intFeature("MAX_SURVEYS", 50)));
        return Plan.builder().code(PlanCode.PREMIUM).features(features).build();
    }

    private Plan basicPlan() {
        List<PlanFeature> features = new ArrayList<>(List.of(
                boolFeature("CAN_SELL_DIRECTLY", true), intFeature("MAX_PRODUCTS", 10),
                boolFeature("CAN_ADVERTISE", false), intFeature("MAX_ADS", 0),
                boolFeature("CAN_USE_GAMES", false), intFeature("MAX_BRANDED_GAMES", 0),
                boolFeature("CAN_USE_SURVEYS", false), intFeature("MAX_SURVEYS", 0)));
        return Plan.builder().code(PlanCode.BASIC).features(features).build();
    }

    @Test
    @DisplayName("todo cabe en el plan destino: lista vacía")
    void everythingFits_returnsEmpty() {
        currentAssets(20, 8, 3, 5);

        assertThat(validator.findBlockers(COMMERCIAL_ID, standardPlan())).isEmpty();
    }

    @Test
    @DisplayName("PREMIUM → STANDARD: baja el máximo de anuncios y juegos, hay que esperar a que finalice el excedente")
    void premiumToStandard_reportsExcessOverLoweredLimits() {
        // STANDARD permite 10 anuncios / 5 juegos; el comercial tiene 30 / 8.
        currentAssets(20, 30, 8, 4);

        List<PlanChangeBlockerDTO> blockers = validator.findBlockers(COMMERCIAL_ID, standardPlan());

        assertThat(blockers).extracting(PlanChangeBlockerDTO::getAssetType)
                .containsExactly("ADS", "BRANDED_GAMES");
        PlanChangeBlockerDTO ads = blockers.get(0);
        assertThat(ads.getCurrentCount()).isEqualTo(30);
        assertThat(ads.getAllowedByTargetPlan()).isEqualTo(10);
        assertThat(ads.getExcessCount()).isEqualTo(20);
        assertThat(ads.getMessage())
                .contains("máximo 10").contains("tiene 30").contains("finalicen al menos 20")
                .doesNotContainIgnoringCase("elimin").doesNotContainIgnoringCase("borr");
    }

    @Test
    @DisplayName("STANDARD → PREMIUM con productos: PREMIUM no vende productos, deben finalizar todos")
    void standardToPremium_withProducts_reportsAllProductsAsExcess() {
        currentAssets(12, 5, 2, 3);

        List<PlanChangeBlockerDTO> blockers = validator.findBlockers(COMMERCIAL_ID, premiumPlan());

        assertThat(blockers).hasSize(1);
        PlanChangeBlockerDTO products = blockers.get(0);
        assertThat(products.getAssetType()).isEqualTo("PRODUCTS");
        assertThat(products.getAllowedByTargetPlan()).isZero();
        assertThat(products.getExcessCount()).isEqualTo(12);
        assertThat(products.getMessage())
                .contains("no admite productos")
                .doesNotContainIgnoringCase("elimin").doesNotContainIgnoringCase("borr");
    }

    @Test
    @DisplayName("cualquiera → BASIC: sin anuncios/juegos/encuestas y con menos productos")
    void toBasic_reportsEveryDisallowedAsset() {
        currentAssets(15, 3, 1, 2);

        List<PlanChangeBlockerDTO> blockers = validator.findBlockers(COMMERCIAL_ID, basicPlan());

        assertThat(blockers).extracting(PlanChangeBlockerDTO::getAssetType)
                .containsExactlyInAnyOrder("PRODUCTS", "ADS", "BRANDED_GAMES", "SURVEYS");
        assertThat(blockers).filteredOn(b -> b.getAssetType().equals("PRODUCTS"))
                .singleElement()
                .satisfies(b -> {
                    assertThat(b.getAllowedByTargetPlan()).isEqualTo(10);
                    assertThat(b.getExcessCount()).isEqualTo(5);
                });
    }

    @Test
    @DisplayName("límite -1 (ilimitado) en el plan destino: nunca es un bloqueo")
    void unlimitedTargetLimit_neverBlocks() {
        currentAssets(0, 9999, 0, 0);
        Plan unlimitedAds = Plan.builder().code(PlanCode.PREMIUM).features(new ArrayList<>(List.of(
                boolFeature("CAN_ADVERTISE", true), intFeature("MAX_ADS", -1)))).build();

        assertThat(validator.findBlockers(COMMERCIAL_ID, unlimitedAds)).isEmpty();
    }

    @Test
    @DisplayName("exactamente en el límite del plan destino: no es un bloqueo")
    void exactlyAtLimit_doesNotBlock() {
        currentAssets(50, 10, 5, 10);

        assertThat(validator.findBlockers(COMMERCIAL_ID, standardPlan())).isEmpty();
    }

    @Test
    @DisplayName("VISIBILITY_BOOST u otras features irrelevantes no afectan el cálculo")
    void unrelatedFeatures_ignored() {
        currentAssets(0, 0, 0, 0);
        Plan plan = standardPlan();
        Feature boost = new Feature();
        boost.setCode("VISIBILITY_BOOST");
        plan.getFeatures().add(PlanFeature.builder().feature(boost).decimalValue(new BigDecimal("30.00")).build());

        assertThat(validator.findBlockers(COMMERCIAL_ID, plan)).isEmpty();
    }
}
