package com.verygana2.dtos.keys;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SpendKeysRequestDTO(
        @NotNull @Min(1) Long amountCents,
        @NotNull Integer itemId,
        @NotBlank String itemName
) {}