package com.verygana2.dtos.finance.plans.requests;

import com.verygana2.models.finance.plans.Plan.PlanCode;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Request para solicitar explícitamente un cambio de plan (BASIC/STANDARD/PREMIUM). */
@Data
public class PlanChangeRequestDTO {

    @NotNull(message = "El plan destino es requerido")
    private PlanCode targetPlanCode;

    /**
     * Monto que el comercial planea depositar si el destino es STANDARD/PREMIUM.
     * Informativo — el abono real se calcula contra el saldo actual del wallet.
     */
    private Long intendedInvestmentAmountCents;
}
