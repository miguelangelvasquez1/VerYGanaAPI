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
     * Monto que el comercial va a invertir en el plan destino si es STANDARD/PREMIUM.
     * Es el abono que se cobrará para aplicar el cambio — independiente del saldo
     * actual o de abonos anteriores. Debe estar dentro del rango [min, max] del plan
     * destino; si se omite, se toma el mínimo del plan.
     */
    private Long intendedInvestmentAmountCents;
}
