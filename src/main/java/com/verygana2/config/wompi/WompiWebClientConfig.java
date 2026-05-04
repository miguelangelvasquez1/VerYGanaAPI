package com.verygana2.config.wompi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Configura el WebClient que usa WompiClient para todas las llamadas a la API de Wompi.
 *
 * El WebClient está preconfigurado con:
 * - Base URL de la API (sandbox o producción según el perfil activo)
 * - Header de autenticación Bearer con la private key
 * - Header Content-Type: application/json
 * - Logging de requests y responses para depuración (solo en nivel DEBUG)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WompiWebClientConfig {

    private final WompiConfig wompiConfig;

    @Bean(name = "wompiWebClient")
    public WebClient wompiWebClient() {
        return WebClient.builder()
                .baseUrl(wompiConfig.getApiBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                // Wompi usa Bearer token con la private key para autenticar llamadas del backend
                .defaultHeader("Authorization", "Bearer " + wompiConfig.getPrivateKey())
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    /**
     * Loguea cada request saliente hacia Wompi en nivel DEBUG.
     * En producción el nivel root es INFO, así que estos logs no aparecen
     * a menos que se active explícitamente para depuración.
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("[WOMPI REQUEST] {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    /**
     * Loguea cada response recibida de Wompi en nivel DEBUG.
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("[WOMPI RESPONSE] status={}", response.statusCode());
            return Mono.just(response);
        });
    }
}
