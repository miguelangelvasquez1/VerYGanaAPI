package com.verygana2.services.commercial;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;

import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingStatusResponseDTO;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.PlanUsage;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.QuickAction;
import com.verygana2.dtos.user.commercial.responses.SalesReportResponseDTO;
import com.verygana2.models.enums.commercial.DashboardPeriod;
import com.verygana2.models.finance.plans.EffectivePlanState;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.commercial.PlanChangeRequestRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameSessionRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.repositories.surveys.SurveyRepository;
import com.verygana2.repositories.surveys.SurveySessionRepository;
import com.verygana2.services.interfaces.commercial.CommercialOnboardingService;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.marketplace.AllyPromotionService;
import com.verygana2.services.plans.EffectivePlanResolver;
import com.verygana2.services.plans.PlanFeatureGuard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link CommercialDashboardServiceImpl}: el panel se adapta al plan
 * (BASIC sin engagement/aliados, PREMIUM sin ventas/productos), calcula el delta
 * vs. el periodo anterior y rellena de ceros los días sin ventas de la tendencia.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CommercialDashboardServiceImpl")
class CommercialDashboardServiceImplTest {

    private static final long COMMERCIAL_ID = 42L;

    @Mock private EffectivePlanResolver planResolver;
    @Mock private PlanFeatureGuard planFeatureGuard;
    @Mock private CommercialDetailsService commercialDetailsService;
    @Mock private CommercialOnboardingService commercialOnboardingService;
    @Mock private AllyPromotionService allyPromotionService;
    @Mock private PurchaseItemRepository purchaseItemRepository;
    @Mock private AdLikeRepository adLikeRepository;
    @Mock private SurveySessionRepository surveySessionRepository;
    @Mock private GameSessionRepository gameSessionRepository;
    @Mock private AdRepository adRepository;
    @Mock private SurveyRepository surveyRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PlanChangeRequestRepository planChangeRequestRepository;
    @Mock private WalletRepository walletRepository;

