package com.verygana2.dtos.branding;

import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * El contenido de marca que manda el anunciante, con la forma que declara el
 * esquema recortado del juego ({@code GET /branding-requests/games/{id}/brief-schema}).
 *
 * No se tipa campo a campo a propósito: cada juego pide cosas distintas y el
 * esquema ya es la fuente de verdad. Tiparlo en Java obligaría a tocar el backend
 * cada vez que entra un juego nuevo.
 */
@Data
public class SaveGameBriefDTO {

    @NotEmpty(message = "El contenido no puede ir vacío")
    private Map<String, Object> content;
}
