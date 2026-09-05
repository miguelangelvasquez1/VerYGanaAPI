package com.verygana2.models.commercial;

import com.verygana2.models.enums.commercial.diagnostic.AcceptWithExample;
import com.verygana2.models.enums.commercial.diagnostic.AdvertisingLeadership;
import com.verygana2.models.enums.commercial.diagnostic.BudgetApproval;
import com.verygana2.models.enums.commercial.diagnostic.CampaignSupport;
import com.verygana2.models.enums.commercial.diagnostic.CurrentReach;
import com.verygana2.models.enums.commercial.diagnostic.DeliveryMethod;
import com.verygana2.models.enums.commercial.diagnostic.DesiredActiveOffers;
import com.verygana2.models.enums.commercial.diagnostic.DirectSaleMode;
import com.verygana2.models.enums.commercial.diagnostic.FeeViability;
import com.verygana2.models.enums.commercial.diagnostic.IndependentHelp;
import com.verygana2.models.enums.commercial.diagnostic.InteractionValues;
import com.verygana2.models.enums.commercial.diagnostic.InvestmentCapacity;
import com.verygana2.models.enums.commercial.diagnostic.MainActivity;
import com.verygana2.models.enums.commercial.diagnostic.MarketReachStructure;
import com.verygana2.models.enums.commercial.diagnostic.MetricsNeeded;
import com.verygana2.models.enums.commercial.diagnostic.OrderHandlingCapacity;
import com.verygana2.models.enums.commercial.diagnostic.OwnPointsGrowth;
import com.verygana2.models.enums.commercial.diagnostic.OwnSalesPoints;
import com.verygana2.models.enums.commercial.diagnostic.ProductsReachViaNetwork;
import com.verygana2.models.enums.commercial.diagnostic.Understanding;
import com.verygana2.models.enums.commercial.diagnostic.YesNoUnsure;
import com.verygana2.models.enums.commercial.diagnostic.YesPartialNo;
import com.verygana2.models.enums.commercial.diagnostic.YesPartialNoNA;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/**
 * Respuestas escalares (una opción) del diagnóstico comercial — cuestionario del
 * "Insumo técnico de caracterización empresarial" (secciones 6 a 12, se excluyen
 * las preguntas jurídicas G-1..G-4). Va embebido en {@link CommercialOnboarding}:
 * mismas columnas, misma fila, sin join. Las preguntas de selección múltiple
 * (M-1, E-3, D-3, P-1) viven como {@code @ElementCollection} en CommercialOnboarding.
 *
 * El código entre paréntesis en cada campo es el identificador de la pregunta en
 * el PDF. El motor de reglas ({@code CommercialDiagnosticClassifier}) traduce estas
 * respuestas a una modalidad (Empresa Tipo A / B / candidata Premium).
 */
@Embeddable
@Getter
@Setter
public class CommercialDiagnosticAnswers {

    // ── §5 / §7: identidad y estructura institucional ─────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_main_activity", length = 30)
    private MainActivity mainActivity; // G-5

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_market_reach_structure", length = 30)
    private MarketReachStructure marketReachStructure; // E-1

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_differentiated_responsibilities", length = 20)
    private YesPartialNo differentiatedResponsibilities; // E-2

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_advertising_leadership", length = 30)
    private AdvertisingLeadership advertisingLeadership; // E-4

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_current_reach", length = 30)
    private CurrentReach currentReach; // E-5

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_own_sales_points", length = 20)
    private OwnSalesPoints ownSalesPoints; // E-6

    // ── §8: producción, comercialización, distribución y consumo ──────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_direct_sale_to_consumer", length = 20)
    private DirectSaleMode directSaleToConsumer; // D-1

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_independent_entrepreneurs_help", length = 20)
    private IndependentHelp independentEntrepreneursHelp; // D-2

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_network_relationship_organized", length = 20)
    private YesPartialNoNA networkRelationshipOrganized; // D-4

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_can_accredit_network", length = 20)
    private YesPartialNoNA canAccreditNetwork; // D-5

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_products_reach_via_network", length = 30)
    private ProductsReachViaNetwork productsReachViaNetwork; // D-6

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_can_convene_distributors", length = 20)
    private YesPartialNoNA canConveneDistributors; // D-7

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_can_support_distributor_campaigns", length = 30)
    private CampaignSupport canSupportDistributorCampaigns; // D-8

