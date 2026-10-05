package com.verygana2.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.verygana2.loadtest.stubs.LoadTestRecaptchaService;
import com.verygana2.testsupport.LoadTestWiring;
import com.verygana2.loadtest.stubs.LoadTestSmsService;
import com.verygana2.security.recaptcha.RecaptchaService;
import com.verygana2.services.TwilioSmsServiceImpl;
import com.verygana2.services.interfaces.TwilioSmsService;


/**
 * Contexto mínimo (sin {@code @SpringBootTest} ni BD) que escanea el paquete
 * {@code loadtest} junto a las piezas reales que los falsos reemplazan, y comprueba
 * qué queda registrado según el perfil.
 */
@DisplayName("Aislamiento por perfil: nada de loadtest en dev, prod ni beta")
class LoadTestProfileIsolationTest {

    /** Registrada con @Import: el nombre del bean es el de la clase completa. */
    private static final String ZAPSIGN_REAL_CONFIG = "com.verygana2.config.zapsign.ZapSignWebClientConfig";

    private static ApplicationContextRunner runner(String... profiles) {
        return new ApplicationContextRunner()
                .withUserConfiguration(LoadTestWiring.class)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profiles))
                .withPropertyValues(
                        "recaptcha.secret-key=clave",
                        "twilio.verify-service-sid=sid",
                        "twilio.phone-number=+570000000000",
                        "zapsign.api-token=token",
                        "loadtest.stubs.base-url=http://wiremock:8080");
    }

    static Stream<List<String>> deployableProfiles() {
        return Stream.of(List.of("dev"), List.of("prod"), List.of("prod", "beta"));
    }

    private static void assertNoStubs(ApplicationContextRunner runner) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            // reCAPTCHA y Twilio: las clases reales, no los falsos.
            assertThat(context.getBean(RecaptchaService.class).getClass()).isEqualTo(RecaptchaService.class);
            assertThat(context.getBean(TwilioSmsService.class).getClass()).isEqualTo(TwilioSmsServiceImpl.class);
            // Ningún bean, de ningún tipo, sale del paquete loadtest.
            List<String> fromLoadTest = Stream.of(context.getBeanDefinitionNames())
                    .filter(name -> {
                        var type = context.getType(name);
                        return type != null && type.getName().startsWith("com.verygana2.loadtest");
                    })
                    .toList();
            assertThat(fromLoadTest).isEmpty();
            // Y el cliente de ZapSign es el de ZapSignWebClientConfig.
            assertThat(context.getBeanFactory().getBeanDefinition("zapSignWebClient").getFactoryBeanName())
                    .isEqualTo(ZAPSIGN_REAL_CONFIG);
        });
    }

    @Test
    @DisplayName("dev usa el reCAPTCHA real y ningún falso")
    void devUsesRealRecaptchaAndNoStubs() {
        assertNoStubs(runner("dev"));
    }

    @Test
    @DisplayName("prod usa el reCAPTCHA real y ningún falso")
    void prodUsesRealRecaptchaAndNoStubs() {
        assertNoStubs(runner("prod"));
    }

    @Test
    @DisplayName("prod+beta usa el reCAPTCHA real y ningún falso")
    void prodBetaUsesRealRecaptchaAndNoStubs() {
        assertNoStubs(runner("prod", "beta"));
    }

    @Test
    @DisplayName("fuera de loadtest el cliente de ZapSign es el real")
    void realZapSignClientOutsideLoadTest() {
        deployableProfiles().forEach(profiles -> runner(profiles.toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanFactory().getBeanDefinition("zapSignWebClient").getFactoryBeanName())
                    .as("perfiles %s", profiles)
                    .isEqualTo(ZAPSIGN_REAL_CONFIG);
            assertThat(context).doesNotHaveBean(LoadTestProperties.class);
        }));
    }

    @Test
    @DisplayName("con prod+loadtest gana el reCAPTCHA y el SMS falsos, y ZapSign va al simulador")
    void loadTestProfileUsesFakeRecaptcha() {
        runner("prod", "loadtest").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RecaptchaService.class)).isInstanceOf(LoadTestRecaptchaService.class);
            assertThat(context.getBean(TwilioSmsService.class)).isInstanceOf(LoadTestSmsService.class);
            assertThat(context.getBeanFactory().getBeanDefinition("zapSignWebClient").getFactoryBeanName())
                    .isEqualTo("loadTestStubClientsConfig");
            assertThat(context.getBeansOfType(org.springframework.web.reactive.function.client.WebClient.class))
                    .containsOnlyKeys("zapSignWebClient");
        });
    }
}
