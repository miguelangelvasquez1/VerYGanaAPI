package com.verygana2.dtos.user.commercial.responses;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.verygana2.dtos.product.responses.AllyPromotionResponseDTO;
import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.models.enums.commercial.DashboardPeriod;

import lombok.Builder;

/**
 * Payload completo del panel de inicio del comercial.
 *
 * <p>Es <b>adaptable al plan</b>: las secciones que no aplican vienen en
 * {@code null} y el front simplemente no las pinta.
 * <ul>
 *   <li>BASIC → sin {@link #engagement} ni {@link #allyPromotions}.</li>
 *   <li>PREMIUM → sin {@link #sales}, {@link #salesTrend} ni {@link #topProducts}
 *       (no vende productos); con {@link #allyPromotions}.</li>
 * </ul>
 *
 * <p><b>Todos los montos van en centavos</b> ({@code *Cents}). El front debe
 * dividir por 100 (o correr la coma dos posiciones) para mostrar pesos.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommercialDashboardSummaryResponseDTO(

        PeriodInfo period,
        PlanInfo plan,

        /** null si el plan no puede vender productos. */
        SalesBlock sales,
        /** null si el plan no puede vender productos. Un punto por día natural del rango (relleno con ceros). */
        List<SalesTrendPoint> salesTrend,
        /** null si el plan no puede vender productos. Top 5 productos del periodo. */
        List<FeaturedProductResponseDTO> topProducts,

        /** null para BASIC. */
        EngagementBlock engagement,

        /** Solo los canales habilitados por el plan. */
        List<ChannelBreakdownItem> channelBreakdown,

        PlanUsage planUsage,
        ActiveAssets activeAssets,

        /** null si el plan no puede promocionar productos de aliados (solo PREMIUM). */
        AllyPromotionsBlock allyPromotions,

        List<QuickAction> quickActions,
        List<Alert> alerts) {

    public record PeriodInfo(
            DashboardPeriod type,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate previousStartDate,
            LocalDate previousEndDate) {}

    public record PlanInfo(
            String code,
            boolean canAdvertise,
            boolean canUseGames,
            boolean canUseSurveys,
            boolean canSellDirectly,
            boolean canPromoteAllyProducts,
            boolean canExportReport,
            boolean budgetSuspended) {}

    /**
     * deltaPct = variación porcentual del periodo actual vs. el anterior.
     * null cuando el periodo anterior fue 0 (el front muestra "nuevo" en vez de +∞).
     */
    public record SalesBlock(
            SalesMetrics current,
            SalesMetrics previous,
            Double salesAmountDeltaPct,
            Double salesCountDeltaPct,
            Double netEarningsDeltaPct,
            Double platformCommissionsDeltaPct) {}

    public record SalesMetrics(
            long salesAmountCents,
            long salesCount,
            long netEarningsCents,
            long platformCommissionsCents) {}

    public record SalesTrendPoint(LocalDate date, long amountCents, long count) {}

    public record EngagementBlock(
            DeltaMetric adLikes,
            DeltaMetric surveyResponses,
            DeltaMetric gamePlays) {}

    public record DeltaMetric(long current, long previous, Double deltaPct) {}

    /** unit = SALES | INTERACTIONS. channel = MARKETPLACE | ADS | SURVEYS | GAMES. */
    public record ChannelBreakdownItem(String channel, String unit, long value) {}

    public record PlanUsage(String code, List<Slot> slots) {
        /** slot = PRODUCTS | ADS | BRANDED_GAMES | SURVEYS. */
        public record Slot(String slot, long used, int max) {}
    }

    /** Cada lista trae máx. 5 elementos; null cuando la sección no aplica al plan. */
    public record ActiveAssets(
            List<AssetPreview> ads,
            List<AssetPreview> brandedGames,
            List<AssetPreview> surveys,
            List<AssetPreview> products) {}

    /** progressPct: 0-100, null si no aplica (ej. producto). */
    public record AssetPreview(
            Long id,
            String title,
            String status,
            Double progressPct,
            String secondaryLabel) {}

    public record AllyPromotionsBlock(int promotedCount, List<AllyPromotionResponseDTO> allies) {}

    /** action = CREATE_AD | CREATE_SURVEY | CREATE_BRANDED_GAME | ADD_PRODUCT | RECHARGE_WALLET | EXPORT_REPORT | MANAGE_ALLIES. */
    public record QuickAction(String action, boolean enabled, String disabledReason) {}

    /**
     * type = ONBOARDING_INCOMPLETE | PLAN_CHANGE_PENDING | BUDGET_SUSPENDED | LOW_BALANCE.
     * severity = INFO | WARNING | CRITICAL.
     */
    public record Alert(String type, String severity, String message, String actionHint) {}
}
