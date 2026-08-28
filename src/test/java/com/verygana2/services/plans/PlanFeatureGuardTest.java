package com.verygana2.services.plans;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.finance.plans.EffectivePlanState;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.RequirePlanCapability.Capability;
import com.verygana2.models.surveys.Survey.SurveyStatus;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.surveys.SurveyRepository;
import com.verygana2.services.plans.PlanFeatureGuard.BudgetSuspendedException;
import com.verygana2.services.plans.PlanFeatureGuard.PlanCapabilityException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PlanFeatureGuard}: cada una de las 11 capacidades del switch en
 * assertCapability (booleanas directas del estado efectivo, y numéricas que cuentan
 * uso actual vía repositorio contra el máximo del plan), más assertBudgetAvailable.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanFeatureGuard")
class PlanFeatureGuardTest {

    @Mock private EffectivePlanResolver planResolver;
    @Mock private ProductRepository productRepository;
    @Mock private AdRepository adRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private BrandingRequestRepository brandingRequestRepository;
    @Mock private SurveyRepository surveyRepository;

    private PlanFeatureGuard guard;

    private static final Long COMMERCIAL_ID = 1L;

    @BeforeEach
    void setUp() {
        guard = new PlanFeatureGuard(planResolver, productRepository, adRepository, campaignRepository,
                brandingRequestRepository, surveyRepository);
    }

    private EffectivePlanState.EffectivePlanStateBuilder baseState() {
        return EffectivePlanState.builder()
                .hasActivePlan(true)
                .effectivePlan(PlanCode.STANDARD)
                .commissionActive(true)
                .commissionRate(BigDecimal.TEN)
                .remainingBudget(BigDecimal.valueOf(1000))
                .canAdvertise(true)
                .canUseGames(true)
                .canUseSurveys(true)
                .canSellDirectly(true)
                .canHavePets(true)
                .canPromoteAllyProducts(true)
                .canExportReport(true)
                .budgetSuspended(false)
                .maxProducts(5)
                .maxAds(5)
                .maxBrandedGames(5)
                .maxSurveys(5)
                .visibilityBoostPct(BigDecimal.ZERO);
    }

    private void mockState(EffectivePlanState state) {
        when(planResolver.resolve(COMMERCIAL_ID)).thenReturn(state);
    }

    // ─── Booleanas directas ─────────────────────────────────────────────────

    @Nested
    @DisplayName("capacidades booleanas directas")
    class BooleanCapabilities {

        @Test
        @DisplayName("CAN_ADVERTISE: pasa si true, lanza si false")
        void canAdvertise() {
            mockState(baseState().canAdvertise(true).build());
            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_ADVERTISE)).doesNotThrowAnyException();

