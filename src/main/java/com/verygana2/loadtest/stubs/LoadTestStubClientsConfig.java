package com.verygana2.loadtest.stubs;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.verygana2.config.zapsign.ZapSignConfig;
import com.verygana2.loadtest.LoadTestProperties;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

/**
 * Clientes HTTP de la prueba de carga que apuntan a WireMock. Wompi y Random.org no
 * necesitan bean: su URL ya es una propiedad y {@code application-loadtest.yml} la sobrescribe.
 *
 * <p>En {@code loadtest} el bean {@code zapSignWebClient} sale de aquí; el de
 * {@code ZapSignWebClientConfig} lleva {@code @Profile("!loadtest")}.
 */
@Configuration
@Profile("loadtest")
public class LoadTestStubClientsConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);

    private final LoadTestProperties properties;
    private final ZapSignConfig zapSignConfig;

    public LoadTestStubClientsConfig(LoadTestProperties properties, ZapSignConfig zapSignConfig) {
        this.properties = properties;
        this.zapSignConfig = zapSignConfig;
    }

    @Bean(name = "zapSignWebClient")
    public WebClient zapSignWebClient() {
        return zapSignWebClientBuilder().build();
    }

    WebClient.Builder zapSignWebClientBuilder() {
        return WebClient.builder()
                .baseUrl(properties.getStubs().getBaseUrl() + "/zapsign")
                .clientConnector(new ReactorClientHttpConnector(stubHttpClient()))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Authorization", "Bearer " + zapSignConfig.getApiToken());
    }

    /** Conexión 2 s, respuesta 5 s: lo mismo que se pide a ZapSign (plan, 6.1). */
    static HttpClient stubHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .responseTimeout(RESPONSE_TIMEOUT);
    }
}
