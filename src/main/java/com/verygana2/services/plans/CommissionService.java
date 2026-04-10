package com.verygana2.services.plans;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.plans.EffectivePlanState;
import com.verygana2.models.plans.Investment;
import com.verygana2.repositories.plans.InvestmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Calcula y aplica comisiones por ventas según el estado efectivo del anunciante.
 *
 * Reglas de comisión:
 * ──────────────────────────────────────────────────────────────────
 * CASO 1 – Anunciante en BASIC (sin presupuesto activo):
 *   → Siempre aplica comisión (porcentaje configurado en PlanFeature).
 *
 * CASO 2 – Anunciante en STANDARD/PREMIUM, ROI < 6x:
 *   → NO aplica comisión. El anunciante opera libremente hasta recuperar
 *     6 veces su inversión.
 *
 * CASO 3 – Anunciante en STANDARD/PREMIUM, ROI >= 6x:
 *   → Aplica comisión (porcentaje configurado en PlanFeature del plan activo).
 *     El presupuesto sigue activo; solo se activa la comisión sobre ventas.
 * ──────────────────────────────────────────────────────────────────
 *
 * En todos los casos, la venta continúa procesándose (no se bloquea).
 * La comisión es un cargo adicional que se descuenta del beneficio del anunciante.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionService {

    private final EffectivePlanResolver planResolver;
    private final InvestmentService investmentService;
    private final InvestmentRepository investmentRepository;

    /**
     * Calcula el monto de comisión que aplica a una venta.
     *
     * @param commercialId ID del anunciante
     * @param saleAmount   Monto bruto de la venta
     * @return Monto de comisión a cobrar (0 si no aplica)
     */
    public BigDecimal calculateCommission(Long commercialId, BigDecimal saleAmount) {
        EffectivePlanState state = planResolver.resolve(commercialId);

        if (!state.isCommissionActive()) {
            log.debug("Anunciante {} sin comisión activa (plan={}, ROI={})",
                    commercialId, state.getEffectivePlan(), state.isRoiReached());
            return BigDecimal.ZERO;
        }

        BigDecimal rate = state.getCommissionRate();
        BigDecimal commission = saleAmount.multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        log.debug("Comisión calculada para anunciante {}: {}% de {} = {}",
                commercialId, rate, saleAmount, commission);

        return commission;
    }

    /**
     * Procesa una venta completada:
     *  1. Registra la recuperación sobre la inversión activa.
     *  2. Verifica si se alcanza el ROI x6 con esta venta.
     *  3. Calcula y retorna el monto de comisión a cobrar.
     *
     * Este método debe invocarse en el flujo de procesamiento de ventas,
     * DESPUÉS de confirmar la transacción comercial.
     *
     * @param commercialId ID del anunciante
     * @param saleAmount   Monto bruto de la venta confirmada
     * @return Resultado del procesamiento con comisión calculada
     */
    @Transactional
    public SaleCommissionResult processSale(Long commercialId, BigDecimal saleAmount) {

        // Registrar recuperación en la inversión activa (si existe)
        boolean roiJustReached = investmentRepository
                .findActiveByCommercialId(commercialId)
                .map(Investment::getId)
                .map(id -> investmentService.registerSaleRecovery(id, saleAmount))
                .orElse(false);

        // Calcular comisión DESPUÉS de actualizar el ROI
        BigDecimal commission = calculateCommission(commercialId, saleAmount);
        EffectivePlanState state = planResolver.resolve(commercialId);

        if (roiJustReached) {
            log.info("Anunciante {} alcanzó ROI x6. Se activará comisión en ventas futuras.",
                    commercialId);
        }

        return new SaleCommissionResult(
                saleAmount,
                commission,
                saleAmount.subtract(commission),
                state.getEffectivePlan().name(),
                roiJustReached,
                state.isCommissionActive()
        );
    }

    // ── DTO de resultado ──────────────────────────────────────────────────────

    public record SaleCommissionResult(
            BigDecimal grossSaleAmount,
            BigDecimal commissionAmount,
            BigDecimal netAmountToAdvertiser,
            String appliedPlan,
            boolean roiReachedOnThisSale,
            boolean commissionWasActive
    ) {}
}