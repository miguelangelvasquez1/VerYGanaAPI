package com.verygana2.exceptions.wompi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 429 (Too Many Requests) es un status 4xx, pero a diferencia de un 400 real
 * (dato inválido) es reintentable: el problema es volumen de requests, no la
 * solicitud en sí. Estos tests fijan esa clasificación.
 */
@DisplayName("WompiApiException")
class WompiApiExceptionTest {

    @Test
    @DisplayName("429: se clasifica como error de servidor (reintentable), no de cliente")
    void tooManyRequests_isServerErrorNotClientError() {
        WompiApiException e = new WompiApiException("rate limited", 429);

        assertThat(e.isServerError()).isTrue();
        assertThat(e.isClientError()).isFalse();
    }

    @Test
    @DisplayName("400: se clasifica como error de cliente (no reintentable sin corregir el dato)")
    void badRequest_isClientErrorNotServerError() {
        WompiApiException e = new WompiApiException("dato inválido", 400);

        assertThat(e.isClientError()).isTrue();
        assertThat(e.isServerError()).isFalse();
    }

    @Test
    @DisplayName("500: se clasifica como error de servidor (reintentable)")
    void internalServerError_isServerErrorNotClientError() {
        WompiApiException e = new WompiApiException("Wompi caído", 500);

        assertThat(e.isServerError()).isTrue();
        assertThat(e.isClientError()).isFalse();
    }
}
