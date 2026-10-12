package com.verygana2.dtos.purchase.requests;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Carrito a cotizar. No lleva las llaves que el usuario quiere usar: la cotización da
 * los límites y el valor de la llave, y con eso el cliente calcula el dinero a pagar
 * para cualquier cantidad sin volver a preguntar.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeyCoverageQuoteRequestDTO {

    @Valid
    @NotEmpty(message = "Quote must have at least one item")
    private List<CreatePurchaseItemRequestDTO> items;
}
