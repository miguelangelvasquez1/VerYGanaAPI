package com.verygana2.config.wompi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Configura el WebClient para la API de Pagos a Terceros de Wompi
 * (api.payouts.wompi.co), independiente del WebClient de cobros
 * (wompiWebClient) porque es un producto/host distinto con su propia
 * autenticación.
 *
 * Headers confirmados contra el spec público de SwaggerHub
 * (https://app.swaggerhub.com/apis-docs/wompi/Payouts/1.0.0): la API espera
 * "x-api-key" y "user-principal-id" en minúscula, sin esquema "Bearer".
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WompiPayoutWebClientConfig {

    private final WompiPayoutConfig wompiPayoutConfig;

    @Bean(name = "wompiPayoutWebClient")
    public WebClient wompiPayoutWebClient() {
        return WebClient.builder()
                .baseUrl(wompiPayoutConfig.getApiBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("x-api-key", wompiPayoutConfig.getApiKey())
                .defaultHeader("user-principal-id", wompiPayoutConfig.getPrincipalUserId())
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("[WOMPI PAYOUT REQUEST] {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("[WOMPI PAYOUT RESPONSE] status={}", response.statusCode());
            return Mono.just(response);
        });
    }
}
