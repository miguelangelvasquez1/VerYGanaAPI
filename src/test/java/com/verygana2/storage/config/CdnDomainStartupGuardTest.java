package com.verygana2.storage.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("CdnDomainStartupGuard - prod y beta no arrancan sin R2_CDN_DOMAIN")
class CdnDomainStartupGuardTest {

    private static final String KEY = "cloudflare.r2.cdn-domain";

    private static void run(MockEnvironment env) {
        new CdnDomainStartupGuard().postProcessEnvironment(env, new SpringApplication());
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
    @DisplayName("prod sin la clave tumba el arranque y nombra la variable")
    void failsInProdWithoutCdnDomain() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> run(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R2_CDN_DOMAIN");
    }

    @Test
    @DisplayName("prod,beta con el dominio en blanco tumba el arranque")
    void failsInProdBetaWithBlankCdnDomain() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "beta");
        env.setProperty(KEY, "  ");

        assertThatThrownBy(() -> run(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R2_CDN_DOMAIN");
    }

    @Test
    @DisplayName("un placeholder sin valor también tumba el arranque")
    void failsWhenPlaceholderCannotBeResolved() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty(KEY, "${R2_CDN_DOMAIN}");

        assertThatThrownBy(() -> run(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R2_CDN_DOMAIN");
    }

    @Test
    @DisplayName("el mensaje nombra la variable pero ningún valor del entorno")
    void messageNamesVariableButNoValues() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("cloudflare.r2.secret-access-key", "secreto-de-mentira-1");
        env.setProperty("cloudflare.r2.account-id", "cuenta-de-mentira-2");
        env.setProperty("wompi.private-key", "llave-de-mentira-3");

        assertThatThrownBy(() -> run(env))
                .hasMessageContaining("R2_CDN_DOMAIN")
                .hasMessageContaining(KEY)
                .hasMessageNotContaining("secreto-de-mentira-1")
                .hasMessageNotContaining("cuenta-de-mentira-2")
                .hasMessageNotContaining("llave-de-mentira-3");
    }

    @Test
    @DisplayName("prod,beta con el dominio configurado arranca")
    void acceptsConfiguredDomainInProdBeta() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "beta");
        env.setProperty(KEY, "cdn-beta.verygana.com");

        assertThatCode(() -> run(env)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sin el perfil prod no hace nada, aunque la clave esté vacía")
    void doesNothingWithoutProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        env.setProperty(KEY, "");

        assertThatCode(() -> run(env)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("está registrado como EnvironmentPostProcessor")
    void isRegisteredAsEnvironmentPostProcessor() {
        var registered = SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class, getClass().getClassLoader());

        assertThat(registered).contains(CdnDomainStartupGuard.class.getName());
    }

    @Test
    @DisplayName("con prod y sin dominio tumba el arranque antes de crear ningún bean")
    void failsBeforeAnyBeanIsCreated() {
        BEAN_CREATED.set(false);
        SpringApplication app = new SpringApplication(ProbeConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles("prod");

        // El argumento vacío gana a una R2_CDN_DOMAIN exportada en el shell de quien corre el test.
        assertThatThrownBy(() -> app.run("--cloudflare.r2.cdn-domain=", "--spring.main.banner-mode=off"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R2_CDN_DOMAIN");
        assertThat(BEAN_CREATED).as("el guardián corrió después de crear beans").isFalse();
    }

    @Test
    @DisplayName("con el dominio configurado el contexto arranca")
    void contextStartsWhenDomainConfigured() {
        BEAN_CREATED.set(false);
        SpringApplication app = new SpringApplication(ProbeConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles("prod");

        try (var ctx = app.run("--cloudflare.r2.cdn-domain=cdn-beta.verygana.com", "--spring.main.banner-mode=off")) {
            assertThat(ctx.isActive()).isTrue();
        }
        assertThat(BEAN_CREATED).isTrue();
    }
}
