package com.verygana2.services.pet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Arma la URL pública de un asset del bucket de mascotas a partir de su
 * {@code objectKey}.
 *
 * Existe porque la misma lógica hacía falta en dos sitios que tienen que
 * coincidir exactamente: al listar escenas (lo que ve el editor de escenas del
 * diseñador) y al entregar el permiso de subida (lo que el editor pinta en el
 * lienzo antes de guardar). Si las dos se calcularan por separado, una miniatura
 * podría apuntar a un sitio y el asset guardado a otro.
 *
 * Ojo: {@code PetCatalogServiceImpl} y {@code CatalogIntegrationRequestServiceImpl}
 * todavía tienen su propia copia idéntica de este cálculo. Se dejaron intactas a
 * propósito para no tocar flujos ajenos a las escenas; migrarlas es un cambio
 * mecánico pendiente.
 */
@Component
public class PetAssetUrlResolver {

    /**
     * Base completa (esquema incluido) desde la que servir los assets. Si está
     * puesta gana sobre el dominio del CDN.
     *
     * Existe para poder servirlos por el proxy del front —igual que el build de
     * Unity— en vez de por el dominio público de R2. El juego pide los assets con
     * fetch, y r2.dev responde 200 pero SIN `Access-Control-Allow-Origin`, así que
     * el navegador los bloquea y la escena se queda vacía. Por el proxy son
     * mismo-origen y el problema desaparece sin depender de la configuración CORS
     * del bucket.
     */
    @Value("${pets.asset-base-url:}")
    private String assetBaseUrl;

    @Value("${cloudflare.r2.pets-cdn-domain:}")
    private String petsCdnDomain;

    @Value("${cloudflare.r2.pets-bucket-name:verygana-pets}")
    private String petsBucketName;

    /** Cadena vacía —no null— si no hay clave: es lo que ya esperaban los DTO de escena. */
    public String resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return "";
        if (assetBaseUrl != null && !assetBaseUrl.isBlank()) {
            String base = assetBaseUrl.endsWith("/")
                    ? assetBaseUrl.substring(0, assetBaseUrl.length() - 1)
                    : assetBaseUrl;
            return base + "/" + objectKey;
        }
        if (petsCdnDomain != null && !petsCdnDomain.isBlank()) {
            return String.format("https://%s/%s", petsCdnDomain, objectKey);
        }
        return String.format("https://%s.r2.dev/%s", petsBucketName, objectKey);
    }
}
