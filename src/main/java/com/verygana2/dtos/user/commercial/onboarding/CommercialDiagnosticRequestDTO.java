package com.verygana2.dtos.user.commercial.onboarding;

import java.util.Set;

import com.verygana2.models.enums.commercial.PrimaryGoal;
import com.verygana2.models.enums.commercial.TechIntegrationNeed;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Formulario inteligente: preguntas 3 a 11 del diagnóstico comercial.
 *
 * Q9 (techIntegrationNeeds) actúa como pregunta de bifurcación: si viene con al
 * menos una necesidad, el resto de las preguntas (Q3-Q8, Q10-Q11) se ignoran y
 * solo se exige integrationDetails, ya que ese caso se resuelve por negociación
 * directa con un asesor (Ruta D) sin pasar por clasificación ni selección de plan.
 * La validación condicional vive en el servicio, no en anotaciones, porque
 * depende del valor de techIntegrationNeeds.
 *
 * La duración del contrato (antes Q12a) se mueve a AcceptPlanRequestDTO: solo
 * aplica al plan Básico (suscripción con tarifa fija) y no tiene sentido
 * preguntarla antes de saber qué plan se va a elegir. La periodicidad de pago
 * (Q12b) y las condiciones de terminación (Q12c) se eliminaron por completo.
 */
@Data
public class CommercialDiagnosticRequestDTO {

    private Set<TechIntegrationNeed> techIntegrationNeeds; // Q9 (vacío/null = ninguna)

    @Size(max = 1000)
    private String integrationDetails; // Requerido solo si techIntegrationNeeds no está vacío

    private PrimaryGoal primaryGoal; // Q3

    private Boolean wantsFixedFee; // Q4

    private Boolean requiresCustomGames; // Q8

    private Boolean regulatedSector; // Q10

    private Boolean requiresSpecialNegotiation; // Q11
}