    @InjectMocks private CommercialDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        when(adRepository.findByCommercialId(anyLong(), any())).thenReturn(new PageImpl<>(List.of()));
        when(surveyRepository.findAllByCreatorIdAndStatusOrderByCreatedAtDesc(any(), anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(productRepository.findByCommercialId(anyLong(), any())).thenReturn(new PageImpl<>(List.of()));
        when(campaignRepository.findByCommercialId(anyLong())).thenReturn(List.of());
        when(allyPromotionService.getMyPromotions(anyLong())).thenReturn(List.of());
        when(planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(anyLong(), any())).thenReturn(List.of());
        when(walletRepository.findByCommercialId(anyLong())).thenReturn(java.util.Optional.empty());
        when(purchaseItemRepository.findDeliveredSaleInstants(anyLong(), any(), any())).thenReturn(List.of());
        when(purchaseItemRepository.sumNetToCommercialCents(anyLong(), any(), any())).thenReturn(null);

        CommercialOnboardingStatusResponseDTO completedOnboarding = new CommercialOnboardingStatusResponseDTO();
        completedOnboarding.setCompleted(true);
        when(commercialOnboardingService.getStatus(anyLong())).thenReturn(completedOnboarding);
    }

    private SalesReportResponseDTO salesReport(long amountCop, int count, long commissionCop) {
        return SalesReportResponseDTO.builder()
                .commercialId(COMMERCIAL_ID)
                .totalSalesAmount(BigDecimal.valueOf(amountCop))
                .totalSalesCount(count)
                .totalPlatformCommissionsAmount(BigDecimal.valueOf(commissionCop))
                .topSellingProducts(List.of())
                .build();
    }

    private EffectivePlanState.EffectivePlanStateBuilder planBase(PlanCode code) {
        return EffectivePlanState.builder()
                .hasActivePlan(true)
                .effectivePlan(code)
                .commissionActive(true)
                .commissionRate(BigDecimal.TEN)
                .remainingBudget(BigDecimal.ZERO)
                .budgetSuspended(false)
                .visibilityBoostPct(BigDecimal.ZERO);
    }

    // ── BASIC ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BASIC: hay ventas y uso de productos, pero no engagement ni aliados")
    void basic_planAdaptation() {
        when(planResolver.resolve(COMMERCIAL_ID)).thenReturn(planBase(PlanCode.BASIC)
                .canSellDirectly(true).maxProducts(10)
                .canAdvertise(false).canUseGames(false).canUseSurveys(false)
                .canPromoteAllyProducts(false).canExportReport(false)
                .build());
        when(commercialDetailsService.getSalesReport(anyLong(), any(), any()))
                .thenReturn(salesReport(500_000, 12, 50_000));
        when(planFeatureGuard.countSlotOccupyingProducts(COMMERCIAL_ID)).thenReturn(3L);

        var dto = service.getSummary(COMMERCIAL_ID, DashboardPeriod.LAST_30_DAYS);

        assertThat(dto.sales()).isNotNull();
        assertThat(dto.salesTrend()).isNotNull();
        assertThat(dto.topProducts()).isNotNull();
        assertThat(dto.engagement()).isNull();
        assertThat(dto.allyPromotions()).isNull();
        assertThat(dto.sales().current().salesAmountCents()).isEqualTo(50_000_000L); // 500.000 COP → centavos
        assertThat(dto.planUsage().slots())
                .extracting(PlanUsage.Slot::slot)
                .containsExactly("PRODUCTS");
        assertThat(dto.channelBreakdown()).extracting(i -> i.channel()).containsExactly("MARKETPLACE");
    }

    // ── PREMIUM ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PREMIUM: sin ventas/tendencia/top productos ni cupo de productos; con engagement y aliados")
    void premium_planAdaptation() {
        when(planResolver.resolve(COMMERCIAL_ID)).thenReturn(planBase(PlanCode.PREMIUM)
                .canSellDirectly(false).maxProducts(0)
                .canAdvertise(true).maxAds(50)
                .canUseGames(true).maxBrandedGames(20)
                .canUseSurveys(true).maxSurveys(50)
                .canPromoteAllyProducts(true).canExportReport(true)
                .build());
        when(adLikeRepository.countByCommercialIdAndCreatedAtRange(anyLong(), any(), any())).thenReturn(100L, 40L, 100L);
        when(surveySessionRepository.countCompletedByCreatorAndDateRange(anyLong(), any(), any())).thenReturn(0L);
        when(gameSessionRepository.countCompletedByCommercialAndDateRange(anyLong(), any(), any())).thenReturn(7L);

        var dto = service.getSummary(COMMERCIAL_ID, DashboardPeriod.LAST_7_DAYS);

        assertThat(dto.sales()).isNull();
        assertThat(dto.salesTrend()).isNull();
        assertThat(dto.topProducts()).isNull();
        assertThat(dto.engagement()).isNotNull();
        assertThat(dto.engagement().adLikes().current()).isEqualTo(100L);
        assertThat(dto.engagement().adLikes().deltaPct()).isEqualTo(150.0); // (100-40)/40*100
        assertThat(dto.allyPromotions()).isNotNull();
        assertThat(dto.planUsage().slots())
                .extracting(PlanUsage.Slot::slot)
                .containsExactlyInAnyOrder("ADS", "BRANDED_GAMES", "SURVEYS");
    }

    // ── STANDARD ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("STANDARD: ventas + engagement; 4 cupos de plan; delta null cuando el periodo anterior fue 0")
    void standard_deltaWithZeroPrevious() {
        when(planResolver.resolve(COMMERCIAL_ID)).thenReturn(planBase(PlanCode.STANDARD)
                .canSellDirectly(true).maxProducts(50)
                .canAdvertise(true).maxAds(10)
                .canUseGames(true).maxBrandedGames(5)
                .canUseSurveys(true).maxSurveys(10)
                .canPromoteAllyProducts(false).canExportReport(false)
                .build());
        // primer llamado = periodo actual con ventas; segundo = periodo anterior sin ventas
        when(commercialDetailsService.getSalesReport(anyLong(), any(), any()))
                .thenReturn(salesReport(300_000, 5, 30_000), salesReport(0, 0, 0));

        var dto = service.getSummary(COMMERCIAL_ID, DashboardPeriod.THIS_MONTH);

        assertThat(dto.sales()).isNotNull();
        assertThat(dto.engagement()).isNotNull();
        assertThat(dto.sales().current().salesCount()).isEqualTo(5L);
        assertThat(dto.sales().previous().salesCount()).isEqualTo(0L);
        assertThat(dto.sales().salesCountDeltaPct()).isNull(); // periodo anterior = 0 → "nuevo"
        assertThat(dto.planUsage().slots())
                .extracting(PlanUsage.Slot::slot)
                .containsExactlyInAnyOrder("PRODUCTS", "ADS", "BRANDED_GAMES", "SURVEYS");
    }

    @Test
    @DisplayName("budgetSuspended: las acciones de creación quedan deshabilitadas y aparece la alerta BUDGET_SUSPENDED")
    void budgetSuspended_disablesCreateActions() {
        when(planResolver.resolve(COMMERCIAL_ID)).thenReturn(planBase(PlanCode.STANDARD)
                .canSellDirectly(true).maxProducts(50)
                .canAdvertise(true).maxAds(10)
                .canUseGames(true).maxBrandedGames(5)
                .canUseSurveys(true).maxSurveys(10)
                .canExportReport(true)
                .budgetSuspended(true)
                .build());
        when(commercialDetailsService.getSalesReport(anyLong(), any(), any())).thenReturn(salesReport(0, 0, 0));

        var dto = service.getSummary(COMMERCIAL_ID, DashboardPeriod.LAST_30_DAYS);

        assertThat(dto.quickActions())
                .filteredOn(a -> a.action().equals("CREATE_AD"))
                .extracting(QuickAction::enabled)
                .containsExactly(false);
        assertThat(dto.alerts()).extracting(a -> a.type()).contains("BUDGET_SUSPENDED");
    }

    @Test
    @DisplayName("salesTrend: rellena con ceros los días del rango sin ventas")
    void salesTrend_fillsZeroDays() {
        when(planResolver.resolve(COMMERCIAL_ID)).thenReturn(planBase(PlanCode.BASIC)
                .canSellDirectly(true).maxProducts(10).build());
        when(commercialDetailsService.getSalesReport(anyLong(), any(), any())).thenReturn(salesReport(0, 0, 0));

        var dto = service.getSummary(COMMERCIAL_ID, DashboardPeriod.LAST_7_DAYS);

        assertThat(dto.salesTrend()).hasSize(7);
        assertThat(dto.salesTrend()).allSatisfy(p -> {
            assertThat(p.amountCents()).isZero();
            assertThat(p.count()).isZero();
        });
    }

    @Test
    @DisplayName("onboarding incompleto → alerta ONBOARDING_INCOMPLETE")
    void onboardingIncomplete_addsAlert() {
        when(planResolver.resolve(COMMERCIAL_ID)).thenReturn(planBase(PlanCode.BASIC)
                .canSellDirectly(true).maxProducts(10).build());
        when(commercialDetailsService.getSalesReport(anyLong(), any(), any())).thenReturn(salesReport(0, 0, 0));
        CommercialOnboardingStatusResponseDTO incomplete = new CommercialOnboardingStatusResponseDTO();
        incomplete.setCompleted(false);
        when(commercialOnboardingService.getStatus(COMMERCIAL_ID)).thenReturn(incomplete);

        var dto = service.getSummary(COMMERCIAL_ID, DashboardPeriod.TODAY);

        assertThat(dto.alerts()).extracting(a -> a.type()).contains("ONBOARDING_INCOMPLETE");
    }
}
