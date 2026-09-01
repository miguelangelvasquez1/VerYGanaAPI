package com.verygana2.dtos.finance.plans.responses;

import java.util.List;

import com.verygana2.models.finance.plans.Plan.PlanCode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resumen del otrosí de cambio de plan, para que el comercial lo revise antes de que
 * se genere el documento y se envíe a firma. No tiene efectos secundarios — solo
 * cálculos de solo lectura sobre el estado actual del comercial.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanChangePreviewResponseDTO {

    private PlanCode fromPlanCode;
    private PlanCode toPlanCode;

    /**
     * false cuando {@code requestPlanChange()} rechazaría la solicitud tal como está
     * el comercial hoy — por saldo publicitario &gt; $0 al bajar a BASIC, o porque tiene
     * activos activos que no caben en el plan destino (ver {@link #blockers}). El
     * frontend debe deshabilitar la confirmación mientras esto sea false.
     */
    private boolean eligible;

    /** Explicación en lenguaje natural de cuándo/cómo aplicará el cambio (o de qué falta ajustar), lista para mostrar. */
    private String message;

    /** Todos los montos van en pesos colombianos (no en centavos). */
    private Long requiredTopUpAmountPesos;
    private Long currentWalletBalancePesos;

    /** Solo poblado cuando el destino es BASIC. En pesos. */
    private Long targetMonthlyPricePesos;

    /** Solo poblados cuando el destino es STANDARD/PREMIUM. En pesos. */
    private Long targetMinInvestmentPesos;
    private Long targetMaxInvestmentPesos;

    private Integer targetSaleCommissionPct;

    /**
     * Activos activos que exceden lo que permite el plan destino. El comercial debe
     * esperar a que finalicen (o pedir su cancelación al soporte de VerYGana) antes de
     * poder solicitar el cambio. Vacío cuando no hay ninguno. Cada entrada dice de qué
     * tipo, cuántos tiene, cuántos permite el destino y cuántos sobran.
     */
    private List<PlanChangeBlockerDTO> blockers;
}
