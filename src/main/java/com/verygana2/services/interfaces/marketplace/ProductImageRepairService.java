package com.verygana2.services.interfaces.marketplace;

import java.util.List;

/**
 * Reparación de un solo uso (spec 014, RN-7): publica la imagen de los productos ACTIVE
 * que solo la tienen en la ruta privada.
 */
public interface ProductImageRepairService {

    /**
     * Recorre los productos ACTIVE y publica la imagen que solo exista en privado.
     * Idempotente: una segunda corrida solo hace consultas. No borra la copia privada
     * ni escribe en la base de datos.
     */
    ProductImageRepairReport repairActiveProductImages();

    /** Producto y asset de una imagen; nunca lleva claves ni datos del comercial. */
    record ProductImageIds(Long productId, Long assetId) {
    }

    /**
     * Resultado de la reparación. Solo conteos e ids.
     *
     * @param checked       imágenes revisadas
     * @param alreadyPublic ya estaban en la ruta pública
     * @param repaired      publicadas por esta corrida
     * @param missing       sin imagen en ninguna de las dos rutas (el comercial debe subirla)
     * @param failed        no se pudo hablar con el almacenamiento (volver a correr)
     */
    record ProductImageRepairReport(int checked, int alreadyPublic, int repaired,
            List<ProductImageIds> missing, List<ProductImageIds> failed) {
    }
}
