package com.verygana2.dtos.survey;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aumento de presupuesto de una encuesta. El presupuesto es
 * {@code preguntas * maxResponses * precioPorPregunta}, y el precio queda congelado al crearla:
 * aumentar el presupuesto es, por tanto, comprar más cupos de respuesta al mismo precio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncreaseSurveyBudgetRequest {

    /**
     * Lo que el cliente ve hoy como {@code maxResponses} de la encuesta. Protege contra el doble
     * cobro: como solo un aumento cambia ese valor, un envío repetido (doble clic, reintento, otra
     * pestaña) ya no lo encuentra igual y se rechaza con 409 sin cobrar.
     */
    @NotNull(message = "El cupo de respuestas actual de la encuesta es obligatorio")
    @Min(value = 1, message = "El cupo de respuestas actual debe ser al menos 1")
    private Integer expectedMaxResponses;

    @NotNull(message = "La cantidad de respuestas adicionales es obligatoria")
    @Min(value = 1, message = "Debes agregar al menos 1 respuesta")
    private Integer additionalResponses;
}
