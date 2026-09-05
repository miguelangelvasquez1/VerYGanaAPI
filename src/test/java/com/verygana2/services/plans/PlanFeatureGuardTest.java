package com.verygana2.services.plans;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.commercial.PlanChangeRequest;
import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;
import com.verygana2.models.finance.plans.EffectivePlanState;
import com.verygana2.models.finance.plans.RequirePlanCapability.Capability;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.commercial.PlanChangeRequestRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.surveys.SurveyRepository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PlanFeatureGuard#assertNoOpenPlanChangeRequest}: bloquea la
 * creación/reactivación de activos mientras haya una solicitud de cambio de plan
 * sin resolver.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanFeatureGuard")
class PlanFeatureGuardTest {

    private static final Long COMMERCIAL_ID = 42L;

    @Mock private EffectivePlanResolver planResolver;
    @Mock private ProductRepository productRepository;
    @Mock private AdRepository adRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private BrandingRequestRepository brandingRequestRepository;
    @Mock private SurveyRepository surveyRepository;
    @Mock private PlanChangeRequestRepository planChangeRequestRepository;

    @InjectMocks private PlanFeatureGuard guard;

    @Nested
    @DisplayName("assertNoOpenPlanChangeRequest")
    class AssertNoOpenPlanChangeRequest {

        @Test
        @DisplayName("sin solicitudes en curso: no lanza")
        void noOpenRequest_passes() {
            when(planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(eq(COMMERCIAL_ID), any()))
                    .thenReturn(List.of());

            assertThatCode(() -> guard.assertNoOpenPlanChangeRequest(COMMERCIAL_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("con una solicitud en curso: lanza PlanCapabilityException")
        void openRequest_throws() {
            when(planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(eq(COMMERCIAL_ID), any()))
                    .thenReturn(List.of(requestInStatus(PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW)));

            assertThatThrownBy(() -> guard.assertNoOpenPlanChangeRequest(COMMERCIAL_ID))
                    .isInstanceOf(PlanFeatureGuard.PlanCapabilityException.class)
                    .hasMessageContaining("cambio de plan en curso");
        }

        private PlanChangeRequest requestInStatus(PlanChangeRequestStatus status) {
            PlanChangeRequest r = new PlanChangeRequest();
            r.setStatus(status);
            return r;
        }
    }

    @Nested
    @DisplayName("assertCapability — métricas de rendimiento / remisión")
    class AssertMetricsCapabilities {

        @Test
        @DisplayName("CAN_VIEW_PERFORMANCE_METRICS con el flag activo: no lanza")
        void performanceMetrics_allowed() {
            when(planResolver.resolve(COMMERCIAL_ID))
                    .thenReturn(EffectivePlanState.builder().canViewPerformanceMetrics(true).build());

            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_VIEW_PERFORMANCE_METRICS))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("CAN_VIEW_PERFORMANCE_METRICS con el flag inactivo: lanza PlanCapabilityException")
        void performanceMetrics_denied() {
            when(planResolver.resolve(COMMERCIAL_ID))
                    .thenReturn(EffectivePlanState.builder().canViewPerformanceMetrics(false).build());

            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_VIEW_PERFORMANCE_METRICS))
                    .isInstanceOf(PlanFeatureGuard.PlanCapabilityException.class)
                    .hasMessageContaining("Estándar y Premium");
        }

        @Test
        @DisplayName("CAN_VIEW_PAGE_VISIT_METRICS con el flag activo: no lanza")
        void pageVisitMetrics_allowed() {
            when(planResolver.resolve(COMMERCIAL_ID))
                    .thenReturn(EffectivePlanState.builder().canViewPageVisitMetrics(true).build());

            assertThatCode(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_VIEW_PAGE_VISIT_METRICS))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("CAN_VIEW_PAGE_VISIT_METRICS con el flag inactivo: lanza PlanCapabilityException")
        void pageVisitMetrics_denied() {
            when(planResolver.resolve(COMMERCIAL_ID))
                    .thenReturn(EffectivePlanState.builder().canViewPageVisitMetrics(false).build());

            assertThatThrownBy(() -> guard.assertCapability(COMMERCIAL_ID, Capability.CAN_VIEW_PAGE_VISIT_METRICS))
                    .isInstanceOf(PlanFeatureGuard.PlanCapabilityException.class)
                    .hasMessageContaining("Premium");
        }
    }

    @Nested
    @DisplayName("assertBudgetNotDormant")
    class AssertBudgetNotDormant {

        @Test
        @DisplayName("estado no dormant: no lanza")
        void notDormant_passes() {
            when(planResolver.resolve(COMMERCIAL_ID))
                    .thenReturn(EffectivePlanState.builder().budgetDormant(false).build());

            assertThatCode(() -> guard.assertBudgetNotDormant(COMMERCIAL_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("estado dormant: lanza BudgetDormantException")
        void dormant_throws() {
            when(planResolver.resolve(COMMERCIAL_ID))
                    .thenReturn(EffectivePlanState.builder().budgetDormant(true).build());

            assertThatThrownBy(() -> guard.assertBudgetNotDormant(COMMERCIAL_ID))
                    .isInstanceOf(PlanFeatureGuard.BudgetDormantException.class);
        }
    }
}