    // ── §9: capacidad comercial directa ──────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_desired_active_offers", length = 20)
    private DesiredActiveOffers desiredActiveOffers; // C-1

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_can_keep_listings_updated", length = 20)
    private YesPartialNoNA canKeepListingsUpdated; // C-2

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_can_handle_orders_and_claims", length = 20)
    private OrderHandlingCapacity canHandleOrdersAndClaims; // C-3

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_delivery_method", length = 30)
    private DeliveryMethod deliveryMethod; // C-4

    // ── §10: herramientas para crecer ────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_metrics_needed", length = 20)
    private MetricsNeeded metricsNeeded; // P-2

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_can_provide_authorized_content", length = 20)
    private YesPartialNo canProvideAuthorizedContent; // P-3

    // ── §11: compatibilidad económica ────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_type_a_monthly_fee_viable", length = 20)
    private FeeViability typeAMonthlyFeeViable; // F-1

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_accepts_type_a_commission", length = 20)
    private AcceptWithExample acceptsTypeACommission; // F-2

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_accepts_type_a_keys", length = 20)
    private AcceptWithExample acceptsTypeAKeys; // F-3

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_type_b_investment_capacity", length = 20)
    private InvestmentCapacity typeBInvestmentCapacity; // F-4

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_accepts_type_b_keys", length = 20)
    private AcceptWithExample acceptsTypeBKeys; // F-5

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_understands_prosperity_regime", length = 25)
    private Understanding understandsProsperityRegime; // F-6

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_can_approve_institutional_budgets", length = 20)
    private BudgetApproval canApproveInstitutionalBudgets; // F-7

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_will_recognize_interaction_values", length = 20)
    private InteractionValues willRecognizeInteractionValues; // F-8

    // ── §12.1: confirmación Tipo A ───────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_sells_directly_and_concentrated", length = 20)
    private YesPartialNo sellsDirectlyAndConcentrated; // A-1

    @Column(name = "diag_three_offers_and_basic_metrics_sufficient")
    private Boolean threeOffersAndBasicMetricsSufficient; // A-2

    @Column(name = "diag_accepts_start_without_own_games_or_intelligence")
    private Boolean acceptsStartWithoutOwnGamesOrIntelligence; // A-3

    // ── §12.2: confirmación Tipo B ───────────────────────────────────────────

    @Column(name = "diag_needs_more_capacity_than_type_a")
    private Boolean needsMoreCapacityThanTypeA; // B-1

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_growth_depends_on_own_points", length = 20)
    private OwnPointsGrowth growthDependsOnOwnPoints; // B-2

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_lacks_institutional_sponsor_network", length = 20)
    private YesNoUnsure lacksInstitutionalSponsorNetwork; // B-3

    // ── §12.3: confirmación Premium ──────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_articulates_institutional_functions", length = 20)
    private YesPartialNo articulatesInstitutionalFunctions; // PR-1

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_stable_network_reaches_consumer", length = 20)
    private YesPartialNo stableNetworkReachesConsumer; // PR-2

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_can_demonstrate_network", length = 20)
    private YesPartialNo canDemonstrateNetwork; // PR-3

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_can_convene_and_sponsor_chain", length = 20)
    private BudgetApproval canConveneAndSponsorChain; // PR-4

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_accepts_premium_brand_focus", length = 25)
    private Understanding acceptsPremiumBrandFocus; // PR-5

    @Enumerated(EnumType.STRING)
    @Column(name = "diag_accepts_data_protection_metrics", length = 25)
    private Understanding acceptsDataProtectionMetrics; // PR-6
}
