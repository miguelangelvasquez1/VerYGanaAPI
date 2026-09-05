package com.verygana2.services.commercial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.models.commercial.CommercialDiagnosticAnswers;
import com.verygana2.models.enums.commercial.CommercialRoute;
import com.verygana2.models.enums.commercial.diagnostic.AcceptWithExample;
import com.verygana2.models.enums.commercial.diagnostic.AdvertisingLeadership;
import com.verygana2.models.enums.commercial.diagnostic.BudgetApproval;
import com.verygana2.models.enums.commercial.diagnostic.CampaignSupport;
import com.verygana2.models.enums.commercial.diagnostic.DesiredActiveOffers;
import com.verygana2.models.enums.commercial.diagnostic.DirectSaleMode;
import com.verygana2.models.enums.commercial.diagnostic.FeeViability;
import com.verygana2.models.enums.commercial.diagnostic.IndependentHelp;
import com.verygana2.models.enums.commercial.diagnostic.InstitutionalTool;
import com.verygana2.models.enums.commercial.diagnostic.InvestmentCapacity;
import com.verygana2.models.enums.commercial.diagnostic.MainActivity;
import com.verygana2.models.enums.commercial.diagnostic.MarketReachStructure;
import com.verygana2.models.enums.commercial.diagnostic.MetricsNeeded;
import com.verygana2.models.enums.commercial.diagnostic.NetworkActor;
import com.verygana2.models.enums.commercial.diagnostic.OwnPointsGrowth;
import com.verygana2.models.enums.commercial.diagnostic.OwnSalesPoints;
import com.verygana2.models.enums.commercial.diagnostic.ProductsReachViaNetwork;
import com.verygana2.models.enums.commercial.diagnostic.Understanding;
import com.verygana2.models.enums.commercial.diagnostic.YesNoUnsure;
import com.verygana2.models.enums.commercial.diagnostic.YesPartialNo;
import com.verygana2.models.enums.commercial.diagnostic.YesPartialNoNA;

/**
 * Cubre las "Pruebas mínimas de aceptación" (§19) del insumo técnico de
 * caracterización empresarial contra {@link CommercialDiagnosticClassifier}.
 */
@DisplayName("CommercialDiagnosticClassifier — reglas del insumo técnico (§13/§14/§19)")
class CommercialDiagnosticClassifierTest {

    private final CommercialDiagnosticClassifier classifier = new CommercialDiagnosticClassifier();

    private CommercialDiagnosticClassifier.Result classify(CommercialDiagnosticAnswers a) {
        return classifier.classify(a, List.of(), Set.of(), Set.of(), List.of());
    }

    // ==================== §19.1 — Empresa Tipo A ====================

    @Test
    @DisplayName("§19.1 — hamburguesería concentrada, hasta 3 ofertas → Tipo A / BASIC, no preliminar")
    void localBurgerJointGetsTypeA() {
        CommercialDiagnosticClassifier.Result r = classify(typeAProfile());

        assertThat(r.route()).isEqualTo(CommercialRoute.A);
        assertThat(r.modalityLabel()).isEqualTo("Empresa Tipo A");
        assertThat(r.preliminary()).isFalse();
        assertThat(r.verificationRequired()).isFalse();
    }

    // ==================== §19.2 — Empresa Tipo B ====================

    @Test
    @DisplayName("§19.2 — cadena con muchos puntos propios, juegos y mayor inversión, sin red independiente → Tipo B / STANDARD")
    void restaurantChainWithoutNetworkGetsTypeB() {
        CommercialDiagnosticClassifier.Result r = classify(typeBProfile());

        assertThat(r.route()).isEqualTo(CommercialRoute.B);
        assertThat(r.modalityLabel()).isEqualTo("Empresa Tipo B");
        assertThat(r.verificationRequired()).isFalse();
    }

    // ==================== §19.3 — Candidata Premium ====================

