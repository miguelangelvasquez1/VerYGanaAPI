package com.verygana2.config.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La puerta de {@code /actuator/prometheus}. Es el único endpoint de la app que se autentica
 * con un secreto de larga vida en vez de un JWT, así que su lógica de decisión no la cubre
 * ningún test de {@code SecurityConfig}.
 *
 * Lo que sí queda fuera de este test, y hay que revisar a mano si se toca la cadena: que
 * {@code @Order(0)} siga poniendo esta cadena antes que la de {@code SecurityConfig}, y que
 * {@code /actuator/health} siga siendo público. Eso vive en el {@code SecurityFilterChain},
 * que necesita contexto de Spring para probarse.
 */
@DisplayName("ActuatorSecurityConfig — token de scrapeo")
class ActuatorSecurityConfigTest {

    private static final String TOKEN = "s3cr3t0-de-scrapeo";

    private static MockHttpServletRequest withAuthorization(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        if (value != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, value);
        }
        return request;
    }

    @Nested
    @DisplayName("con un token configurado")
    class ConToken {

        private final ActuatorSecurityConfig config = new ActuatorSecurityConfig(TOKEN);

        @Test
        @DisplayName("acepta el Bearer correcto")
        void aceptaElTokenCorrecto() {
            assertThat(config.hasValidScrapeToken(withAuthorization("Bearer " + TOKEN))).isTrue();
        }

        @Test
        @DisplayName("rechaza un token distinto")
        void rechazaOtroToken() {
            assertThat(config.hasValidScrapeToken(withAuthorization("Bearer otro-token"))).isFalse();
        }

        @Test
        @DisplayName("rechaza un token que solo comparte el prefijo")
        void rechazaPrefijo() {
            // MessageDigest.isEqual compara longitud además de contenido: un token truncado
            // no puede pasar por coincidencia parcial.
            assertThat(config.hasValidScrapeToken(withAuthorization("Bearer " + TOKEN.substring(0, 5))))
                    .isFalse();
            assertThat(config.hasValidScrapeToken(withAuthorization("Bearer " + TOKEN + "-extra")))
                    .isFalse();
        }

        @Test
        @DisplayName("rechaza si no hay cabecera Authorization")
        void rechazaSinCabecera() {
            assertThat(config.hasValidScrapeToken(withAuthorization(null))).isFalse();
        }

        @Test
        @DisplayName("rechaza otros esquemas de autenticación")
        void rechazaOtroEsquema() {
            // Prometheus con `authorization: type: Basic` mandaría esto. No debe pasar:
            // el token viajaría en base64 y la comparación se haría contra otra cosa.
            assertThat(config.hasValidScrapeToken(withAuthorization("Basic " + TOKEN))).isFalse();
            assertThat(config.hasValidScrapeToken(withAuthorization("bearer " + TOKEN))).isFalse();
            assertThat(config.hasValidScrapeToken(withAuthorization(TOKEN))).isFalse();
        }
    }

    @Nested
    @DisplayName("sin token configurado (la clave falta en el perfil)")
    class SinToken {

        @Test
        @DisplayName("cierra el endpoint en vez de dejarlo abierto")
        void fallaCerrado() {
            // El escenario real: application-prod.yml sin observability.scrape-token. La app
            // arranca igual — no queremos tumbar producción por esto — pero las métricas de
            // negocio (montos de payouts, balance de Wompi) NO quedan expuestas.
            for (String value : new String[] {null, "", "   "}) {
                ActuatorSecurityConfig config = new ActuatorSecurityConfig(value);
                assertThat(config.hasValidScrapeToken(withAuthorization("Bearer lo-que-sea")))
                        .as("token configurado = %s", value)
                        .isFalse();
                assertThat(config.hasValidScrapeToken(withAuthorization(null))).isFalse();
            }
        }
    }
}
