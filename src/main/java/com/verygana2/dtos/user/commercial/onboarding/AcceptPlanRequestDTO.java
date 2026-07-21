package com.verygana2.dtos.user.commercial.onboarding;

import com.verygana2.models.finance.plans.Plan.PlanCode;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Paso 7: el empresario elige el plan a aceptar — no necesariamente el recomendado. */
@Data
public class AcceptPlanRequestDTO {

    @NotNull(message = "El plan a aceptar es requerido")
    private PlanCode planCode;

    /**
     * Monto que el empresario invertirá, en centavos de COP. Requerido solo para
     * STANDARD/PREMIUM (debe caer dentro del rango de inversión del plan); para
     * BASIC no aplica (es suscripción mensual) y se ignora si se envía.
     */
    private Long investmentAmountCents;

    /**
     * Duración del contrato en meses. Requerida solo para BASIC (suscripción con
     * tarifa fija recurrente); para STANDARD/PREMIUM no aplica —el monto invertido
     * se consume vía comisión a un ritmo que depende de las ventas, no de un plazo
     * fijo— y se ignora si se envía.
     */
    private Integer contractDurationMonths;
}
