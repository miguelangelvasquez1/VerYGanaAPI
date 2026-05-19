package com.verygana2.dtos.purchase.requests;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePurchaseRequestDTO {

    @Valid
    @NotEmpty(message = "Purchase must have at least one item")
    private List<CreatePurchaseItemRequestDTO> items;

    /**
     * Llaves de compra que el usuario quiere aplicar al total.
     * 0 = pago 100% con dinero real vía Wompi.
     * No puede superar el máximo permitido por el plan del empresario por producto.
     */
    @PositiveOrZero(message = "keysToUse cannot be negative")
    @Builder.Default
    private Long keysToUse = 0L;

    private String contactEmail;

    /**
     * URL de retorno del checkout de Wompi tras completar el pago.
     * Si no se provee, se usará una URL por defecto de la plataforma.
     */
    private String redirectUrl;
}