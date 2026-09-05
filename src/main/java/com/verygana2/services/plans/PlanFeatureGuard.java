package com.verygana2.services.plans;

import java.util.List;

import org.springframework.stereotype.Service;

import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;
import com.verygana2.models.finance.plans.EffectivePlanState;
import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.surveys.Survey.SurveyStatus;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.commercial.PlanChangeRequestRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.surveys.SurveyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Valida que un anunciante tenga las capacidades necesarias antes de
 * ejecutar una acción. Actúa como guardia de negocio para operaciones
 * que dependen del plan efectivo.
 *
 * Uso típico (en un servicio de anuncios, por ejemplo):
 * <pre>
 *   planGuard.assertCanAdvertise(commercialId);
 *   // ... continúa la lógica de creación del anuncio
 * </pre>
 *
 * Todas las validaciones consultan el estado efectivo en tiempo real,
 * garantizando que los cambios de presupuesto se reflejen inmediatamente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanFeatureGuard {

    /** Estados terminales de una solicitud de cambio de plan: ya no "está en curso". */
    private static final List<PlanChangeRequestStatus> PLAN_CHANGE_TERMINAL_STATUSES = List.of(
            PlanChangeRequestStatus.APPLIED, PlanChangeRequestStatus.REJECTED, PlanChangeRequestStatus.CANCELLED);

    private final EffectivePlanResolver planResolver;
    private final ProductRepository productRepository;
    private final AdRepository adRepository;
    private final CampaignRepository campaignRepository;
    private final BrandingRequestRepository brandingRequestRepository;
    private final SurveyRepository surveyRepository;
    private final PlanChangeRequestRepository planChangeRequestRepository;

    public void assertCapability(Long commercialId, RequirePlanCapability.Capability capability) {
        EffectivePlanState state = planResolver.resolve(commercialId);

        switch (capability) {
            case CAN_ADVERTISE -> {
                if (!state.isCanAdvertise()) {
                    throw new PlanCapabilityException(
                        "El anunciante no puede publicar anuncios en su plan actual: " + state.getEffectivePlan().name());
                }
            }
            case CAN_USE_GAMES -> {
                if (!state.isCanUseGames()) {
                    throw new PlanCapabilityException(
                        "Juegos branded no disponibles en el plan: " + state.getEffectivePlan().name());
                }
            }
            case CAN_USE_SURVEYS -> {
                if (!state.isCanUseSurveys()) {
                    throw new PlanCapabilityException(
                        "Encuestas no disponibles en el plan: " + state.getEffectivePlan().name());
                }
            }
            case CAN_SELL_DIRECTLY -> {
                if (!state.isCanSellDirectly()) {
                    throw new PlanCapabilityException(
                        "El plan " + state.getEffectivePlan().name() + " no permite vender productos propios en el marketplace");
                }
            }
            case CAN_HAVE_PETS -> {
                if (!state.isCanHavePets()) {
                    throw new PlanCapabilityException(
                        "El módulo de mascotas no está disponible en el plan: " + state.getEffectivePlan().name());
                }
            }
            case CAN_PROMOTE_ALLY_PRODUCTS -> {
                if (!state.isCanPromoteAllyProducts()) {
                    throw new PlanCapabilityException(
                        "El plan " + state.getEffectivePlan().name() + " no permite promocionar productos de aliados");
                }
            }
            case CAN_EXPORT_REPORT -> {
                if (!state.isCanExportReport()) {
                    throw new PlanCapabilityException(
                        "El plan " + state.getEffectivePlan().name() + " no permite exportar el reporte ejecutivo en PDF");
                }
            }
            case CAN_VIEW_PERFORMANCE_METRICS -> {
                if (!state.isCanViewPerformanceMetrics()) {
                    throw new PlanCapabilityException(
                        "Las métricas de rendimiento de anuncios, encuestas y campañas están disponibles solo en los planes Estándar y Premium.");
                }
            }
            case CAN_VIEW_PAGE_VISIT_METRICS -> {
                if (!state.isCanViewPageVisitMetrics()) {
                    throw new PlanCapabilityException(
                        "La métrica de visitas a tu página oficial es exclusiva del plan Premium.");
                }
            }
            case MAX_PRODUCTS -> {
                long current = countSlotOccupyingProducts(commercialId);
                if (current >= state.getMaxProducts()) {
                    throw new PlanCapabilityException(
                        "Límite de productos alcanzado. Plan " + state.getEffectivePlan().name() +
                        " permite máximo " + state.getMaxProducts() + " (actual: " + current + ")");
                }
            }
            case MAX_ADS -> {
                long current = countSlotOccupyingAds(commercialId);
                if (current >= state.getMaxAds()) {
                    throw new PlanCapabilityException(
                        "Límite de anuncios alcanzado. Plan " + state.getEffectivePlan().name() +
                        " permite máximo " + state.getMaxAds() + " (actual: " + current + ")");
                }
            }
            case MAX_BRANDED_GAMES -> {
                long current = countSlotOccupyingBrandedGames(commercialId);
                if (current >= state.getMaxBrandedGames()) {
                    throw new PlanCapabilityException(
                        "Límite de juegos branded alcanzado. Plan " + state.getEffectivePlan().name() +
                        " permite máximo " + state.getMaxBrandedGames() + " (actual: " + current + ")");
                }
            }
            case MAX_SURVEYS -> {
                long current = countSlotOccupyingSurveys(commercialId);
                if (current >= state.getMaxSurveys()) {
                    throw new PlanCapabilityException(
                        "Límite de encuestas alcanzado. Plan " + state.getEffectivePlan().name() +
                        " permite máximo " + state.getMaxSurveys() + " (actual: " + current + ")");
                }
            }

        }
    }

    // ── Conteo de activos que ocupan un cupo del plan ─────────────────────────
    // La fuente única de verdad de "qué cuenta como un activo" — la usan tanto la
    // guardia de creación (assertCapability) como la validación de bajada de plan
    // (PlanChangeAssetValidator), para que ambos midan exactamente lo mismo.

    /** Productos activos (status ACTIVE) del comercial. */
    public long countSlotOccupyingProducts(Long commercialId) {
        return productRepository.countByCommercialIdAndIsActive(commercialId);
    }

    /** Anuncios en circulación (status ACTIVE) del comercial. */
    public long countSlotOccupyingAds(Long commercialId) {
        return adRepository.countByCommercialIdAndStatus(commercialId, AdStatus.ACTIVE);
    }

    /**
     * Juegos brandeados que ocupan un cupo: campañas no finalizadas
     * (DRAFT/ACTIVE/PAUSED) + solicitudes de branding aún en curso (todo lo que no
     * sea REJECTED/CANCELLED/CAMPAIGN_CREATED, ya que esta última ya está
     * representada por su Campaign correspondiente).
     */
    public long countSlotOccupyingBrandedGames(Long commercialId) {
        long nonFinalCampaigns = campaignRepository.countByCommercialIdAndStatusNotIn(
            commercialId, List.of(CampaignStatus.COMPLETED, CampaignStatus.CANCELLED));
        long activeRequests = brandingRequestRepository.countByCommercial_User_IdAndStatusNotIn(
            commercialId, List.of(
                BrandingRequestStatus.REJECTED,
                BrandingRequestStatus.CANCELLED,
                BrandingRequestStatus.CAMPAIGN_CREATED));
        return nonFinalCampaigns + activeRequests;
    }

    /** Encuestas que siguen consumiendo un cupo del plan (todo menos estados finales: REJECTED y COMPLETED). */
    public long countSlotOccupyingSurveys(Long commercialId) {
        return surveyRepository.countByCreatorIdAndStatusNotIn(
            commercialId, List.of(SurveyStatus.REJECTED, SurveyStatus.COMPLETED));
    }

    /**
     * Mientras el comercial tenga una solicitud de cambio de plan abierta (cualquier
     * estado que no sea APPLIED/REJECTED/CANCELLED) no puede crear ni reactivar activos:
     * el plan destino podría admitir menos —o ninguno— y quedaría por encima del límite
     * justo cuando el cambio se aplique. Debe esperar a que se resuelva o cancelar la
     * solicitud (POST /plans/change-request/{id}/cancel).
     *
     * <p>Se aplica en todos los puntos que verifican un límite {@code MAX_*}
     * (ver {@link PlanGuardAspect}): crear anuncio/producto/encuesta, solicitar branding,
     * reactivar un anuncio pausado, etc.
     */
    public void assertNoOpenPlanChangeRequest(Long commercialId) {
        boolean hasOpenRequest = !planChangeRequestRepository
                .findByCommercial_IdAndStatusNotIn(commercialId, PLAN_CHANGE_TERMINAL_STATUSES)
                .isEmpty();
        if (hasOpenRequest) {
            throw new PlanCapabilityException(
                "Tiene una solicitud de cambio de plan en curso. No puede crear ni activar " +
                "nuevos activos hasta que se resuelva o cancele la solicitud.");
        }
    }

    /**
     * Exige que el comercial tenga presupuesto disponible (STANDARD/PREMIUM con
     * wallet no agotado). BASIC nunca se suspende por presupuesto — no tiene wallet.
     */
    public void assertBudgetAvailable(Long commercialId) {
        EffectivePlanState state = planResolver.resolve(commercialId);
        if (state.isBudgetSuspended()) {
            throw new BudgetSuspendedException(
                "Su saldo publicitario está agotado. Recargue su billetera para crear nuevos " +
                "anuncios, campañas, encuestas o exportar reportes.");
        }
    }

    /**
     * Exige que la billetera del comercial no lleve agotada más del periodo de gracia de
     * su plan (estado DORMANT). Se aplica a la edición de activos ya creados — nunca a
     * pausar/reactivar activos ya financiados, que sigue permitido.
     */
    public void assertBudgetNotDormant(Long commercialId) {
        EffectivePlanState state = planResolver.resolve(commercialId);
        if (state.isBudgetDormant()) {
            throw new BudgetDormantException(
                "Tu billetera lleva demasiado tiempo agotada y tu cuenta está en pausa. " +
                "Recárgala para volver a crear y editar anuncios, campañas y encuestas.");
        }
    }

    // Excepción personalizada
    public static class PlanCapabilityException extends RuntimeException {
        public PlanCapabilityException(String message) {
            super(message);
        }
    }

    /** Subtipo específico para cuando el bloqueo es por presupuesto agotado, no por plan. */
    public static class BudgetSuspendedException extends PlanCapabilityException {
        public BudgetSuspendedException(String message) {
            super(message);
        }
    }

    /**
     * Subtipo de {@link BudgetSuspendedException} para cuando el saldo lleva agotado más
     * del periodo de gracia del plan (estado DORMANT) — además de crear, tampoco se puede editar.
     */
    public static class BudgetDormantException extends BudgetSuspendedException {
        public BudgetDormantException(String message) {
            super(message);
        }
    }
}