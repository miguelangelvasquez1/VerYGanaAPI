package com.verygana2.dtos.user.commercial.onboarding;

import java.util.Set;

import com.verygana2.models.enums.commercial.PrimaryGoal;
import com.verygana2.models.enums.commercial.TechIntegrationNeed;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Formulario inteligente: preguntas 3 a 9 del diagnóstico comercial.
 *
 * Q9 (techIntegrationNeeds) actúa como pregunta de bifurcación: si viene con al
 * menos una necesidad, el resto de las preguntas (Q3-Q8) se ignoran y solo se
 * exige integrationDetails, ya que ese caso se resuelve por negociación directa
 * con un asesor (Ruta D) sin pasar por clasificación ni selección de plan. La
 * validación condicional vive en el servicio, no en anotaciones, porque depende
 * del valor de techIntegrationNeeds.
 *
 * Sector regulado se eliminó por completo (no cambiaba la ruta ni nada más).
 * La negociación especial (antes Q11) se mueve a AcceptPlanRequestDTO: el
 * empresario solo sabe si la necesita una vez ve el detalle de los planes. La
 * duración del contrato (antes Q12a) también vive ahí, solo para plan Básico.
 */
@Data
public class CommercialDiagnosticRequestDTO {

    private Set<TechIntegrationNeed> techIntegrationNeeds; // Q9 (vacío/null = ninguna)

    @Size(max = 1000)
    private String integrationDetails; // Requerido solo si techIntegrationNeeds no está vacío

    private PrimaryGoal primaryGoal; // Q3

    private Boolean wantsFixedFee; // Q4

    private Boolean requiresCustomGames; // Q8: ¿requiere juegos personalizados?

    private Boolean requiresPets; // ¿requiere mascotas?

    private Boolean requiresSurveys; // ¿requiere encuestas?
}
