package com.verygana2.services.plans;

import org.springframework.stereotype.Service;

import com.verygana2.models.plans.EffectivePlanState;

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

    /**
     * Valida que el anunciante pueda publicar anuncios.
     * Solo disponible en STANDARD y PREMIUM con presupuesto activo.
     *
     * @throws PlanCapabilityException si no tiene permiso
     */
    public void assertCanAdvertise(Long commercialId) {
        EffectivePlanState state = planResolver.resolve(commercialId);
        if (!state.isCanAdvertise()) {
            throw new PlanCapabilityException(
                    "El anunciante no puede publicar anuncios en su plan actual: "
                    + state.getEffectivePlan().name()
                    + ". Se requiere inversión activa (plan STANDARD o PREMIUM).");
        }
    }

    /**
     * Valida que el anunciante pueda usar juegos branded.
     *
     * @throws PlanCapabilityException si no tiene permiso
     */
    public void assertCanUseGames(Long commercialId) {
        EffectivePlanState state = planResolver.resolve(commercialId);
        if (!state.isCanUseGames()) {
            throw new PlanCapabilityException(
                    "El anunciante no puede usar juegos branded en su plan actual: "
                    + state.getEffectivePlan().name());
        }
    }

    /**
     * Valida que el anunciante no haya excedido el límite de productos activos.
     *
     * @param currentProductCount Número actual de productos activos del anunciante
     * @throws PlanCapabilityException si se excede el límite
     */
    public void assertProductLimitNotExceeded(Long commercialId, int currentProductCount) {
        EffectivePlanState state = planResolver.resolve(commercialId);
        if (currentProductCount >= state.getMaxProducts()) {
            throw new PlanCapabilityException(
                    "Límite de productos alcanzado. Plan " + state.getEffectivePlan().name()
                    + " permite máximo " + state.getMaxProducts() + " productos activos.");
        }
    }

    /**
     * Valida que el anunciante no haya excedido el límite de anuncios activos.
     *
     * @param currentAdCount Número actual de anuncios activos
     * @throws PlanCapabilityException si se excede el límite
     */
    public void assertAdLimitNotExceeded(Long commercialId, int currentAdCount) { //implementar lo de grok y probar con anuncios puede ser
        EffectivePlanState state = planResolver.resolve(commercialId);
        if (currentAdCount >= state.getMaxAds()) {
            throw new PlanCapabilityException(
                    "Límite de anuncios alcanzado. Plan " + state.getEffectivePlan().name()
                    + " permite máximo " + state.getMaxAds() + " anuncios activos.");
        }
    }

    /**
     * Valida que el anunciante no haya excedido el límite de juegos branded activos.
     *
     * @param currentGameCount Número actual de juegos branded activos
     * @throws PlanCapabilityException si se excede el límite
     */
    public void assertBrandedGameLimitNotExceeded(Long commercialId, int currentGameCount) {
        EffectivePlanState state = planResolver.resolve(commercialId);
        if (currentGameCount >= state.getMaxBrandedGames()) {
            throw new PlanCapabilityException(
                    "Límite de juegos branded alcanzado. Plan " + state.getEffectivePlan().name()
                    + " permite máximo " + state.getMaxBrandedGames() + " juegos activos.");
        }
    }

    /**
     * Retorna el estado efectivo del anunciante (lectura).
     * Útil para queries de estado sin validación.
     */
    public EffectivePlanState getState(Long commercialId) {
        return planResolver.resolve(commercialId);
    }

    // ── Excepción de negocio ──────────────────────────────────────────────────

    public static class PlanCapabilityException extends RuntimeException {
        public PlanCapabilityException(String message) {
            super(message);
        }
    }
}