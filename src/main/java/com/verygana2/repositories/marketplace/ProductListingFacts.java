package com.verygana2.repositories.marketplace;

import java.util.UUID;

/**
 * Datos que un listado de productos necesita y que no se leen del producto mismo: el stock
 * disponible (contado en la base, sin cargar las unidades con su código cifrado) y el nombre y
 * publicId del comercio (sin cargar el comercio ni su usuario). Una fila por producto de la página.
 */
public record ProductListingFacts(Long productId, String companyName, UUID commercialPublicId, long availableStock) {

    /** El DTO expone el stock como {@code Integer}. */
    public Integer availableStockAsInt() {
        return (int) availableStock;
    }
}
