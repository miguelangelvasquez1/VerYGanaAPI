package com.verygana2.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Orígenes autorizados a llamar a la API desde un navegador (CORS y WebSocket).
 * Se llena desde {@code app.cors.allowed-origins}, que a su vez lee la variable de
 * entorno {@code CORS_ALLOWED_ORIGINS} (lista separada por comas).
 */
@Component
@ConfigurationProperties(prefix = "app.cors")
@Data
public class CorsProperties {

    /**
     * Esquema + host + puerto, sin "/" final: {@code https://verygana.com}.
     * Admite patrones ({@code https://*.verygana.com}). Vacío = ningún origen
     * cruzado (fail-closed).
     */
    private List<String> allowedOrigins = new ArrayList<>();
}
