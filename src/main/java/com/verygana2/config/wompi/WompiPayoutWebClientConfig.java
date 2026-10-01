package com.verygana2.config.wompi;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

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
        WompiPayoutConfig.Payout payoutConfig = wompiPayoutConfig.getPayout();

        // Sin esto, un Wompi colgado deja el .block() de WompiPayoutClient esperando
        // indefinidamente — grave en processScheduledPayouts(), que procesa ~100
        // payouts secuenciales: uno solo trabado detiene a todos los que le siguen.
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, payoutConfig.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(payoutConfig.getResponseTimeoutMs()));

        return WebClient.builder()
                .baseUrl(wompiPayoutConfig.getApiBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("x-api-key", wompiPayoutConfig.getApiKey())
                .defaultHeader("user-principal-id", wompiPayoutConfig.getPrincipalUserId())
                .defaultHeader("user-id", wompiPayoutConfig.getUserId())
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("[WOMPI PAYOUT REQUEST] {} {}", request.method(), request.url());
            request.headers().forEach((name, values) -> {
                String value = String.join(",", values);
                if ("x-api-key".equalsIgnoreCase(name)) {
                    value = mask(value);
                }
                log.debug("[WOMPI PAYOUT REQUEST HEADER] {}: {}", name, value);
            });
            return Mono.just(request);
        });
    }

    /** Enmascara el valor de un header sensible, mostrando solo los extremos para poder cotejarlo con .env. */
    private String mask(String value) {
        if (value == null || value.length() <= 8) return "***";
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("[WOMPI PAYOUT RESPONSE] status={}", response.statusCode());
            return Mono.just(response);
        });
    }
}
