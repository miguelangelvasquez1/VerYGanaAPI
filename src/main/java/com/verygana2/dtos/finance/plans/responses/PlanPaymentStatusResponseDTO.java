package com.verygana2.dtos.finance.plans.responses;

import com.verygana2.models.finance.plans.Plan.PlanCode;
import lombok.Builder;
import lombok.Data;
 
/**
 * Estado de un pago de plan consultado por el frontend.
 * El frontend lo usa para mostrar el resultado al usuario
 * después de volver del checkout de Wompi.
 */
@Data
@Builder
public class PlanPaymentStatusResponseDTO {
 
    private String reference;
 
    /**
     * Estado de la WompiTransaction asociada.
     * PENDING  → el webhook aún no llegó, seguir esperando
     * APPROVED → pago exitoso, plan activado
     * DECLINED → pago rechazado
     * ERROR    → error técnico
     */
    private String wompiStatus;
 
    /**
     * Estado del plan resultante.
     * PENDING_PAYMENT → aún no confirmado
     * ACTIVE          → plan activado exitosamente
     * PAYMENT_FAILED  → el pago falló
     */
    private String planStatus;
 
    private PlanCode planCode;
 
    /** Mensaje legible para mostrar al usuario */
    private String message;
}

