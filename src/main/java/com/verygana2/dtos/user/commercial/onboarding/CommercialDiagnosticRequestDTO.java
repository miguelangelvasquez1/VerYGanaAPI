package com.verygana2.dtos.user.commercial.onboarding;

import java.util.List;
import java.util.Set;

import com.verygana2.models.enums.commercial.TechIntegrationNeed;
import com.verygana2.models.enums.commercial.diagnostic.AcceptWithExample;
import com.verygana2.models.enums.commercial.diagnostic.AdvertisingLeadership;
import com.verygana2.models.enums.commercial.diagnostic.BudgetApproval;
import com.verygana2.models.enums.commercial.diagnostic.BusinessGoal;
import com.verygana2.models.enums.commercial.diagnostic.CampaignSupport;
import com.verygana2.models.enums.commercial.diagnostic.CurrentReach;
import com.verygana2.models.enums.commercial.diagnostic.DeliveryMethod;
import com.verygana2.models.enums.commercial.diagnostic.DesiredActiveOffers;
import com.verygana2.models.enums.commercial.diagnostic.DirectSaleMode;
import com.verygana2.models.enums.commercial.diagnostic.FeeViability;
import com.verygana2.models.enums.commercial.diagnostic.GrowthTool;
import com.verygana2.models.enums.commercial.diagnostic.IndependentHelp;
import com.verygana2.models.enums.commercial.diagnostic.InstitutionalTool;
import com.verygana2.models.enums.commercial.diagnostic.InteractionValues;
import com.verygana2.models.enums.commercial.diagnostic.InvestmentCapacity;
import com.verygana2.models.enums.commercial.diagnostic.MainActivity;
import com.verygana2.models.enums.commercial.diagnostic.MarketReachStructure;
import com.verygana2.models.enums.commercial.diagnostic.MetricsNeeded;
import com.verygana2.models.enums.commercial.diagnostic.NetworkActor;
import com.verygana2.models.enums.commercial.diagnostic.OrderHandlingCapacity;
import com.verygana2.models.enums.commercial.diagnostic.OwnPointsGrowth;
import com.verygana2.models.enums.commercial.diagnostic.OwnSalesPoints;
import com.verygana2.models.enums.commercial.diagnostic.ProductsReachViaNetwork;
import com.verygana2.models.enums.commercial.diagnostic.Understanding;
import com.verygana2.models.enums.commercial.diagnostic.YesNoUnsure;
import com.verygana2.models.enums.commercial.diagnostic.YesPartialNo;
import com.verygana2.models.enums.commercial.diagnostic.YesPartialNoNA;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Paso 4: diagnóstico comercial. Cuestionario del "Insumo técnico de
 * caracterización empresarial" (secciones 6 a 12; se excluyen las preguntas
 * jurídicas G-1..G-4 y la lista de evidencias de la §16).
 *
 * Los nombres de campo coinciden 1:1 con {@code CommercialDiagnosticAnswers} para
 * que el mapper los copie por nombre. El código entre paréntesis es el id de la
 * pregunta en el PDF. La obligatoriedad se valida en
 * {@code CommercialOnboardingServiceImpl.validateDiagnostic()} (no con anotaciones),
 * porque algunas preguntas dependen de respuestas anteriores.
 *
 * <b>Ruta alternativa — integración técnica.</b> Si {@code techIntegrationNeeds}
 * viene con al menos una necesidad, es una bifurcación: el resto del cuestionario
 * se ignora y la cuenta se clasifica como Ruta D (proveedor/aliado que requiere
 * integración técnica). No pasa por clasificación de modalidad ni selección de
 * plan; se coordina directamente con un asesor de VERYGANA. En ese caso
 * {@code integrationDetails} es obligatorio.
 */
@Data
public class CommercialDiagnosticRequestDTO {

    // ── Ruta alternativa: integración técnica (excluyente del cuestionario) ──

    /** Vacío/null = cuestionario normal. Con valores = Ruta D (integración técnica). */
    private Set<TechIntegrationNeed> techIntegrationNeeds;

