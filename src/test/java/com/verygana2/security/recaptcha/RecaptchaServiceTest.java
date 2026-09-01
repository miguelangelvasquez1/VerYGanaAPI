package com.verygana2.security.recaptcha;

import java.net.UnknownHostException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests de {@link RecaptchaService}: el corto circuito cuando no hay token, las
 * cinco formas en que la respuesta de Google puede rechazar una verificación
 * (success, action, score) y qué pasa cuando Google no se puede alcanzar.
 *
 * Se usa {@link MockRestServiceServer} en lugar de mockear la cadena fluida de
 * {@link RestClient}: sigue siendo un test unitario (no levanta contexto de
 * Spring) pero además verifica el cuerpo que realmente se le manda a Google.
 */
@DisplayName("RecaptchaService")
class RecaptchaServiceTest {

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private static final String SECRET = "secreto-de-prueba";
    private static final String TOKEN = "token-de-prueba";
    private static final String ACTION = "register_commercial";

    private MockRestServiceServer server;
    private RecaptchaService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        service = new RecaptchaService(builder);
        ReflectionTestUtils.setField(service, "secretKey", SECRET);
        ReflectionTestUtils.setField(service, "minScore", 0.5);
    }

    /** Arma una respuesta de la API de Google. Cualquier campo en null se omite. */
    private static String googleResponse(Boolean success, String action, Double score) {
        StringBuilder json = new StringBuilder("{");
        if (success != null) {
            json.append("\"success\":").append(success);
        }
        if (action != null) {
            json.append(",\"action\":\"").append(action).append("\"");
        }
        if (score != null) {
            json.append(",\"score\":").append(score);
        }
        return json.append("}").toString();
    }

    private void expectGoogleReturns(String json) {
        server.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Nested
    @DisplayName("token ausente")
    class MissingToken {

        @Test
        @DisplayName("token null: retorna false sin llamar a Google")
        void nullToken_returnsFalseWithoutCallingGoogle() {
            assertThat(service.verify(null, ACTION)).isFalse();
            server.verify(); // no se registró ninguna expectativa: no hubo request
        }

        @Test
        @DisplayName("token en blanco: retorna false sin llamar a Google")
        void blankToken_returnsFalseWithoutCallingGoogle() {
            assertThat(service.verify("   ", ACTION)).isFalse();
            server.verify();
        }
    }

    @Nested
    @DisplayName("petición a Google")
    class Request {

        @Test
        @DisplayName("manda el secreto y el token como form-urlencoded")
        void sendsSecretAndTokenAsFormData() {
            server.expect(requestTo(VERIFY_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(content().string("secret=" + SECRET + "&response=" + TOKEN))
                    .andRespond(withSuccess(googleResponse(true, ACTION, 0.9), MediaType.APPLICATION_JSON));

            assertThat(service.verify(TOKEN, ACTION)).isTrue();
            server.verify();
        }
    }

    @Nested
    @DisplayName("respuesta de Google")
    class Response {

        @Test
        @DisplayName("success=false: retorna false")
        void successFalse_returnsFalse() {
            expectGoogleReturns(googleResponse(false, ACTION, 0.9));

            assertThat(service.verify(TOKEN, ACTION)).isFalse();
        }

        @Test
        @DisplayName("la acción recibida no es la esperada: retorna false")
        void actionMismatch_returnsFalse() {
            // Regresión: durante 8 días el backend verificó todo contra la acción
            // "login", así que los registros de comercial y consumidor fallaban.
            expectGoogleReturns(googleResponse(true, "login", 0.9));

            assertThat(service.verify(TOKEN, "register_commercial")).isFalse();
        }

        @Test
        @DisplayName("Google no devuelve acción: retorna false")
        void missingAction_returnsFalse() {
            expectGoogleReturns(googleResponse(true, null, 0.9));

            assertThat(service.verify(TOKEN, ACTION)).isFalse();
        }

        @Test
        @DisplayName("acción correcta y score sobre el mínimo: retorna true")
        void actionMatchesAndScoreAboveMin_returnsTrue() {
            expectGoogleReturns(googleResponse(true, ACTION, 0.9));

            assertThat(service.verify(TOKEN, ACTION)).isTrue();
        }

        @Test
        @DisplayName("score exactamente en el mínimo: retorna true")
        void scoreExactlyAtMin_returnsTrue() {
            expectGoogleReturns(googleResponse(true, ACTION, 0.5));

            assertThat(service.verify(TOKEN, ACTION)).isTrue();
        }

        @Test
        @DisplayName("score bajo el mínimo: retorna false")
        void scoreBelowMin_returnsFalse() {
            expectGoogleReturns(googleResponse(true, ACTION, 0.4));

            assertThat(service.verify(TOKEN, ACTION)).isFalse();
        }

        @Test
        @DisplayName("respuesta sin score: retorna false")
        void missingScore_returnsFalse() {
            expectGoogleReturns(googleResponse(true, ACTION, null));

            assertThat(service.verify(TOKEN, ACTION)).isFalse();
        }

        @Test
        @DisplayName("respuesta vacía: retorna false")
        void emptyBody_returnsFalse() {
            server.expect(requestTo(VERIFY_URL))
                    .andRespond(withNoContent());

            assertThat(service.verify(TOKEN, ACTION)).isFalse();
        }
    }

    @Nested
    @DisplayName("Google inalcanzable")
    class TransportFailure {

        @Test
        @DisplayName("falla de DNS: retorna false y bloquea el login")
        void dnsFailure_returnsFalse() {
            // Comportamiento actual: fail-closed. Un hipo de DNS deja a toda la
            // plataforma sin poder autenticarse. Si se decide fail-open ante
            // fallas de transporte, este test debe cambiar a isTrue().
            server.expect(requestTo(VERIFY_URL))
                    .andRespond(withException(
                            new UnknownHostException("Failed to resolve 'www.google.com'")));

            assertThat(service.verify(TOKEN, ACTION)).isFalse();
        }

        @Test
        @DisplayName("Google responde 500: retorna false")
        void serverError_returnsFalse() {
            server.expect(requestTo(VERIFY_URL))
                    .andRespond(withServerError());

            assertThat(service.verify(TOKEN, ACTION)).isFalse();
        }
    }
}