    @Test
    @DisplayName("§19.3 — productora con mercadeo institucional, distribuidores verificables y patrocinio → candidata Premium / PREMIUM")
    void institutionalProducerGetsPremiumCandidate() {
        CommercialDiagnosticClassifier.Result r = classifier.classify(
                premiumProfile(),
                List.of(),
                Set.of(InstitutionalTool.ESTATUTOS, InstitutionalTool.ORGANIGRAMA, InstitutionalTool.POLITICAS_COMERCIALES),
                Set.of(NetworkActor.DISTRIBUIDORES, NetworkActor.TIENDAS),
                List.of());

        assertThat(r.route()).isEqualTo(CommercialRoute.C);
        assertThat(r.modalityLabel()).isEqualTo("Candidata a Empresa Premium");
        assertThat(r.verificationRequired()).isTrue();
        assertThat(r.preliminary()).isTrue();
        assertThat(r.explanation()).contains("sujeto a documentos, verificación y aprobación");
    }

    // ==================== §19.4 — empresa grande sin red ====================

    @Test
    @DisplayName("§19.4 — empresa grande sin red independiente NO recibe Premium")
    void largeCompanyWithoutIndependentNetworkIsNotPremium() {
        CommercialDiagnosticAnswers a = premiumProfile();
        a.setIndependentEntrepreneursHelp(IndependentHelp.NO);
        a.setStableNetworkReachesConsumer(YesPartialNo.NO);

        CommercialDiagnosticClassifier.Result r = classifier.classify(
                a, List.of(), Set.of(InstitutionalTool.ESTATUTOS), Set.of(NetworkActor.NINGUNO), List.of());

        assertThat(r.route()).isNotEqualTo(CommercialRoute.C);
    }

    // ==================== §19.5 — afirma red sin evidencia ====================

    @Test
    @DisplayName("§19.5 — afirma tener red pero no puede acreditarla → se excluye Premium, recomendación preliminar")
    void claimsNetworkButCannotAccreditIsNotPremium() {
        CommercialDiagnosticAnswers a = premiumProfile();
        a.setCanAccreditNetwork(YesPartialNoNA.NO);   // D-5
        a.setCanDemonstrateNetwork(YesPartialNo.NO);  // PR-3

        CommercialDiagnosticClassifier.Result r = classifier.classify(
                a, List.of(), Set.of(InstitutionalTool.ESTATUTOS),
                Set.of(NetworkActor.DISTRIBUIDORES), List.of());

        assertThat(r.route()).isNotEqualTo(CommercialRoute.C);
        assertThat(r.preliminary()).isTrue();
    }

    // ==================== §19.6 — condiciones económicas ====================

    @Test
    @DisplayName("§19.6 — no acepta la cuota mensual de Tipo A (F-1 = NO) → se excluye Tipo A")
    void rejectingTypeAEconomicsExcludesTypeA() {
        CommercialDiagnosticAnswers a = typeAProfile();
        a.setTypeAMonthlyFeeViable(FeeViability.NO); // F-1 = NO
        // pero sí puede asumir las condiciones de Tipo B
        a.setTypeBInvestmentCapacity(InvestmentCapacity.SI);
        a.setAcceptsTypeBKeys(AcceptWithExample.SI);
        a.setUnderstandsProsperityRegime(Understanding.SI);

        CommercialDiagnosticClassifier.Result r = classify(a);

        assertThat(r.route()).isNotEqualTo(CommercialRoute.A);
        assertThat(r.route()).isEqualTo(CommercialRoute.B);
    }

    // ==================== §19.7 — las dudas no son rechazo ====================

    @Test
    @DisplayName("§19.7 — respuestas 'necesito explicación / depende' no bloquean: candidatura Premium condicionada")
    void doubtsDoNotBlockAndYieldConditionalCandidacy() {
        CommercialDiagnosticAnswers a = premiumProfile();
        a.setAcceptsPremiumBrandFocus(Understanding.NECESITA_EXPLICACION);   // PR-5
        a.setAcceptsDataProtectionMetrics(Understanding.NECESITA_EXPLICACION); // PR-6
        a.setUnderstandsProsperityRegime(Understanding.NECESITA_EXPLICACION); // F-6
        a.setCanConveneAndSponsorChain(BudgetApproval.DEPENDE_APROBACION);    // PR-4
        a.setCanApproveInstitutionalBudgets(BudgetApproval.DEPENDE_APROBACION); // F-7

        CommercialDiagnosticClassifier.Result r = classifier.classify(
                a, List.of(), Set.of(InstitutionalTool.ESTATUTOS, InstitutionalTool.JUNTA_DECISION),
                Set.of(NetworkActor.DISTRIBUIDORES), List.of());

        assertThat(r.route()).isEqualTo(CommercialRoute.C);
        assertThat(r.verificationRequired()).isTrue();
        assertThat(r.explanation()).contains("condicionada");
    }

