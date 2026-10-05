package com.verygana2.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.SpringFactoriesLoader;
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
        new LoadTestSafetyGuard(guardLogs()).validate(env);
    }

    private static DeferredLogFactory guardLogs() {
        return logSupplier -> logSupplier.get();
    }

    /** Se marca si Spring llegó a crear beans: el guardián tiene que fallar antes. */
    static final AtomicBoolean BEAN_CREATED = new AtomicBoolean();

    /** Sin @Configuration a propósito: así los escaneos de componentes de otros tests no la recogen. */
    static class ProbeConfig {
        @Bean
        Object probeBean() {
            BEAN_CREATED.set(true);
            return new Object();
        }
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

    @Test
    @DisplayName("está registrado como EnvironmentPostProcessor: corre antes de Flyway, los @Scheduled y los clientes")
    void isRegisteredAsEnvironmentPostProcessor() {
        var registered = SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class, getClass().getClassLoader());

        assertThat(registered).contains(LoadTestSafetyGuard.class.getName());
    }

    @Test
    @DisplayName("sin el perfil loadtest no hace nada, aunque la configuración sea insegura")
    void doesNothingWithoutLoadTestProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("wompi.api-base-url", "https://production.wompi.co/v1");

        assertThatCode(() -> new LoadTestSafetyGuard(guardLogs()).postProcessEnvironment(env, new SpringApplication()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("con loadtest y configuración insegura tumba el arranque antes de crear ningún bean")
    void failsBeforeAnyBeanIsCreated() {
        BEAN_CREATED.set(false);
        SpringApplication app = new SpringApplication(ProbeConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles("loadtest");

        assertThatThrownBy(() -> app.run("--wompi.api-base-url=https://production.wompi.co/v1",
                "--spring.main.banner-mode=off"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wompi.api-base-url");
        assertThat(BEAN_CREATED).as("el guardián corrió después de crear beans").isFalse();
    }
}
