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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resumen de solo lectura de todas las respuestas del diagnóstico comercial (paso 4),
 * mismos campos que {@code CommercialDiagnosticRequestDTO} — pensado para que compliance
 * las vea junto al Contrato Marco al revisarlo (ver ComplianceContractController), como
 * respaldo de la modalidad/ruta que el comercial confirmó.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticAnswersSummaryDTO {

    private Set<TechIntegrationNeed> techIntegrationNeeds;
    private String integrationDetails;

    private List<BusinessGoal> businessGoals; // M-1
    private List<GrowthTool> growthTools; // P-1
    private Set<InstitutionalTool> institutionalTools; // E-3
    private Set<NetworkActor> commercialNetworkActors; // D-3

    private MainActivity mainActivity; // G-5
    private MarketReachStructure marketReachStructure; // E-1
    private YesPartialNo differentiatedResponsibilities; // E-2
    private AdvertisingLeadership advertisingLeadership; // E-4
    private CurrentReach currentReach; // E-5
    private OwnSalesPoints ownSalesPoints; // E-6

    private DirectSaleMode directSaleToConsumer; // D-1
    private IndependentHelp independentEntrepreneursHelp; // D-2
    private YesPartialNoNA networkRelationshipOrganized; // D-4
    private YesPartialNoNA canAccreditNetwork; // D-5
    private ProductsReachViaNetwork productsReachViaNetwork; // D-6
    private YesPartialNoNA canConveneDistributors; // D-7
    private CampaignSupport canSupportDistributorCampaigns; // D-8

    private DesiredActiveOffers desiredActiveOffers; // C-1
    private YesPartialNoNA canKeepListingsUpdated; // C-2
    private OrderHandlingCapacity canHandleOrdersAndClaims; // C-3
    private DeliveryMethod deliveryMethod; // C-4

    private MetricsNeeded metricsNeeded; // P-2
    private YesPartialNo canProvideAuthorizedContent; // P-3

    private FeeViability typeAMonthlyFeeViable; // F-1
    private AcceptWithExample acceptsTypeACommission; // F-2
    private AcceptWithExample acceptsTypeAKeys; // F-3
    private InvestmentCapacity typeBInvestmentCapacity; // F-4
    private AcceptWithExample acceptsTypeBKeys; // F-5
    private Understanding understandsProsperityRegime; // F-6
    private BudgetApproval canApproveInstitutionalBudgets; // F-7
    private InteractionValues willRecognizeInteractionValues; // F-8

    private YesPartialNo sellsDirectlyAndConcentrated; // A-1
    private Boolean threeOffersAndBasicMetricsSufficient; // A-2
    private Boolean acceptsStartWithoutOwnGamesOrIntelligence; // A-3

    private Boolean needsMoreCapacityThanTypeA; // B-1
    private OwnPointsGrowth growthDependsOnOwnPoints; // B-2
    private YesNoUnsure lacksInstitutionalSponsorNetwork; // B-3

    private YesPartialNo articulatesInstitutionalFunctions; // PR-1
    private YesPartialNo stableNetworkReachesConsumer; // PR-2
    private YesPartialNo canDemonstrateNetwork; // PR-3
    private BudgetApproval canConveneAndSponsorChain; // PR-4
    private Understanding acceptsPremiumBrandFocus; // PR-5
    private Understanding acceptsDataProtectionMetrics; // PR-6
}