    // ==================== robustez ====================

    @Nested
    @DisplayName("robustez")
    class Robustness {

        @Test
        @DisplayName("respuestas nulas no lanzan NPE y devuelven una modalidad preliminar")
        void nullAnswersDoNotThrow() {
            CommercialDiagnosticClassifier.Result r =
                    classifier.classify(new CommercialDiagnosticAnswers(), null, null, null, null);

            assertThat(r.route()).isIn(CommercialRoute.A, CommercialRoute.B, CommercialRoute.C);
            assertThat(r.preliminary()).isTrue();
        }

        @Test
        @DisplayName("siempre devuelve A, B o C — nunca D ni E")
        void neverReturnsDorE() {
            assertThat(classify(typeAProfile()).route()).isIn(CommercialRoute.A, CommercialRoute.B, CommercialRoute.C);
            assertThat(classify(typeBProfile()).route()).isIn(CommercialRoute.A, CommercialRoute.B, CommercialRoute.C);
        }
    }

    // ==================== perfiles base ====================

    /** Operación concentrada, venta directa, hasta 3 ofertas, acepta economía A. */
    private static CommercialDiagnosticAnswers typeAProfile() {
        CommercialDiagnosticAnswers a = new CommercialDiagnosticAnswers();
        a.setMainActivity(MainActivity.RESTAURANTE);
        a.setMarketReachStructure(MarketReachStructure.PROPIETARIO);
        a.setDifferentiatedResponsibilities(YesPartialNo.NO);
        a.setAdvertisingLeadership(AdvertisingLeadership.PROPIETARIO);
        a.setOwnSalesPoints(OwnSalesPoints.UNO);
        a.setDirectSaleToConsumer(DirectSaleMode.SI);
        a.setIndependentEntrepreneursHelp(IndependentHelp.NO);
        a.setDesiredActiveOffers(DesiredActiveOffers.HASTA_TRES);
        a.setMetricsNeeded(MetricsNeeded.BASICAS);
        a.setSellsDirectlyAndConcentrated(YesPartialNo.SI);
        a.setThreeOffersAndBasicMetricsSufficient(Boolean.TRUE);
        a.setAcceptsStartWithoutOwnGamesOrIntelligence(Boolean.TRUE);
        a.setNeedsMoreCapacityThanTypeA(Boolean.FALSE);
        a.setTypeAMonthlyFeeViable(FeeViability.SI);
        a.setAcceptsTypeACommission(AcceptWithExample.SI);
        a.setAcceptsTypeAKeys(AcceptWithExample.SI);
        a.setTypeBInvestmentCapacity(InvestmentCapacity.NO);
        a.setAcceptsPremiumBrandFocus(Understanding.NO);
        a.setAcceptsDataProtectionMetrics(Understanding.NO);
        return a;
    }

