package com.verygana2.dtos.branding;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lo que un juego le pide al anunciante, en las dos formas que puede pedirlo.
 *
 * El texto viene como esquema para pintar un formulario; los archivos, como un
 * número mínimo — porque el anunciante los sube como recursos corporativos y es el
 * diseñador quien los audita y los publica como assets del juego.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameBriefRequirementsDTO {

    private Long gameId;
    private String gameName;

    /** Nulos si el juego no pide contenido escrito: el paso del formulario no se muestra. */
    private Map<String, Object> jsonSchema;
    private Map<String, Object> uiSchema;

    /** Cero si el juego no exige archivos del anunciante. */
    private int requiredResourceCount;

    /** Qué son esos archivos, en palabras para el anunciante. */
    private String requiredResourceLabel;
}
