package com.verygana2.repositories.marketplace;

/**
 * Proyección mínima de la imagen de un producto: solo ids y clave de objeto en R2.
 */
public record ProductImageRef(Long productId, Long assetId, String objectKey) {
}
