package com.verygana2.storage.service;

import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import com.verygana2.storage.config.R2Config;

/**
 * Única regla para armar la URL pública de un archivo de R2: {@code https://<cdn>/public/<key>}.
 * El dominio sale de {@code cloudflare.r2.cdn-domain} ({@code R2_CDN_DOMAIN}) y no hay fallback:
 * sin dominio el constructor falla, para no armar nunca una URL que no resuelve.
 */
@Component
public class PublicUrlBuilder {

    private final String cdnDomain;

    public PublicUrlBuilder(R2Config r2Config) {
        String domain = r2Config.getCdnDomain();
        if (domain == null || domain.isBlank()) {
            throw new IllegalStateException("Falta R2_CDN_DOMAIN (cloudflare.r2.cdn-domain): "
                    + "sin ella no se pueden armar las URL públicas de R2");
        }
        this.cdnDomain = domain.trim();
    }

    /** Calificador obligatorio en MapStruct: sin él se aplicaría a toda propiedad String. */
    @Named("publicUrl")
    public String toPublicUrl(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        return String.format("https://%s/public/%s", cdnDomain, objectKey);
    }
}
