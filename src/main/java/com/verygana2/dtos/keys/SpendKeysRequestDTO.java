package com.verygana2.dtos.keys;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SpendKeysRequestDTO(
        Long amountCents,
        @JsonProperty("amount") Long amountKeys,
        @NotNull Integer itemId,
        @NotBlank String itemName
) {

    /** Compat con los clientes (y tests) que ya mandan centavos. */
    public SpendKeysRequestDTO(Long amountCents, Integer itemId, String itemName) {
        this(amountCents, null, itemId, itemName);
    }

    @AssertTrue(message = "se requiere amount (llaves) o amountCents (centavos), positivo")
    public boolean isAmountPresent() {
        Long value = amountCents != null ? amountCents : amountKeys;
        return value != null && value >= 1;
    }

    /**
     * @param keyValueCents cuántos centavos vale una llave (financial.key-value-cents)
     */
    public Long resolveAmountCents(long keyValueCents) {
        if (amountCents != null) return amountCents;
        return amountKeys == null ? null : amountKeys * keyValueCents;
    }
}