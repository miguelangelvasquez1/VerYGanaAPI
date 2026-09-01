package com.verygana2.dtos.finance.plans.responses;

import com.verygana2.models.finance.plans.Plan.PlanCode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resumen del otrosí de recarga, para que el comercial lo revise antes de que se
 * genere el documento y se envíe a firma. No tiene efectos secundarios — solo
 * cálculos de solo lectura sobre el estado actual del comercial.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargePreviewResponseDTO {

    /** Plan vigente — null si no tiene plan, la recarga nunca aplica en ese caso. */
    private PlanCode planCode;

    /** false si el plan no es STANDARD/PREMIUM, el monto está fuera de rango, o ya hay una recarga/cambio de plan en curso. */
    private boolean eligible;

    /** Explicación en lenguaje natural, lista para mostrar. */
    private String message;

    /** Todos los montos van en pesos colombianos (no en centavos). */
    private Long requestedAmountPesos;
    private Long minInvestmentPesos;
    private Long maxInvestmentPesos;
    private Long currentWalletBalancePesos;

    /**
     * Monto que realmente se acredita a la wallet tras el descuento de reserva de
     * tesorería — siempre menor al monto pagado. Se calcula igual que en
     * PlanServiceImpl#activateInvestment. En pesos.
     */
    private Long estimatedCreditedAmountPesos;

    private Long resultingWalletBalancePesos;
}
