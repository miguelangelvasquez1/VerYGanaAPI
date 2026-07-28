package com.verygana2.dtos.user.commercial.onboarding;

import com.verygana2.models.finance.plans.Plan.PlanCode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Paso 7: el empresario elige el plan a aceptar — no necesariamente el recomendado.
 *
 * Si además necesita condiciones a la medida (requiresSpecialNegotiation=true), debe
 * describirlas en specialNegotiationDetails; igual elige el plan que más se ajuste a
 * lo que necesita y sigue el flujo normal (monto a invertir, duración, etc.). Un
 * asesor revisa esos detalles y ajusta condiciones sobre esa base — no es un flujo
 * aparte sin plan.
 */
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

    /** true: además del plan elegido, necesita condiciones a la medida (Ruta E). */
    private Boolean requiresSpecialNegotiation;

    /** Requerido solo si requiresSpecialNegotiation es true: qué necesita, para que un asesor lo evalúe. */
    @Size(max = 1000)
    private String specialNegotiationDetails;
}
