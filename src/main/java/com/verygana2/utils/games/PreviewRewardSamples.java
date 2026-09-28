package com.verygana2.utils.games;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.verygana2.dtos.game.RewardCardResponseDTO;

/**
 * Productos de ejemplo para el popup de recompensas, solo en la vista previa.
 *
 * El popup se llena con los productos del comercial marcados como recompensa de
 * juego. Un comercial que todavía no cargó ninguno —el caso normal mientras se
 * diseña la campaña— deja ese bloque vacío, y el juego muestra «No hay anunciantes
 * por el momento». Eso está bien en producción: no hay nada que ofrecer. Pero en la
 * preview impide revisar cómo queda el popup, que es parte de lo que el diseñador y
 * el anunciante tienen que aprobar.
 *
 * <p>Van marcados como ejemplo en el nombre y en el mensaje a propósito: la preview
 * la mira el anunciante, y un producto inventado que parezca real es peor que un
 * popup vacío. <b>Nunca se usan en una campaña real</b>: si una campaña activa sale
 * sin productos, el popup tiene que salir vacío de verdad.
 */
@Component
public class PreviewRewardSamples {

    private static final String AVISO = "Ejemplo · solo en la vista previa";

    private final String imageUrl;

    public PreviewRewardSamples(@Value("${games.preview-sample-product-image-url}") String imageUrl) {
        this.imageUrl = imageUrl;
    }

    /**
     * Tres, que es el máximo que el popup muestra ({@code findGameRewardsProducts} usa LIMIT 3).
     *
     * Ids negativos: ningún producto real los tiene, así que si el popup enlaza por id
     * no puede abrir el producto de otro comercial.
     */
    public List<RewardCardResponseDTO> products(String commercialName) {
        return List.of(
            sample(-1L, "Producto de ejemplo", commercialName, 89900L, 4495L),
            sample(-2L, "Otro producto de ejemplo", commercialName, 31900L, 1595L),
            sample(-3L, "Tercer producto de ejemplo", commercialName, 47900L, 2395L));
    }

    private RewardCardResponseDTO sample(Long id, String name, String commercial, long priceCop, long keys) {
        return RewardCardResponseDTO.builder()
            .id(id)
            .name(name)
            .image_url(imageUrl)
            .image_message(AVISO)
            .commercial(commercial)
            .regular_price(priceCop)
            .keys_message("Con [[" + String.format("%,d", keys).replace(",", ".") + "]] llaves pagas [[menos]]")
            .rating(4.5)
            .max_keys_allowed(keys)
            .min_cash_cents(priceCop * 50)
            .stock(10)
            .category_name("Ejemplo")
            .build();
    }
}
