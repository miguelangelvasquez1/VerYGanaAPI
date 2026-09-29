package com.verygana2.dtos.ad.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aumento de presupuesto de un anuncio. El presupuesto de un anuncio es
 * {@code rewardPerLike * maxLikes}, y {@code rewardPerLike} queda congelado al crearlo:
 * aumentar el presupuesto es, por tanto, comprar más likes al mismo precio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncreaseAdBudgetRequestDTO {

    /**
     * Lo que el cliente ve hoy como {@code maxLikes} del anuncio. Protege contra el doble cobro: como
     * solo un aumento cambia ese valor, un envío repetido (doble clic, reintento, otra pestaña) ya no
     * lo encuentra igual y se rechaza con 409 sin cobrar.
     */
    @NotNull(message = "El máximo de likes actual del anuncio es obligatorio")
    @Min(value = 1, message = "El máximo de likes actual debe ser al menos 1")
    private Integer expectedMaxLikes;

    @NotNull(message = "La cantidad de likes adicionales es obligatoria")
    @Min(value = 1, message = "Debes agregar al menos 1 like")
    @Max(value = 10000000, message = "No puedes agregar más de 10,000,000 likes")
    private Integer additionalLikes;
}
