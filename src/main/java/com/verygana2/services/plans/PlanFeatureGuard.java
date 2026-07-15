package com.verygana2.services.plans;

import java.util.List;

import org.springframework.stereotype.Service;

import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.finance.plans.EffectivePlanState;
import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.surveys.Survey.SurveyStatus;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.branding.BrandingRequestRepository;
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

    private final EffectivePlanResolver planResolver;
    private final ProductRepository productRepository;
    private final AdRepository adRepository;
    private final CampaignRepository campaignRepository;
    private final BrandingRequestRepository brandingRequestRepository;
    private final SurveyRepository surveyRepository;

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
            case MAX_PRODUCTS -> {
                long current = productRepository.countByCommercialIdAndIsActive(commercialId);
                if (current >= state.getMaxProducts()) {
                    throw new PlanCapabilityException(
                        "Límite de productos alcanzado. Plan " + state.getEffectivePlan().name() +
                        " permite máximo " + state.getMaxProducts() + " (actual: " + current + ")");
                }
            }
            case MAX_ADS -> {
                long current = adRepository.countByCommercialIdAndStatus(commercialId, AdStatus.ACTIVE);
                if (current >= state.getMaxAds()) {
                    throw new PlanCapabilityException(
                        "Límite de anuncios alcanzado. Plan " + state.getEffectivePlan().name() +
                        " permite máximo " + state.getMaxAds() + " (actual: " + current + ")");
                }
            }
            case MAX_BRANDED_GAMES -> {
                // Cuenta campañas no finalizadas (DRAFT/ACTIVE/PAUSED) + solicitudes de branding
                // aún en curso (todo lo que no sea REJECTED/CANCELLED/CAMPAIGN_CREATED, ya que
                // esta última ya está representada por su Campaign correspondiente).
                long nonFinalCampaigns = campaignRepository.countByCommercialIdAndStatusNotIn(
                    commercialId, List.of(CampaignStatus.COMPLETED, CampaignStatus.CANCELLED));
                long activeRequests = brandingRequestRepository.countByCommercial_User_IdAndStatusNotIn(
                    commercialId, List.of(
                        BrandingRequestStatus.REJECTED,
                        BrandingRequestStatus.CANCELLED,
                        BrandingRequestStatus.CAMPAIGN_CREATED));
                long current = nonFinalCampaigns + activeRequests;
                if (current >= state.getMaxBrandedGames()) {
                    throw new PlanCapabilityException(
                        "Límite de juegos branded alcanzado. Plan " + state.getEffectivePlan().name() +
                        " permite máximo " + state.getMaxBrandedGames() + " (actual: " + current + ")");
                }
            }
            case MAX_SURVEYS -> {
                // Cuenta encuestas que siguen consumiendo un cupo del plan (todo menos estados finales: CLOSED/"cancelada" y COMPLETED).
                long current = surveyRepository.countByCreatorIdAndStatusNotIn(
                    commercialId, List.of(SurveyStatus.CLOSED, SurveyStatus.COMPLETED));
                if (current >= state.getMaxSurveys()) {
                    throw new PlanCapabilityException(
                        "Límite de encuestas alcanzado. Plan " + state.getEffectivePlan().name() +
                        " permite máximo " + state.getMaxSurveys() + " (actual: " + current + ")");
                }
            }

        }
    }

    // Excepción personalizada
    public static class PlanCapabilityException extends RuntimeException {
        public PlanCapabilityException(String message) {
            super(message);
        }
    }
}