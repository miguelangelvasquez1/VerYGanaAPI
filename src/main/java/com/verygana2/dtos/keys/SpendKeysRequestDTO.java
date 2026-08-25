package com.verygana2.dtos.keys;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Compra desde el juego de mascota, más los clientes internos que ya existían.
 *
 * El juego manda {@code BuyItemRequest {itemId, amount, itemName}} donde:
 *   • {@code amount} es la CANTIDAD de unidades (siempre 1), no el precio.
 *   • {@code itemId} llega siempre en 0 — el build no lo puebla (pendiente con el
 *     equipo de Unity). El identificador real viaja en {@code itemName} como
 *     string numérico ("14"), y corresponde a {@code pet_catalog_items.external_id},
 *     NO a la PK: quien resuelve el precio es {@code findByExternalId}. Confirmado
 *     el 2026-08-17 con una compra real (itemName=14 → Water).
 *
 * Por eso {@link #resolveCatalogId()} mira los dos campos: cuando arreglen el
 * build y empiecen a poblar {@code itemId}, sigue funcionando sin cambios acá.
 */
public record SpendKeysRequestDTO(
        Long amountCents,
        @JsonProperty("amount") Long quantity,
        @NotNull Integer itemId,
        @NotBlank String itemName
) {

    /** Compat con los clientes (y tests) que ya mandan centavos. */
    public SpendKeysRequestDTO(Long amountCents, Integer itemId, String itemName) {
        this(amountCents, null, itemId, itemName);
    }

    @AssertTrue(message = "se requiere amount (cantidad) o amountCents (centavos), positivo")
    public boolean isAmountPresent() {
        Long value = amountCents != null ? amountCents : quantity;
        return value != null && value >= 1;
    }

    /**
     * {@code externalId} del ítem, o null si no se puede determinar (la ropa manda
     * nombres como "monoculo", no números).
     */
    public Integer resolveCatalogId() {
        if (itemId != null && itemId > 0) return itemId;
        if (itemName == null) return null;
        try {
            return Integer.valueOf(itemName.trim());
        } catch (NumberFormatException e) {
            return null;   // ropa y demás ítems que mandan nombre en vez de id
        }
    }

    public long quantityOrOne() {
        return quantity != null && quantity > 0 ? quantity : 1L;
    }

    /**
     * Fallback para cuando el ítem no está en nuestro catálogo: se cobra lo que
     * diga el cliente. Es el comportamiento viejo, y se mantiene solo para los
     * ítems horneados en el build (que no tienen precio en el servidor).
     *
     * @param keyValueCents cuántos centavos vale una llave (financial.key-value-cents)
     */
    public Long resolveAmountCents(long keyValueCents) {
        if (amountCents != null) return amountCents;
        return quantity == null ? null : quantity * keyValueCents;
    }
}