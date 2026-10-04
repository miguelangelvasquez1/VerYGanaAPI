package com.verygana2.loadtest.stubs;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.verygana2.config.zapsign.ZapSignConfig;
import com.verygana2.loadtest.LoadTestProperties;

import io.netty.channel.ChannelOption;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@DisplayName("LoadTestStubClientsConfig - cliente de ZapSign contra el simulador")
class LoadTestStubClientsConfigTest {

    private LoadTestStubClientsConfig config;

    @BeforeEach
    void setUp() {
        LoadTestProperties properties = new LoadTestProperties();
        properties.getStubs().setBaseUrl("http://wiremock:8080");
        ZapSignConfig zapSignConfig = new ZapSignConfig();
        zapSignConfig.setApiToken("loadtest-zapsign-token");
        config = new LoadTestStubClientsConfig(properties, zapSignConfig);
    }

    @Test
    @DisplayName("las peticiones salen hacia el simulador, bajo /zapsign, con el token falso")
    void zapSignClientTargetsStub() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction fake = request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(org.springframework.http.HttpStatus.OK).build());
        };

        WebClient client = config.zapSignWebClientBuilder().exchangeFunction(fake).build();
        client.get().uri("/api/v1/docs/").retrieve().toBodilessEntity().block();

        assertThat(captured.get().url()).isEqualTo(URI.create("http://wiremock:8080/zapsign/api/v1/docs/"));
        assertThat(captured.get().headers().getFirst("Authorization")).isEqualTo("Bearer loadtest-zapsign-token");
    }

    @Test
    @DisplayName("el cliente tiene timeout de conexión (2 s) y de respuesta (5 s)")
    void zapSignClientHasResponseTimeout() {
        HttpClient httpClient = LoadTestStubClientsConfig.stubHttpClient();

        assertThat(httpClient.configuration().responseTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(httpClient.configuration().options().get(ChannelOption.CONNECT_TIMEOUT_MILLIS)).isEqualTo(2_000);
    }

    @Test
    @DisplayName("el builder de ZapSign usa de verdad el HttpClient con timeouts (si se quita el clientConnector, falla)")
    void zapSignBuilderUsesTheHttpClientWithTimeouts() {
        WebClient.Builder builder = config.zapSignWebClientBuilder();

        // El conector no tiene getter público: se lee el campo, que es lo que `build()` le pasa a Reactor Netty.
        Object connector = ReflectionTestUtils.getField(builder, "connector");
        assertThat(connector).as("el builder no tiene conector: sin timeouts").isInstanceOf(ReactorClientHttpConnector.class);
        HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(connector, "httpClient");

        assertThat(httpClient).isNotNull();
        assertThat(httpClient.configuration().responseTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(httpClient.configuration().options().get(ChannelOption.CONNECT_TIMEOUT_MILLIS)).isEqualTo(2_000);
    }
}
