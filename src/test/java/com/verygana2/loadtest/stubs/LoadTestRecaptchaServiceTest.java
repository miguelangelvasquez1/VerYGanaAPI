package com.verygana2.loadtest.stubs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("LoadTestRecaptchaService - reCAPTCHA falso de la prueba de carga")
class LoadTestRecaptchaServiceTest {

    private static final String PLACEHOLDER = "loadtest-placeholder";

    private MockRestServiceServer server;
    private SimpleMeterRegistry registry;
    private LoadTestRecaptchaService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        registry = new SimpleMeterRegistry();
        service = new LoadTestRecaptchaService(builder, new StubCallCounter(registry));
    }

    @Test
    @DisplayName("acepta el token de relleno, que Google rechazaría, en cualquier acción")
    void acceptsPlaceholderToken() {
        assertThat(service.verify(PLACEHOLDER, "login")).isTrue();
        assertThat(service.verify(PLACEHOLDER, "register_consumer")).isTrue();
        assertThat(service.verify(PLACEHOLDER, "register_commercial")).isTrue();
    }

    @Test
    @DisplayName("un token vacío o nulo sigue rechazándose")
    void blankTokenIsRejected() {
        assertThat(service.verify("", "login")).isFalse();
        assertThat(service.verify("  ", "login")).isFalse();
        assertThat(service.verify(null, "login")).isFalse();
    }

    @Test
    @DisplayName("nunca llama a Google: el servidor simulado no tiene expectativas")
    void neverCallsGoogle() {
        service.verify(PLACEHOLDER, "login");

        // Sin expectativas: cualquier petición HTTP habría fallado con AssertionError.
        server.verify();
    }

    @Test
    @DisplayName("cuenta cada verificación como llamada a recaptcha")
    void countsCalls() {
        service.verify(PLACEHOLDER, "login");
        service.verify(PLACEHOLDER, "login");

        assertThat(registry.get("loadtest.stub.calls").tag("provider", "recaptcha").counter().count())
                .isEqualTo(2.0);
    }
}
