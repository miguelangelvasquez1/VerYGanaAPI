package com.verygana2.config.kushki;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KushkiWebClientConfig {

    private final KushkiConfig kushkiConfig;

    @Bean(name = "kushkiWebClient")
    public WebClient kushkiWebClient() {
        return WebClient.builder()
                .baseUrl(kushkiConfig.getApiBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                // Kushki usa Private-Merchant-Id para autenticar llamadas del backend
                .defaultHeader("Private-Merchant-Id", kushkiConfig.getPrivateKey())
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("[KUSHKI REQUEST] {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("[KUSHKI RESPONSE] status={}", response.statusCode());
            return Mono.just(response);
        });
    }
}
