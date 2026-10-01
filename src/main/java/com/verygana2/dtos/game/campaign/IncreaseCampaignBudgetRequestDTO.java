package com.verygana2.dtos.game.campaign;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aumento de presupuesto de una campaña de juego branded. A diferencia de anuncios y
 * encuestas, el presupuesto de una campaña es un monto libre (no un múltiplo de una unidad),
 * así que el aumento se expresa directamente en centavos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncreaseCampaignBudgetRequestDTO {

    /**
     * Lo que el cliente ve hoy como {@code budgetCents} de la campaña. Protege contra el doble
     * cobro: como solo un aumento cambia ese valor, un envío repetido (doble clic, reintento, otra
     * pestaña) ya no lo encuentra igual y se rechaza con 409 sin cobrar.
     */
    @NotNull(message = "El presupuesto actual de la campaña es obligatorio")
    @Min(value = 1, message = "El presupuesto actual debe ser mayor a 0")
    private Long expectedBudgetCents;

    @NotNull(message = "El monto adicional es obligatorio")
    @Min(value = 1, message = "El monto adicional debe ser mayor a 0")
    private Long additionalBudgetCents;
}
