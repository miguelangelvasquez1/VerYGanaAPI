package com.verygana2.services.commercial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.product.responses.AllyPromotionResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.CommercialOnboardingStatusResponseDTO;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.ActiveAssets;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.Alert;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.AllyPromotionsBlock;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.AssetPreview;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.ChannelBreakdownItem;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.DeltaMetric;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.EngagementBlock;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.PeriodInfo;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.PlanInfo;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.PlanUsage;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.QuickAction;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.SalesBlock;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.SalesMetrics;
import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO.SalesTrendPoint;
import com.verygana2.dtos.user.commercial.responses.SalesReportResponseDTO;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.enums.commercial.DashboardPeriod;
import com.verygana2.models.enums.finance.WalletStatus;
import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.EffectivePlanState;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.surveys.Survey;
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
import com.verygana2.services.interfaces.commercial.CommercialDashboardService;
import com.verygana2.services.interfaces.commercial.CommercialOnboardingService;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.marketplace.AllyPromotionService;
import com.verygana2.services.plans.EffectivePlanResolver;
import com.verygana2.services.plans.PlanFeatureGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ver {@link CommercialDashboardService}. Todo se calcula en tiempo real y en
 * centavos; la sección que no aplica al plan del comercial se devuelve en null.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommercialDashboardServiceImpl implements CommercialDashboardService {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final int PREVIEW_LIMIT = 5;
    private static final int ALLY_LIMIT = 10;

    /** Estados terminales de una solicitud de cambio de plan (espejo de PlanFeatureGuard). */
    private static final List<PlanChangeRequestStatus> PLAN_CHANGE_TERMINAL = List.of(
            PlanChangeRequestStatus.APPLIED, PlanChangeRequestStatus.REJECTED, PlanChangeRequestStatus.CANCELLED);

    private final EffectivePlanResolver planResolver;
    private final PlanFeatureGuard planFeatureGuard;
    private final CommercialDetailsService commercialDetailsService;
    private final CommercialOnboardingService commercialOnboardingService;
    private final AllyPromotionService allyPromotionService;

    private final PurchaseItemRepository purchaseItemRepository;
    private final AdLikeRepository adLikeRepository;
    private final SurveySessionRepository surveySessionRepository;
    private final GameSessionRepository gameSessionRepository;
    private final AdRepository adRepository;
    private final SurveyRepository surveyRepository;
    private final CampaignRepository campaignRepository;
    private final ProductRepository productRepository;
    private final PlanChangeRequestRepository planChangeRequestRepository;
    private final WalletRepository walletRepository;

    @Override
    public CommercialDashboardSummaryResponseDTO getSummary(Long commercialId, DashboardPeriod period) {
        DashboardPeriod.Window w = period.resolve(ZONE);
        EffectivePlanState plan = planResolver.resolve(commercialId);

        boolean sells = plan.isCanSellDirectly();
        boolean engages = plan.isCanAdvertise() || plan.isCanUseGames() || plan.isCanUseSurveys();
        boolean ally = plan.isCanPromoteAllyProducts();

        // El reporte de ventas del periodo actual se calcula una sola vez y se reutiliza.
        SalesReportResponseDTO currentSales = sells
                ? commercialDetailsService.getSalesReport(commercialId, w.start(), w.end())
                : null;

        return CommercialDashboardSummaryResponseDTO.builder()
                .period(buildPeriodInfo(period, w))
                .plan(buildPlanInfo(plan))
                .sales(sells ? buildSales(commercialId, w, currentSales) : null)
                .salesTrend(sells ? buildSalesTrend(commercialId, w) : null)
                .topProducts(currentSales != null ? currentSales.getTopSellingProducts() : null)
                .engagement(engages ? buildEngagement(commercialId, plan, w) : null)
                .channelBreakdown(buildChannelBreakdown(commercialId, plan, w, currentSales))
                .planUsage(buildPlanUsage(commercialId, plan))
                .activeAssets(buildActiveAssets(commercialId, plan))
                .allyPromotions(ally ? buildAllyPromotions(commercialId) : null)
                .quickActions(buildQuickActions(plan))
                .alerts(buildAlerts(commercialId, plan))
                .build();
    }

    // ── Periodo / plan ───────────────────────────────────────────────────────

    private PeriodInfo buildPeriodInfo(DashboardPeriod period, DashboardPeriod.Window w) {
        return new PeriodInfo(
                period,
                w.start().toLocalDate(),
                w.end().minusDays(1).toLocalDate(),
                w.previousStart().toLocalDate(),
                w.previousEnd().minusDays(1).toLocalDate());
    }

    private PlanInfo buildPlanInfo(EffectivePlanState plan) {
        return new PlanInfo(
                planCode(plan),
                plan.isCanAdvertise(),
                plan.isCanUseGames(),
                plan.isCanUseSurveys(),
                plan.isCanSellDirectly(),
                plan.isCanPromoteAllyProducts(),
                plan.isCanExportReport(),
                plan.isBudgetSuspended());
    }

    // ── Ventas ───────────────────────────────────────────────────────────────

    private SalesBlock buildSales(Long commercialId, DashboardPeriod.Window w, SalesReportResponseDTO currentSales) {
        SalesMetrics cur = salesMetrics(commercialId, w.start(), w.end(), currentSales);
        SalesMetrics prev = salesMetrics(commercialId, w.previousStart(), w.previousEnd(),
                commercialDetailsService.getSalesReport(commercialId, w.previousStart(), w.previousEnd()));
        return new SalesBlock(
                cur, prev,
                deltaPct(cur.salesAmountCents(), prev.salesAmountCents()),
                deltaPct(cur.salesCount(), prev.salesCount()),
                deltaPct(cur.netEarningsCents(), prev.netEarningsCents()),
                deltaPct(cur.platformCommissionsCents(), prev.platformCommissionsCents()));
    }

    private SalesMetrics salesMetrics(Long commercialId, ZonedDateTime start, ZonedDateTime end,
            SalesReportResponseDTO report) {
        long net = nz(purchaseItemRepository.sumNetToCommercialCents(commercialId, start, end));
        return new SalesMetrics(
                toCents(report.getTotalSalesAmount()),
                nz(report.getTotalSalesCount()),
                net,
                toCents(report.getTotalPlatformCommissionsAmount()));
    }

    private List<SalesTrendPoint> buildSalesTrend(Long commercialId, DashboardPeriod.Window w) {
        Map<LocalDate, long[]> byDay = new HashMap<>(); // [amountCents, count]
        for (Object[] row : purchaseItemRepository.findDeliveredSaleInstants(commercialId, w.start(), w.end())) {
            LocalDate day = toZonedDateTime(row[0]).withZoneSameInstant(ZONE).toLocalDate();
            long subtotal = ((Number) row[1]).longValue();
            long[] acc = byDay.computeIfAbsent(day, k -> new long[2]);
            acc[0] += subtotal;
            acc[1] += 1;
        }

        List<SalesTrendPoint> trend = new ArrayList<>();
        LocalDate last = w.end().toLocalDate();
        for (LocalDate d = w.start().toLocalDate(); d.isBefore(last); d = d.plusDays(1)) {
            long[] acc = byDay.getOrDefault(d, new long[2]);
            trend.add(new SalesTrendPoint(d, acc[0], acc[1]));
        }
        return trend;
    }

    // ── Engagement (anuncios / encuestas / juegos) ────────────────────────────

    private EngagementBlock buildEngagement(Long commercialId, EffectivePlanState plan, DashboardPeriod.Window w) {
        DeltaMetric likes = plan.isCanAdvertise() ? delta(
                adLikeRepository.countByCommercialIdAndCreatedAtRange(commercialId, w.start(), w.end()),
                adLikeRepository.countByCommercialIdAndCreatedAtRange(commercialId, w.previousStart(), w.previousEnd()))
                : null;
        DeltaMetric responses = plan.isCanUseSurveys() ? delta(
                surveySessionRepository.countCompletedByCreatorAndDateRange(commercialId, w.start(), w.end()),
                surveySessionRepository.countCompletedByCreatorAndDateRange(commercialId, w.previousStart(), w.previousEnd()))
                : null;
        DeltaMetric plays = plan.isCanUseGames() ? delta(
                gameSessionRepository.countCompletedByCommercialAndDateRange(commercialId, w.start(), w.end()),
                gameSessionRepository.countCompletedByCommercialAndDateRange(commercialId, w.previousStart(), w.previousEnd()))
                : null;
        return new EngagementBlock(likes, responses, plays);
    }

    // ── Desglose por canal ───────────────────────────────────────────────────

    private List<ChannelBreakdownItem> buildChannelBreakdown(Long commercialId, EffectivePlanState plan,
            DashboardPeriod.Window w, SalesReportResponseDTO currentSales) {
        List<ChannelBreakdownItem> items = new ArrayList<>();
        if (plan.isCanSellDirectly() && currentSales != null) {
            items.add(new ChannelBreakdownItem("MARKETPLACE", "SALES", nz(currentSales.getTotalSalesCount())));
        }
        if (plan.isCanAdvertise()) {
            items.add(new ChannelBreakdownItem("ADS", "INTERACTIONS",
                    adLikeRepository.countByCommercialIdAndCreatedAtRange(commercialId, w.start(), w.end())));
        }
        if (plan.isCanUseSurveys()) {
            items.add(new ChannelBreakdownItem("SURVEYS", "INTERACTIONS",
                    surveySessionRepository.countCompletedByCreatorAndDateRange(commercialId, w.start(), w.end())));
        }
        if (plan.isCanUseGames()) {
            items.add(new ChannelBreakdownItem("GAMES", "INTERACTIONS",
                    gameSessionRepository.countCompletedByCommercialAndDateRange(commercialId, w.start(), w.end())));
        }
        return items;
    }

    // ── Uso vs. límites del plan ─────────────────────────────────────────────

    private PlanUsage buildPlanUsage(Long commercialId, EffectivePlanState plan) {
        List<PlanUsage.Slot> slots = new ArrayList<>();
        if (plan.isCanSellDirectly()) {
            slots.add(new PlanUsage.Slot("PRODUCTS",
                    planFeatureGuard.countSlotOccupyingProducts(commercialId), plan.getMaxProducts()));
        }
        if (plan.isCanAdvertise()) {
            slots.add(new PlanUsage.Slot("ADS",
                    planFeatureGuard.countSlotOccupyingAds(commercialId), plan.getMaxAds()));
        }
        if (plan.isCanUseGames()) {
            slots.add(new PlanUsage.Slot("BRANDED_GAMES",
                    planFeatureGuard.countSlotOccupyingBrandedGames(commercialId), plan.getMaxBrandedGames()));
        }
        if (plan.isCanUseSurveys()) {
            slots.add(new PlanUsage.Slot("SURVEYS",
                    planFeatureGuard.countSlotOccupyingSurveys(commercialId), plan.getMaxSurveys()));
        }
        return new PlanUsage(planCode(plan), slots);
    }

    // ── Activos activos en preview ──────────────────────────────────────────

    private ActiveAssets buildActiveAssets(Long commercialId, EffectivePlanState plan) {
        return new ActiveAssets(
                plan.isCanAdvertise() ? previewAds(commercialId) : null,
                plan.isCanUseGames() ? previewGames(commercialId) : null,
                plan.isCanUseSurveys() ? previewSurveys(commercialId) : null,
                plan.isCanSellDirectly() ? previewProducts(commercialId) : null);
    }

    private List<AssetPreview> previewAds(Long commercialId) {
        return adRepository.findByCommercialId(commercialId, PageRequest.of(0, 30)).getContent().stream()
                .filter(a -> a.getStatus() == AdStatus.ACTIVE)
                .limit(PREVIEW_LIMIT)
                .map(a -> new AssetPreview(
                        a.getId(), a.getTitle(), a.getStatus().name(),
                        pct(nz(a.getCurrentLikes()), nz(a.getMaxLikes())),
                        nz(a.getCurrentLikes()) + " / " + nz(a.getMaxLikes()) + " likes"))
                .toList();
    }

    private List<AssetPreview> previewGames(Long commercialId) {
        return campaignRepository.findByCommercialId(commercialId).stream()
                .filter(c -> c.getStatus() == CampaignStatus.ACTIVE)
                .limit(PREVIEW_LIMIT)
                .map(this::toGamePreview)
                .toList();
    }

    private AssetPreview toGamePreview(Campaign c) {
        String title = c.getGame() != null ? c.getGame().getTitle() : "Campaña #" + c.getId();
        return new AssetPreview(
                c.getId(), title, c.getStatus().name(),
                pct(nz(c.getSpentCents()), nz(c.getBudgetCents())),
                nz(c.getCompletedSessions()) + " partidas · "
                        + money(nz(c.getSpentCents())) + " / " + money(nz(c.getBudgetCents())));
    }

    private List<AssetPreview> previewSurveys(Long commercialId) {
        return surveyRepository
                .findAllByCreatorIdAndStatusOrderByCreatedAtDesc(
                        PageRequest.of(0, PREVIEW_LIMIT), commercialId, Survey.SurveyStatus.ACTIVE)
                .getContent().stream()
                .map(this::toSurveyPreview)
                .toList();
    }

    private AssetPreview toSurveyPreview(Survey s) {
        Double progress = (s.getMaxResponses() != null && s.getMaxResponses() > 0)
                ? pct(nz(s.getResponseCount()), s.getMaxResponses())
                : null;
        return new AssetPreview(
                s.getId(), s.getTitle(), s.getStatus().name(),
                progress, nz(s.getResponseCount()) + " respuestas");
    }

    private List<AssetPreview> previewProducts(Long commercialId) {
        return productRepository.findByCommercialId(commercialId, PageRequest.of(0, PREVIEW_LIMIT)).getContent().stream()
                .map(this::toProductPreview)
                .toList();
    }

    private AssetPreview toProductPreview(Product p) {
        return new AssetPreview(
                p.getId(), p.getName(), p.getStatus() != null ? p.getStatus().name() : null,
                null,
                money(nz(p.getPriceCents())) + (p.getStock() != null ? " · Stock: " + p.getStock() : ""));
    }

    // ── Aliados (PREMIUM) ───────────────────────────────────────────────────

    private AllyPromotionsBlock buildAllyPromotions(Long commercialId) {
        List<AllyPromotionResponseDTO> promos = allyPromotionService.getMyPromotions(commercialId);
        if (promos == null) {
            return new AllyPromotionsBlock(0, List.of());
        }
        return new AllyPromotionsBlock(promos.size(), promos.stream().limit(ALLY_LIMIT).toList());
    }

    // ── Accesos directos ───────────────────────────────────────────────────

    private List<QuickAction> buildQuickActions(EffectivePlanState plan) {
        boolean budgetOk = !plan.isBudgetSuspended();
        boolean hasWallet = plan.getEffectivePlan() != null && plan.getEffectivePlan() != PlanCode.BASIC;
        return List.of(
                budgetGated("CREATE_AD", plan.isCanAdvertise(), budgetOk),
                budgetGated("CREATE_SURVEY", plan.isCanUseSurveys(), budgetOk),
                budgetGated("CREATE_BRANDED_GAME", plan.isCanUseGames(), budgetOk),
                budgetGated("ADD_PRODUCT", plan.isCanSellDirectly(), budgetOk),
                budgetGated("EXPORT_REPORT", plan.isCanExportReport(), budgetOk),
                simple("RECHARGE_WALLET", hasWallet),
                simple("MANAGE_ALLIES", plan.isCanPromoteAllyProducts()));
    }

    private QuickAction budgetGated(String action, boolean capable, boolean budgetOk) {
        if (!capable) return new QuickAction(action, false, "No disponible en tu plan");
        if (!budgetOk) return new QuickAction(action, false, "Saldo publicitario agotado");
        return new QuickAction(action, true, null);
    }

    private QuickAction simple(String action, boolean capable) {
        return capable ? new QuickAction(action, true, null)
                : new QuickAction(action, false, "No disponible en tu plan");
    }

    // ── Alertas / pendientes ───────────────────────────────────────────────

    private List<Alert> buildAlerts(Long commercialId, EffectivePlanState plan) {
        List<Alert> alerts = new ArrayList<>();

        try {
            CommercialOnboardingStatusResponseDTO onb = commercialOnboardingService.getStatus(commercialId);
            if (onb != null && !onb.isCompleted()) {
                alerts.add(new Alert("ONBOARDING_INCOMPLETE", "WARNING",
                        "Completa tu proceso de registro para operar sin restricciones.", "Ir al onboarding"));
            }
        } catch (RuntimeException e) {
            log.debug("No se pudo leer el estado de onboarding del comercial {}: {}", commercialId, e.getMessage());
        }

        if (!planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(commercialId, PLAN_CHANGE_TERMINAL).isEmpty()) {
            alerts.add(new Alert("PLAN_CHANGE_PENDING", "INFO",
                    "Tienes una solicitud de cambio de plan en curso. No puedes crear ni reactivar activos hasta resolverla.",
                    null));
        }

        Wallet wallet = walletRepository.findByCommercialId(commercialId).orElse(null);
        if (plan.isBudgetSuspended()) {
            alerts.add(new Alert("BUDGET_SUSPENDED", "CRITICAL",
                    "Tu saldo publicitario está agotado y tus anuncios, juegos y encuestas quedaron pausados. Recárgalo para reactivarlos.",
                    "Recargar saldo"));
        } else if (wallet != null && wallet.getStatus() == WalletStatus.LOW_BALANCE) {
            alerts.add(new Alert("LOW_BALANCE", "WARNING",
                    "Tu saldo publicitario está bajo. Recarga pronto para evitar que se pausen tus activos.",
                    "Recargar saldo"));
        }

        return alerts;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static String planCode(EffectivePlanState plan) {
        return plan.getEffectivePlan() != null ? plan.getEffectivePlan().name() : "NONE";
    }

    private DeltaMetric delta(long current, long previous) {
        return new DeltaMetric(current, previous, deltaPct(current, previous));
    }

    /** Variación porcentual redondeada a 2 decimales; null si el periodo previo fue 0. */
    private static Double deltaPct(double current, double previous) {
        if (previous == 0d) return null;
        return Math.round((current - previous) / previous * 10000d) / 100d;
    }

    /** Porcentaje 0-100 con 2 decimales; 0 si el total es 0. */
    private static Double pct(long part, long total) {
        if (total <= 0) return 0d;
        return Math.round(part * 10000d / total) / 100d;
    }

    private static long toCents(BigDecimal cop) {
        if (cop == null) return 0L;
        return cop.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static long nz(Integer v) {
        return v != null ? v : 0L;
    }

    private static long nz(Long v) {
        return v != null ? v : 0L;
    }

    private static String money(long cents) {
        return "$" + String.format("%,d", cents / 100);
    }

    /** Convierte lo que Hibernate devuelva para una columna temporal en un {@link ZonedDateTime}. */
    private static ZonedDateTime toZonedDateTime(Object value) {
        if (value instanceof ZonedDateTime z) return z;
        if (value instanceof OffsetDateTime o) return o.toZonedDateTime();
        if (value instanceof Instant i) return i.atZone(ZoneOffset.UTC);
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant().atZone(ZoneOffset.UTC);
        if (value instanceof LocalDateTime ldt) return ldt.atZone(ZoneOffset.UTC);
        throw new IllegalStateException("Tipo temporal inesperado: " + value.getClass());
    }
}