    /** Venta directa de mayor capacidad: muchos puntos propios, juegos, campañas; sin red independiente. */
    private static CommercialDiagnosticAnswers typeBProfile() {
        CommercialDiagnosticAnswers a = new CommercialDiagnosticAnswers();
        a.setMainActivity(MainActivity.RESTAURANTE);
        a.setMarketReachStructure(MarketReachStructure.AREAS_INTERNAS);
        a.setDifferentiatedResponsibilities(YesPartialNo.SI);
        a.setAdvertisingLeadership(AdvertisingLeadership.AREA_MERCADEO);
        a.setOwnSalesPoints(OwnSalesPoints.MAS_DE_20);
        a.setDirectSaleToConsumer(DirectSaleMode.SI);
        a.setIndependentEntrepreneursHelp(IndependentHelp.NO);
        a.setDesiredActiveOffers(DesiredActiveOffers.MAS_DE_10);
        a.setMetricsNeeded(MetricsNeeded.JUEGOS_CAMPANAS);
        a.setSellsDirectlyAndConcentrated(YesPartialNo.PARCIALMENTE);
        a.setThreeOffersAndBasicMetricsSufficient(Boolean.FALSE);
        a.setAcceptsStartWithoutOwnGamesOrIntelligence(Boolean.FALSE);
        a.setNeedsMoreCapacityThanTypeA(Boolean.TRUE);
        a.setGrowthDependsOnOwnPoints(OwnPointsGrowth.SI);
        a.setLacksInstitutionalSponsorNetwork(YesNoUnsure.SI);
        a.setTypeAMonthlyFeeViable(FeeViability.SI);
        a.setAcceptsTypeACommission(AcceptWithExample.SI);
        a.setAcceptsTypeAKeys(AcceptWithExample.SI);
        a.setTypeBInvestmentCapacity(InvestmentCapacity.SI);
        a.setAcceptsTypeBKeys(AcceptWithExample.SI);
        a.setUnderstandsProsperityRegime(Understanding.SI);
        a.setAcceptsPremiumBrandFocus(Understanding.NO);
        a.setAcceptsDataProtectionMetrics(Understanding.NO);
        return a;
    }

    /**
     * Ecosistema institucional con red estable de empresarios independientes,
     * acreditable, patrocinable; no vende directamente. Los actores de red y las
     * herramientas institucionales se pasan aparte en cada prueba.
     */
    private static CommercialDiagnosticAnswers premiumProfile() {
        CommercialDiagnosticAnswers a = new CommercialDiagnosticAnswers();
        a.setMainActivity(MainActivity.PRODUCCION);
        a.setMarketReachStructure(MarketReachStructure.COMBINACION);
        a.setDifferentiatedResponsibilities(YesPartialNo.SI);
        a.setAdvertisingLeadership(AdvertisingLeadership.AREA_MERCADEO);
        a.setOwnSalesPoints(OwnSalesPoints.NINGUNO);
        a.setDirectSaleToConsumer(DirectSaleMode.NO);
        a.setDesiredActiveOffers(DesiredActiveOffers.NO_VENTA_DIRECTA);
        a.setMetricsNeeded(MetricsNeeded.AVANZADAS);
        a.setSellsDirectlyAndConcentrated(YesPartialNo.NO);
        a.setIndependentEntrepreneursHelp(IndependentHelp.SI);
        a.setNetworkRelationshipOrganized(YesPartialNoNA.SI);
        a.setCanAccreditNetwork(YesPartialNoNA.SI);
        a.setCanDemonstrateNetwork(YesPartialNo.SI);
        a.setProductsReachViaNetwork(ProductsReachViaNetwork.SI_PRINCIPALMENTE);
        a.setCanConveneDistributors(YesPartialNoNA.SI);
        a.setCanSupportDistributorCampaigns(CampaignSupport.SI);
        a.setCanConveneAndSponsorChain(BudgetApproval.SI);
        a.setCanApproveInstitutionalBudgets(BudgetApproval.SI);
        a.setArticulatesInstitutionalFunctions(YesPartialNo.SI);
        a.setStableNetworkReachesConsumer(YesPartialNo.SI);
        a.setLacksInstitutionalSponsorNetwork(YesNoUnsure.NO);
        a.setAcceptsPremiumBrandFocus(Understanding.SI);
        a.setAcceptsDataProtectionMetrics(Understanding.SI);
        a.setTypeAMonthlyFeeViable(FeeViability.NO);
        a.setAcceptsTypeACommission(AcceptWithExample.SI);
        a.setAcceptsTypeAKeys(AcceptWithExample.SI);
        a.setTypeBInvestmentCapacity(InvestmentCapacity.SI);
        a.setAcceptsTypeBKeys(AcceptWithExample.SI);
        a.setUnderstandsProsperityRegime(Understanding.SI);
        return a;
    }
}
