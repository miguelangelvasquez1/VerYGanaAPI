package com.verygana2.dtos.finance.plans.responses;

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
     * false solo cuando el destino es BASIC viniendo de STANDARD/PREMIUM y el saldo
     * de la wallet todavía no está en $0 — en ese caso requestPlanChange() rechaza la
     * solicitud, así que el frontend debe deshabilitar la confirmación mientras esto
     * sea false.
     */
    private boolean eligible;

    /** Explicación en lenguaje natural de cuándo/cómo aplicará el cambio, lista para mostrar. */
    private String message;

    private Long requiredTopUpAmountCents;
    private Long currentWalletBalanceCents;

    /** Solo poblado cuando el destino es BASIC. */
    private Long targetMonthlyPriceCents;

    /** Solo poblados cuando el destino es STANDARD/PREMIUM. */
    private Long targetMinInvestmentCents;
    private Long targetMaxInvestmentCents;

    private Integer targetSaleCommissionPct;
}