            mockState(baseState().canAdvertise(false).build());
            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_ADVERTISE))
                    .isInstanceOf(PlanCapabilityException.class);
        }

        @Test
        @DisplayName("CAN_USE_GAMES: pasa si true, lanza si false")
        void canUseGames() {
            mockState(baseState().canUseGames(true).build());
            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_USE_GAMES)).doesNotThrowAnyException();

            mockState(baseState().canUseGames(false).build());
            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_USE_GAMES))
                    .isInstanceOf(PlanCapabilityException.class);
        }

        @Test
        @DisplayName("CAN_USE_SURVEYS: pasa si true, lanza si false")
        void canUseSurveys() {
            mockState(baseState().canUseSurveys(true).build());
            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_USE_SURVEYS)).doesNotThrowAnyException();

            mockState(baseState().canUseSurveys(false).build());
            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_USE_SURVEYS))
                    .isInstanceOf(PlanCapabilityException.class);
        }

        @Test
        @DisplayName("CAN_SELL_DIRECTLY: pasa si true, lanza si false")
        void canSellDirectly() {
            mockState(baseState().canSellDirectly(true).build());
            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_SELL_DIRECTLY)).doesNotThrowAnyException();

            mockState(baseState().canSellDirectly(false).build());
            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_SELL_DIRECTLY))
                    .isInstanceOf(PlanCapabilityException.class);
        }

        @Test
        @DisplayName("CAN_HAVE_PETS: pasa si true, lanza si false")
        void canHavePets() {
            mockState(baseState().canHavePets(true).build());
            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_HAVE_PETS)).doesNotThrowAnyException();

            mockState(baseState().canHavePets(false).build());
            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_HAVE_PETS))
                    .isInstanceOf(PlanCapabilityException.class);
        }

        @Test
        @DisplayName("CAN_PROMOTE_ALLY_PRODUCTS: pasa si true, lanza si false")
        void canPromoteAllyProducts() {
            mockState(baseState().canPromoteAllyProducts(true).build());
            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_PROMOTE_ALLY_PRODUCTS)).doesNotThrowAnyException();

            mockState(baseState().canPromoteAllyProducts(false).build());
            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_PROMOTE_ALLY_PRODUCTS))
                    .isInstanceOf(PlanCapabilityException.class);
        }

        @Test
        @DisplayName("CAN_EXPORT_REPORT: pasa si true, lanza si false")
        void canExportReport() {
            mockState(baseState().canExportReport(true).build());
            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_EXPORT_REPORT)).doesNotThrowAnyException();

            mockState(baseState().canExportReport(false).build());
            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_EXPORT_REPORT))
                    .isInstanceOf(PlanCapabilityException.class);
        }
    }

    // ─── Numéricas por conteo ───────────────────────────────────────────────

    @Nested
    @DisplayName("capacidades numéricas (conteo actual vs máximo)")
    class NumericCapabilities {

        @Test
        @DisplayName("MAX_PRODUCTS: conteo < máximo pasa; conteo == máximo lanza")
        void maxProducts() {
            mockState(baseState().maxProducts(5).build());
            when(productRepository.countByCommercialIdAndIsActive(COMMERCIAL_ID)).thenReturn(4L);
            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.MAX_PRODUCTS)).doesNotThrowAnyException();

            when(productRepository.countByCommercialIdAndIsActive(COMMERCIAL_ID)).thenReturn(5L);
            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.MAX_PRODUCTS))
                    .isInstanceOf(PlanCapabilityException.class);
        }

        @Test
        @DisplayName("MAX_ADS: conteo < máximo pasa; conteo == máximo lanza")
        void maxAds() {
            mockState(baseState().maxAds(5).build());
            when(adRepository.countByCommercialIdAndStatus(COMMERCIAL_ID, AdStatus.ACTIVE)).thenReturn(4L);
            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.MAX_ADS)).doesNotThrowAnyException();

            when(adRepository.countByCommercialIdAndStatus(COMMERCIAL_ID, AdStatus.ACTIVE)).thenReturn(5L);
            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.MAX_ADS))
                    .isInstanceOf(PlanCapabilityException.class);
        }

        @Test
        @DisplayName("MAX_SURVEYS: conteo < máximo pasa; conteo == máximo lanza")
        void maxSurveys() {
            mockState(baseState().maxSurveys(5).build());
            when(surveyRepository.countByCreatorIdAndStatusNotIn(eq(COMMERCIAL_ID),
                    eq(List.of(SurveyStatus.REJECTED, SurveyStatus.COMPLETED)))).thenReturn(4L);
            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.MAX_SURVEYS)).doesNotThrowAnyException();

            when(surveyRepository.countByCreatorIdAndStatusNotIn(eq(COMMERCIAL_ID),
                    eq(List.of(SurveyStatus.REJECTED, SurveyStatus.COMPLETED)))).thenReturn(5L);
            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.MAX_SURVEYS))
                    .isInstanceOf(PlanCapabilityException.class);
        }

        @Test
        @DisplayName("MAX_BRANDED_GAMES: suma campañas no finalizadas + solicitudes de branding activas contra el máximo")
        void maxBrandedGames() {
            mockState(baseState().maxBrandedGames(5).build());
            when(campaignRepository.countByCommercialIdAndStatusNotIn(eq(COMMERCIAL_ID),
                    eq(List.of(CampaignStatus.COMPLETED, CampaignStatus.CANCELLED)))).thenReturn(2L);
            when(brandingRequestRepository.countByCommercial_User_IdAndStatusNotIn(eq(COMMERCIAL_ID),
                    eq(List.of(BrandingRequestStatus.REJECTED, BrandingRequestStatus.CANCELLED,
                            BrandingRequestStatus.CAMPAIGN_CREATED)))).thenReturn(2L);
            // 2 + 2 = 4 < 5 → pasa
            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.MAX_BRANDED_GAMES)).doesNotThrowAnyException();

            when(brandingRequestRepository.countByCommercial_User_IdAndStatusNotIn(eq(COMMERCIAL_ID),
                    eq(List.of(BrandingRequestStatus.REJECTED, BrandingRequestStatus.CANCELLED,
                            BrandingRequestStatus.CAMPAIGN_CREATED)))).thenReturn(3L);
            // 2 + 3 = 5 == 5 → lanza
            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.MAX_BRANDED_GAMES))
                    .isInstanceOf(PlanCapabilityException.class);
        }
    }

    // ─── assertBudgetAvailable ──────────────────────────────────────────────

    @Nested
    @DisplayName("assertBudgetAvailable")
    class AssertBudgetAvailable {

        @Test
        @DisplayName("budgetSuspended == true: lanza BudgetSuspendedException")
        void budgetSuspended_throwsBudgetSuspendedException() {
            mockState(baseState().budgetSuspended(true).build());

            assertThatThrownBy(() -> guard.assertBudgetAvailable(COMMERCIAL_ID))
                    .isInstanceOf(BudgetSuspendedException.class)
                    .isInstanceOf(PlanCapabilityException.class);
        }

        @Test
        @DisplayName("budgetSuspended == false: no lanza")
        void budgetNotSuspended_doesNotThrow() {
            mockState(baseState().budgetSuspended(false).build());

            assertThatCode(() -> guard.assertBudgetAvailable(COMMERCIAL_ID)).doesNotThrowAnyException();
        }
    }
}
