package com.verygana2.services.plans;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.finance.plans.responses.PlanChangeBlockerDTO;
import com.verygana2.models.finance.plans.Plan;

import lombok.RequiredArgsConstructor;

/**
 * Comprueba, antes de dejar que un comercial solicite (o previsualice) un cambio de
 * plan, que los activos que ya tiene creados caben dentro de lo que permite el plan
 * destino.
 *
 * <p>Un cambio de plan puede reducir o quitar cupos:
 * <ul>
 *   <li>PREMIUM → STANDARD baja el máximo de anuncios/juegos/encuestas.</li>
 *   <li>STANDARD → PREMIUM: PREMIUM no vende productos propios.</li>
 *   <li>cualquiera → BASIC: sin anuncios, sin juegos brandeados, sin encuestas y
 *       menos productos.</li>
 * </ul>
 * Los activos no se pueden borrar directamente: el comercial debe esperar a que el
 * excedente finalice <b>antes</b> de pedir el cambio, o contactar al soporte de
 * VerYGana para cancelarlos antes de tiempo. Este validador devuelve la lista de
 * excedentes con cuántos siguen activos de cada tipo.
 *
 * <p>El conteo de "qué es un activo que ocupa cupo" se delega a {@link PlanFeatureGuard}
 * para que coincida exactamente con lo que esa misma guardia exige al crear activos
 * nuevos.
 */
@Service
@RequiredArgsConstructor
public class PlanChangeAssetValidator {

    private final PlanFeatureGuard planFeatureGuard;

    /**
     * @param commercialId id del comercial (== userId)
     * @param targetPlan   plan al que quiere cambiarse
     * @return excedentes por tipo de activo; vacío si todo cabe en el plan destino
     */
    @Transactional(readOnly = true)
    public List<PlanChangeBlockerDTO> findBlockers(Long commercialId, Plan targetPlan) {
        List<PlanChangeBlockerDTO> blockers = new ArrayList<>();

        addIfExceeded(blockers, "PRODUCTS", "productos",
                planFeatureGuard.countSlotOccupyingProducts(commercialId),
                effectiveLimit(targetPlan, "CAN_SELL_DIRECTLY", "MAX_PRODUCTS"));

        addIfExceeded(blockers, "ADS", "anuncios",
                planFeatureGuard.countSlotOccupyingAds(commercialId),
                effectiveLimit(targetPlan, "CAN_ADVERTISE", "MAX_ADS"));

        addIfExceeded(blockers, "BRANDED_GAMES", "juegos brandeados",
                planFeatureGuard.countSlotOccupyingBrandedGames(commercialId),
                effectiveLimit(targetPlan, "CAN_USE_GAMES", "MAX_BRANDED_GAMES"));

        addIfExceeded(blockers, "SURVEYS", "encuestas",
                planFeatureGuard.countSlotOccupyingSurveys(commercialId),
                effectiveLimit(targetPlan, "CAN_USE_SURVEYS", "MAX_SURVEYS"));

        return blockers;
    }

    /**
     * Límite real de un tipo de activo en un plan: 0 si el plan no habilita la
     * capacidad, {@link Integer#MAX_VALUE} si el feature es -1 (ilimitado por
     * convención), y el valor configurado en cualquier otro caso.
     */
    private int effectiveLimit(Plan plan, String capabilityCode, String limitCode) {
        if (!plan.getBoolFeature(capabilityCode, false)) {
            return 0;
        }
        int configured = plan.getIntFeature(limitCode, 0);
        return configured < 0 ? Integer.MAX_VALUE : configured;
    }

    private void addIfExceeded(List<PlanChangeBlockerDTO> blockers, String assetType,
            String assetLabel, long current, int allowed) {
        if (current <= allowed) {
            return;
        }
        long excess = current - allowed;
        blockers.add(new PlanChangeBlockerDTO(assetType, assetLabel, current, allowed, excess,
                buildMessage(assetLabel, current, allowed, excess)));
    }

    private String buildMessage(String assetLabel, long current, int allowed, long excess) {
        if (allowed == 0) {
            return "El plan destino no admite " + assetLabel + ": debe esperar a que finalicen los "
                    + current + " que siguen activos (no se conservan al cambiar de plan).";
        }
        return "El plan destino permite máximo " + allowed + " " + assetLabel + " y usted tiene " + current
                + ": debe esperar a que finalicen al menos " + excess + ".";
    }
}
