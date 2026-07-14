package com.verygana2.dtos.wompi;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Respuesta de Wompi al buscar transacciones por filtro (ej: GET /transactions?reference=X).
 * A diferencia de {@link WompiTransactionResponseDTO}, "data" es una lista porque el
 * usuario pudo haber reintentado el pago varias veces con la misma referencia.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WompiTransactionListResponseDTO {

    @JsonProperty("data")
    private List<WompiTransactionResponseDTO.WompiTransactionData> data;
}
