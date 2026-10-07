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
public class KeyCoverageQuoteRequestDTO {

    @Valid
    @NotEmpty(message = "Quote must have at least one item")
    private List<CreatePurchaseItemRequestDTO> items;

    /**
     * Llaves que el usuario quiere aplicar. Null = cotizar con todas las que pueda usar.
     * Si pide más de las permitidas, la cotización muestra lo que realmente se aplicaría.
     */
    @PositiveOrZero(message = "keysToUse cannot be negative")
    private Long keysToUse;
}
