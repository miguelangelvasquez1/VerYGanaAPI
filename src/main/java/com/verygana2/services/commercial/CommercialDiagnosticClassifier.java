package com.verygana2.services.commercial;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.verygana2.models.commercial.CommercialDiagnosticAnswers;
import com.verygana2.models.enums.commercial.CommercialRoute;
import com.verygana2.models.enums.commercial.diagnostic.AcceptWithExample;
import com.verygana2.models.enums.commercial.diagnostic.AdvertisingLeadership;
import com.verygana2.models.enums.commercial.diagnostic.BudgetApproval;
import com.verygana2.models.enums.commercial.diagnostic.BusinessGoal;
import com.verygana2.models.enums.commercial.diagnostic.CampaignSupport;
import com.verygana2.models.enums.commercial.diagnostic.DesiredActiveOffers;
import com.verygana2.models.enums.commercial.diagnostic.DirectSaleMode;
import com.verygana2.models.enums.commercial.diagnostic.FeeViability;
import com.verygana2.models.enums.commercial.diagnostic.GrowthTool;
import com.verygana2.models.enums.commercial.diagnostic.IndependentHelp;
import com.verygana2.models.enums.commercial.diagnostic.InstitutionalTool;
import com.verygana2.models.enums.commercial.diagnostic.InvestmentCapacity;
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
 * Motor de reglas del diagnóstico comercial: traduce las respuestas del
 * cuestionario del "Insumo técnico de caracterización empresarial" a una de las
 * tres modalidades (§14) aplicando las "Reglas decisivas de clasificación" (§13)
 * y validándose contra las "Pruebas mínimas de aceptación" (§19).
 *
 * Sin dependencias de Spring más allá de {@code @Component} — la lógica es una
 * función pura y se prueba directamente en {@code CommercialDiagnosticClassifierTest}.
 *
 * Siempre devuelve A, B o C (§3.4/§3.5: "recomendación aproximada, nunca un
 * callejón sin salida"). Marca {@code preliminary} cuando el encaje es dudoso.
 */
@Component
public class CommercialDiagnosticClassifier {

    private static final String MSG_TYPE_A =
            "Su empresa presenta un perfil compatible con Tipo A: una modalidad sencilla para comenzar a "
            + "vender, atraer nuevos clientes y fortalecer su presencia comercial. La recomendación es "
            + "preliminar y no constituye activación automática.";

    private static final String MSG_TYPE_B =
            "Su empresa presenta un perfil compatible con Tipo B: una modalidad para vender directamente y "
            + "crecer mediante más campañas, juegos y herramientas promocionales. La recomendación es "
            + "preliminar y no constituye activación automática.";

    private static final String MSG_PREMIUM =
            "Su empresa presenta características preliminares de un ecosistema institucional capaz de "
            + "fortalecer su marca y a los empresarios que la acercan al consumidor. Puede ser evaluada "
            + "como candidata Premium. El resultado está sujeto a documentos, verificación y aprobación.";

    private static final String MSG_PREMIUM_CONDITIONAL =
            " Parte de la información quedó como \"parcial\" o \"depende de aprobación\", así que la "
            + "candidatura es condicionada y requiere revisión adicional.";

    private static final String MSG_APPROX_SUFFIX =
            " Faltan datos o hay respuestas en el límite entre modalidades; puede revisar y ajustar sus "
            + "respuestas antes de confirmar.";

    /** Herramientas P-1 que van más allá de Tipo A (juegos, ecosistema, inteligencia). */
    private static final Set<GrowthTool> BEYOND_TYPE_A_GROWTH_TOOLS = Set.of(
            GrowthTool.CURIOSIDAD_NEUROCIENCIA, GrowthTool.ECOSISTEMA_CONSUMIDOR,
            GrowthTool.DESCUBRIR_INTENCION_COMPRA);

    /** Herramientas P-1 que orientan hacia Premium (posicionamiento de marca / red / patrocinio). */
    private static final Set<GrowthTool> PREMIUM_GROWTH_TOOLS = Set.of(
            GrowthTool.POSICIONAMIENTO_EXPERIENCIAS, GrowthTool.FORTALECER_RED_CAMPANAS,
            GrowthTool.PATROCINAR_EMPRESAS_AB, GrowthTool.IMPULSAR_ALIADOS_PROMOCIONES);

    /** Objetivos M-1 que orientan hacia Premium. */
    private static final Set<BusinessGoal> PREMIUM_GOALS = Set.of(
            BusinessGoal.POSICIONAMIENTO_MARCA, BusinessGoal.INTELIGENCIA_COMERCIAL,
            BusinessGoal.PATROCINAR_EMPRESARIOS, BusinessGoal.FORTALECER_DISTRIBUIDORES,
            BusinessGoal.AMPLIAR_RED_DISTRIBUCION, BusinessGoal.CAMPANAS_REGIONALES_NACIONALES);

    public record Result(
            CommercialRoute route,
            String modalityLabel,
            String explanation,
            boolean preliminary,
            boolean verificationRequired) {
    }

    public Result classify(CommercialDiagnosticAnswers answers,
                           List<BusinessGoal> businessGoals,
                           Set<InstitutionalTool> institutionalTools,
                           Set<NetworkActor> networkActors,
                           List<GrowthTool> growthTools) {

        CommercialDiagnosticAnswers a = answers == null ? new CommercialDiagnosticAnswers() : answers;
        List<BusinessGoal> goals = businessGoals == null ? List.of() : businessGoals;
        Set<InstitutionalTool> tools = institutionalTools == null ? Set.of() : institutionalTools;
        Set<NetworkActor> actors = networkActors == null ? Set.of() : networkActors;
        List<GrowthTool> growth = growthTools == null ? List.of() : growthTools;

        // ── Señales de venta directa ────────────────────────────────────────
        boolean sellsDirectly =
                oneOf(a.getDirectSaleToConsumer(), DirectSaleMode.SI, DirectSaleMode.COMBINA)
                || oneOf(a.getSellsDirectlyAndConcentrated(), YesPartialNo.SI, YesPartialNo.PARCIALMENTE)
                || oneOf(a.getDesiredActiveOffers(), DesiredActiveOffers.HASTA_TRES,
                        DesiredActiveOffers.DE_4_A_10, DesiredActiveOffers.MAS_DE_10);

        // ── Señales de escala A vs B ────────────────────────────────────────
        boolean concentratedOperation =
                a.getSellsDirectlyAndConcentrated() == YesPartialNo.SI
                || (oneOf(a.getMarketReachStructure(), MarketReachStructure.PROPIETARIO,
                        MarketReachStructure.EQUIPO_PEQUENO)
                    && a.getDifferentiatedResponsibilities() != YesPartialNo.SI);

        boolean uptoThreeOffers =
                a.getDesiredActiveOffers() == DesiredActiveOffers.HASTA_TRES
                && !Boolean.FALSE.equals(a.getThreeOffersAndBasicMetricsSufficient());

        boolean needsMoreThanTypeA =
                oneOf(a.getDesiredActiveOffers(), DesiredActiveOffers.DE_4_A_10, DesiredActiveOffers.MAS_DE_10)
                || Boolean.TRUE.equals(a.getNeedsMoreCapacityThanTypeA())
                || oneOf(a.getMetricsNeeded(), MetricsNeeded.JUEGOS_CAMPANAS, MetricsNeeded.AVANZADAS)
                || Boolean.FALSE.equals(a.getThreeOffersAndBasicMetricsSufficient())
                || Boolean.FALSE.equals(a.getAcceptsStartWithoutOwnGamesOrIntelligence())
                || oneOf(a.getAdvertisingLeadership(), AdvertisingLeadership.AREA_MERCADEO,
                        AdvertisingLeadership.AGENCIA_EXTERNA, AdvertisingLeadership.AREAS_Y_AGENCIAS)
                || oneOf(a.getOwnSalesPoints(), OwnSalesPoints.DE_6_A_20, OwnSalesPoints.MAS_DE_20)
                || growth.stream().anyMatch(BEYOND_TYPE_A_GROWTH_TOOLS::contains);

        // ── Requisitos Premium (§13: TODOS deben cumplirse) ─────────────────
        boolean hasIndependentNetwork =
                a.getIndependentEntrepreneursHelp() == IndependentHelp.SI
                && !actorsEmptyOrNone(actors)
                && oneOf(a.getStableNetworkReachesConsumer(), YesPartialNo.SI, YesPartialNo.PARCIALMENTE)
                && a.getLacksInstitutionalSponsorNetwork() != YesNoUnsure.SI;

        boolean networkOrganized =
                oneOf(a.getNetworkRelationshipOrganized(), YesPartialNoNA.SI, YesPartialNoNA.PARCIALMENTE);

        boolean canAccredit =
                oneOf(a.getCanAccreditNetwork(), YesPartialNoNA.SI, YesPartialNoNA.PARCIALMENTE)
                && oneOf(a.getCanDemonstrateNetwork(), YesPartialNo.SI, YesPartialNo.PARCIALMENTE);

        boolean reachesViaNetwork =
                oneOf(a.getProductsReachViaNetwork(), ProductsReachViaNetwork.SI_PRINCIPALMENTE,
                        ProductsReachViaNetwork.SI_PARCIALMENTE);

        boolean canSponsor =
                oneOf(a.getCanSupportDistributorCampaigns(), CampaignSupport.SI, CampaignSupport.DEPENDE_PRESUPUESTO)
                && oneOf(a.getCanConveneAndSponsorChain(), BudgetApproval.SI, BudgetApproval.DEPENDE_APROBACION);

        boolean hasInstitutionalStructure =
                !toolsEmptyOrNone(tools)
                && oneOf(a.getArticulatesInstitutionalFunctions(), YesPartialNo.SI, YesPartialNo.PARCIALMENTE);

        boolean acceptsPremiumFocus = oneOf(a.getAcceptsPremiumBrandFocus(),
                Understanding.SI, Understanding.NECESITA_EXPLICACION);

        boolean acceptsDataMetrics = oneOf(a.getAcceptsDataProtectionMetrics(),
                Understanding.SI, Understanding.NECESITA_EXPLICACION);

        boolean premiumEligible = hasIndependentNetwork && networkOrganized && canAccredit
                && reachesViaNetwork && canSponsor && hasInstitutionalStructure
                && acceptsPremiumFocus && acceptsDataMetrics;

        boolean premiumConditional = premiumEligible && (
                a.getCanConveneAndSponsorChain() == BudgetApproval.DEPENDE_APROBACION
                || a.getCanApproveInstitutionalBudgets() == BudgetApproval.DEPENDE_APROBACION
                || a.getCanSupportDistributorCampaigns() == CampaignSupport.DEPENDE_PRESUPUESTO
                || a.getNetworkRelationshipOrganized() == YesPartialNoNA.PARCIALMENTE
                || a.getCanAccreditNetwork() == YesPartialNoNA.PARCIALMENTE
                || a.getStableNetworkReachesConsumer() == YesPartialNo.PARCIALMENTE
                || a.getCanDemonstrateNetwork() == YesPartialNo.PARCIALMENTE
                || a.getArticulatesInstitutionalFunctions() == YesPartialNo.PARCIALMENTE
                || a.getProductsReachViaNetwork() == ProductsReachViaNetwork.SI_PARCIALMENTE);

        // ── Aceptación de condiciones económicas (§13) ─────────────────────
        boolean acceptsTypeAEconomics =
                a.getTypeAMonthlyFeeViable() != FeeViability.NO
                && a.getAcceptsTypeACommission() != AcceptWithExample.NO
                && a.getAcceptsTypeAKeys() != AcceptWithExample.NO;

        boolean acceptsTypeBEconomics =
                a.getTypeBInvestmentCapacity() != InvestmentCapacity.NO
                && a.getAcceptsTypeBKeys() != AcceptWithExample.NO
                && a.getUnderstandsProsperityRegime() != Understanding.NO;

        // ── Decisión (siempre A/B/C, §19.7) ───────────────────────────────
        if (premiumEligible) {
            String explanation = premiumConditional ? MSG_PREMIUM + MSG_PREMIUM_CONDITIONAL : MSG_PREMIUM;
            return new Result(CommercialRoute.C, "Candidata a Empresa Premium", explanation, true, true);
        }

        if (concentratedOperation && uptoThreeOffers && sellsDirectly
                && !needsMoreThanTypeA && acceptsTypeAEconomics) {
            return new Result(CommercialRoute.A, "Empresa Tipo A", MSG_TYPE_A, false, false);
        }

        if (sellsDirectly && (needsMoreThanTypeA || !acceptsTypeAEconomics) && acceptsTypeBEconomics) {
            return new Result(CommercialRoute.B, "Empresa Tipo B", MSG_TYPE_B, false, false);
        }

        // Borde / señales insuficientes: recomendación aproximada entre A y B
        // (C sólo se recomienda con red independiente verificable, §19.4).
        int scoreA = count(
                concentratedOperation,
                uptoThreeOffers,
                sellsDirectly && !needsMoreThanTypeA,
                acceptsTypeAEconomics,
                a.getDesiredActiveOffers() == DesiredActiveOffers.HASTA_TRES,
                a.getSellsDirectlyAndConcentrated() == YesPartialNo.SI,
                growth.isEmpty() && goals.stream().noneMatch(PREMIUM_GOALS::contains));

        int scoreB = count(
                needsMoreThanTypeA,
                sellsDirectly,
                acceptsTypeBEconomics,
                oneOf(a.getOwnSalesPoints(), OwnSalesPoints.DE_6_A_20, OwnSalesPoints.MAS_DE_20),
                a.getMetricsNeeded() == MetricsNeeded.JUEGOS_CAMPANAS,
                a.getGrowthDependsOnOwnPoints() == OwnPointsGrowth.SI,
                Boolean.TRUE.equals(a.getNeedsMoreCapacityThanTypeA()),
                growth.stream().anyMatch(PREMIUM_GROWTH_TOOLS::contains)
                        || goals.stream().anyMatch(PREMIUM_GOALS::contains));

        // §19.6: una respuesta negativa a las condiciones económicas esenciales
        // excluye esa modalidad, también en la recomendación aproximada.
        CommercialRoute route;
        if (!acceptsTypeAEconomics && acceptsTypeBEconomics) {
            route = CommercialRoute.B;
        } else if (!acceptsTypeBEconomics && acceptsTypeAEconomics) {
            route = CommercialRoute.A;
        } else {
            route = scoreB > scoreA ? CommercialRoute.B : CommercialRoute.A;
        }
        boolean toB = route == CommercialRoute.B;
        return new Result(route,
                toB ? "Empresa Tipo B" : "Empresa Tipo A",
                (toB ? MSG_TYPE_B : MSG_TYPE_A) + MSG_APPROX_SUFFIX,
                true, false);
    }

    // ==================== HELPERS ====================

    @SafeVarargs
    private static <E extends Enum<E>> boolean oneOf(E value, E... options) {
        if (value == null) {
            return false;
        }
        for (E option : options) {
            if (value == option) {
                return true;
            }
        }
        return false;
    }

    private static int count(boolean... flags) {
        int n = 0;
        for (boolean f : flags) {
            if (f) {
                n++;
            }
        }
        return n;
    }

    private static boolean actorsEmptyOrNone(Set<NetworkActor> actors) {
        return actors.isEmpty() || (actors.size() == 1 && actors.contains(NetworkActor.NINGUNO));
    }

    private static boolean toolsEmptyOrNone(Set<InstitutionalTool> tools) {
        return tools.isEmpty() || (tools.size() == 1 && tools.contains(InstitutionalTool.NINGUNA));
    }
}
