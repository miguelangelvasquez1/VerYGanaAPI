package com.verygana2.services.plans;

import org.springframework.stereotype.Service;

import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.finance.plans.EffectivePlanState;
import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.surveys.Survey.SurveyStatus;
import com.verygana2.repositories.AdRepository;
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
                long current = campaignRepository.countByCommercialIdAndStatus(commercialId, CampaignStatus.ACTIVE);
                if (current >= state.getMaxBrandedGames()) {
                    throw new PlanCapabilityException(
                        "Límite de juegos branded alcanzado. Plan " + state.getEffectivePlan().name() +
                        " permite máximo " + state.getMaxBrandedGames() + " (actual: " + current + ")");
                }
            }
            case MAX_SURVEYS -> {
                long current = surveyRepository.countByCreatorIdAndStatus(commercialId, SurveyStatus.ACTIVE);
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