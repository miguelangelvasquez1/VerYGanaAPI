package com.verygana2.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("LoadTestSafetyGuard - ningún tercero real recibe la prueba de carga")
class LoadTestSafetyGuardTest {

    private static final String SECRET_VALUE = "sk_live_super-secreto-123";

    /** Configuración válida con los hosts de compose (fase local). */
    private static MockEnvironment stubEnvironment() {
        return environment("http://wiremock:8080", "http://minio:9000");
    }

    private static MockEnvironment environment(String stubHost, String s3Host) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "loadtest");
        env.setProperty("loadtest.stubs.base-url", stubHost);
        env.setProperty("wompi.api-base-url", stubHost + "/wompi/v1");
        env.setProperty("wompi.payout.api-base-url", stubHost + "/wompi-payouts/v1");
        env.setProperty("randomOrg.api-url", stubHost + "/random/json-rpc/4/invoke");
        env.setProperty("cloudflare.r2.endpoint", s3Host);
        for (String key : LoadTestSafetyGuard.CREDENTIAL_KEYS) {
            env.setProperty(key, "loadtest-" + key.replace('.', '-'));
        }
        return env;
    }

    private static void validate(MockEnvironment env) {
        new LoadTestSafetyGuard(env).validate();
    }

    @Test
    @DisplayName("rechaza una URL real de Wompi (pagos o payouts)")
    void rejectsRealWompiUrl() {
        MockEnvironment pay = stubEnvironment();
        pay.setProperty("wompi.api-base-url", "https://production.wompi.co/v1");
        assertThatThrownBy(() -> validate(pay))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wompi.api-base-url");

        MockEnvironment payout = stubEnvironment();
        payout.setProperty("wompi.payout.api-base-url", "https://api.sandbox.payouts.wompi.co/v1");
        assertThatThrownBy(() -> validate(payout))
                .hasMessageContaining("wompi.payout.api-base-url");
    }

    @Test
    @DisplayName("rechaza ZapSign, Random.org o R2 reales")
    void rejectsRealZapSignOrR2Endpoint() {
        MockEnvironment zap = stubEnvironment();
        zap.setProperty("loadtest.stubs.base-url", "https://sandbox.api.zapsign.com.br");
        assertThatThrownBy(() -> validate(zap)).hasMessageContaining("loadtest.stubs.base-url");

        MockEnvironment r2 = stubEnvironment();
        r2.setProperty("cloudflare.r2.endpoint", "https://abc123.r2.cloudflarestorage.com");
        assertThatThrownBy(() -> validate(r2)).hasMessageContaining("cloudflare.r2.endpoint");

        MockEnvironment random = stubEnvironment();
        random.setProperty("randomOrg.api-url", "https://api.random.org/json-rpc/4/invoke");
        assertThatThrownBy(() -> validate(random)).hasMessageContaining("randomOrg.api-url");
    }

    @Test
    @DisplayName("un endpoint de R2 vacío también se rechaza: R2Config caería en el R2 real")
    void rejectsBlankR2Endpoint() {
        MockEnvironment env = stubEnvironment();
        env.setProperty("cloudflare.r2.endpoint", "");

        assertThatThrownBy(() -> validate(env)).hasMessageContaining("cloudflare.r2.endpoint");
    }

    @Test
    @DisplayName("rechaza una credencial que no empieza por loadtest-")
    void rejectsCredentialWithoutLoadtestPrefix() {
        for (String key : LoadTestSafetyGuard.CREDENTIAL_KEYS) {
            MockEnvironment env = stubEnvironment();
            env.setProperty(key, "credencial-real");

            assertThatThrownBy(() -> validate(env))
                    .as(key)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(key);
        }
    }

    @Test
    @DisplayName("rechaza el perfil beta")
    void rejectsBetaProfile() {
        MockEnvironment env = stubEnvironment();
        env.setActiveProfiles("prod", "beta", "loadtest");

        assertThatThrownBy(() -> validate(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("beta");
    }

    @Test
    @DisplayName("rechaza el perfil dev")
    void rejectsDevProfile() {
        MockEnvironment env = stubEnvironment();
        env.setActiveProfiles("dev", "loadtest");

        assertThatThrownBy(() -> validate(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev");
    }

    @Test
    @DisplayName("acepta los simuladores, con hosts de compose y con IP privada")
    void acceptsStubConfiguration() {
        assertThatCode(() -> validate(stubEnvironment())).doesNotThrowAnyException();
        assertThatCode(() -> validate(environment("http://10.0.1.25:8089", "http://10.0.1.25:9000")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el mensaje nombra la clave pero no imprime su valor")
    void errorMessageNamesTheKeyButNotItsValue() {
        MockEnvironment env = stubEnvironment();
        env.setProperty("wompi.private-key", SECRET_VALUE);
        env.setProperty("twilio.auth-token", SECRET_VALUE);

        assertThatThrownBy(() -> validate(env))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage())
                        .contains("wompi.private-key")
                        .contains("twilio.auth-token")
                        .doesNotContain(SECRET_VALUE)
                        .doesNotContain("super-secreto"));
    }
}