    /** Descripción libre de la integración requerida. Obligatoria si techIntegrationNeeds no está vacío. */
    @Size(max = 1000)
    private String integrationDetails;

    // ── §6 / §10: selección múltiple (ordenada por prioridad) ────────────────

    @Size(max = 3, message = "Puede seleccionar hasta tres objetivos")
    private List<BusinessGoal> businessGoals; // M-1

    @Size(max = 5, message = "Puede seleccionar hasta cinco herramientas")
    private List<GrowthTool> growthTools; // P-1

    // ── §7 / §8: selección múltiple ─────────────────────────────────────────

    private Set<InstitutionalTool> institutionalTools; // E-3

    private Set<NetworkActor> commercialNetworkActors; // D-3

    // ── §5 / §7: identidad y estructura institucional ───────────────────────

    private MainActivity mainActivity; // G-5
    private MarketReachStructure marketReachStructure; // E-1
    private YesPartialNo differentiatedResponsibilities; // E-2
    private AdvertisingLeadership advertisingLeadership; // E-4
    private CurrentReach currentReach; // E-5
    private OwnSalesPoints ownSalesPoints; // E-6

    // ── §8: producción, comercialización, distribución y consumo ────────────

    private DirectSaleMode directSaleToConsumer; // D-1
    private IndependentHelp independentEntrepreneursHelp; // D-2
    private YesPartialNoNA networkRelationshipOrganized; // D-4
    private YesPartialNoNA canAccreditNetwork; // D-5
    private ProductsReachViaNetwork productsReachViaNetwork; // D-6
    private YesPartialNoNA canConveneDistributors; // D-7
    private CampaignSupport canSupportDistributorCampaigns; // D-8

    // ── §9: capacidad comercial directa ────────────────────────────────────

    private DesiredActiveOffers desiredActiveOffers; // C-1
    private YesPartialNoNA canKeepListingsUpdated; // C-2
    private OrderHandlingCapacity canHandleOrdersAndClaims; // C-3
    private DeliveryMethod deliveryMethod; // C-4

    // ── §10: herramientas para crecer ──────────────────────────────────────

    private MetricsNeeded metricsNeeded; // P-2
    private YesPartialNo canProvideAuthorizedContent; // P-3

    // ── §11: compatibilidad económica ──────────────────────────────────────

    private FeeViability typeAMonthlyFeeViable; // F-1
    private AcceptWithExample acceptsTypeACommission; // F-2
    private AcceptWithExample acceptsTypeAKeys; // F-3
    private InvestmentCapacity typeBInvestmentCapacity; // F-4
    private AcceptWithExample acceptsTypeBKeys; // F-5
    private Understanding understandsProsperityRegime; // F-6
    private BudgetApproval canApproveInstitutionalBudgets; // F-7
    private InteractionValues willRecognizeInteractionValues; // F-8

    // ── §12.1: confirmación Tipo A ─────────────────────────────────────────

    private YesPartialNo sellsDirectlyAndConcentrated; // A-1
    private Boolean threeOffersAndBasicMetricsSufficient; // A-2
    private Boolean acceptsStartWithoutOwnGamesOrIntelligence; // A-3

    // ── §12.2: confirmación Tipo B ─────────────────────────────────────────

    private Boolean needsMoreCapacityThanTypeA; // B-1
    private OwnPointsGrowth growthDependsOnOwnPoints; // B-2
    private YesNoUnsure lacksInstitutionalSponsorNetwork; // B-3

    // ── §12.3: confirmación Premium ────────────────────────────────────────

    private YesPartialNo articulatesInstitutionalFunctions; // PR-1
    private YesPartialNo stableNetworkReachesConsumer; // PR-2
    private YesPartialNo canDemonstrateNetwork; // PR-3
    private BudgetApproval canConveneAndSponsorChain; // PR-4
    private Understanding acceptsPremiumBrandFocus; // PR-5
    private Understanding acceptsDataProtectionMetrics; // PR-6
}
