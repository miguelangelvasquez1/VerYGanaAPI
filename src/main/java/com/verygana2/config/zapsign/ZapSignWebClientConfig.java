package com.verygana2.config.zapsign;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Configura el WebClient que usa ZapSignClient para todas las llamadas a la API de ZapSign.
 * Ver: https://docs.zapsign.com.br/espanol
 *
 * Sandbox y producción son DOS APIs distintas en ZapSign, con base URL propia cada una —
 * un token de sandbox contra la URL de producción (o viceversa) devuelve 403. Por eso la
 * URL se deriva acá del flag zapsign.sandbox en vez de configurarse aparte: así no se
 * puede desincronizar token/ambiente por error.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ZapSignWebClientConfig {

    private static final String SANDBOX_BASE_URL = "https://sandbox.api.zapsign.com.br";
    private static final String PRODUCTION_BASE_URL = "https://api.zapsign.com.br";

    private final ZapSignConfig zapSignConfig;

    @Bean(name = "zapSignWebClient")
    public WebClient zapSignWebClient() {
        String baseUrl = zapSignConfig.isSandbox() ? SANDBOX_BASE_URL : PRODUCTION_BASE_URL;
        log.info("[ZAPSIGN] WebClient apuntando a {} (sandbox={})", baseUrl, zapSignConfig.isSandbox());
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Authorization", "Bearer " + zapSignConfig.getApiToken())
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("[ZAPSIGN REQUEST] {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("[ZAPSIGN RESPONSE] status={}", response.statusCode());
            return Mono.just(response);
        });
    }
}
