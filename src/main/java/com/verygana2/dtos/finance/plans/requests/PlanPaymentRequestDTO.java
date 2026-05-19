package com.verygana2.dtos.finance.plans.requests;

import com.verygana2.models.finance.plans.Plan.PlanCode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
 
/**
 * Request para iniciar el pago de un plan.
 */
@Data
public class PlanPaymentRequestDTO {
 
    @NotNull(message = "El código del plan es requerido")
    private PlanCode planCode;
 
    /**
     * Solo requerido para STANDARD y PREMIUM.
     * Para BASIC se ignora — el precio lo determina el plan.
     */
    private Long amountCents;
}

