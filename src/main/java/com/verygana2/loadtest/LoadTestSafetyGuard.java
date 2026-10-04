package com.verygana2.loadtest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * Tumba el arranque si el modo prueba de carga podría tocar un tercero real:
 * perfil {@code beta} o {@code dev} activo, una URL saliente con dominio real, o una
 * credencial que no empieza por {@code loadtest-} (por ejemplo, variables reales
 * exportadas por error: el binding relajado de Spring las pondría por encima del yml).
 *
 * <p>Los mensajes nombran la clave, nunca su valor.
 *
 * <p>Es un {@link EnvironmentPostProcessor} (registrado en {@code META-INF/spring.factories}):
 * corre con el entorno ya cargado pero antes de crear el contexto, así que Flyway, los
 * {@code @Scheduled} y los clientes salientes (R2, Wompi, ZapSign) no llegan a arrancar con una
 * configuración insegura. Solo actúa con el perfil {@code loadtest}; en dev, prod y beta no hace nada.
 */
public class LoadTestSafetyGuard implements EnvironmentPostProcessor, Ordered {

    static final String PROFILE = "loadtest";

    static final String CREDENTIAL_PREFIX = "loadtest-";

    /** Perfiles incompatibles con la prueba de carga. */
    static final List<String> FORBIDDEN_PROFILES = List.of("beta", "dev");

    /** Dominios de terceros reales que ninguna URL de la prueba puede contener. */
    static final List<String> REAL_DOMAINS = List.of(
            "wompi.co", "zapsign.com.br", "random.org", "r2.cloudflarestorage.com",
            "twilio.com", "sendgrid.com");

    /** URLs salientes. Las de ZapSign salen de {@code loadtest.stubs.base-url}. */
    static final List<String> URL_KEYS = List.of(
            "loadtest.stubs.base-url",
            "wompi.api-base-url",
            "wompi.payout.api-base-url",
            "randomOrg.api-url",
            "cloudflare.r2.endpoint");

    /** Credenciales de terceros que tienen que ser literales {@code loadtest-...}. */
    static final List<String> CREDENTIAL_KEYS = List.of(
            "wompi.public-key", "wompi.private-key", "wompi.integrity-secret", "wompi.events-key",
            "wompi.payout.api-key", "wompi.payout.events-key",
            "zapsign.api-token", "zapsign.webhook-secret",
            "randomOrg.api-key",
            "twilio.account-sid", "twilio.auth-token",
            "sendgrid.api-key",
            "cloudflare.r2.access-key-id", "cloudflare.r2.secret-access-key",
            "recaptcha.secret-key");

    private final Log log;

    public LoadTestSafetyGuard(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(LoadTestSafetyGuard.class);
    }

    /** Después de la carga de los archivos de configuración (application*.yml) y de las variables. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains(PROFILE)) {
            return;
        }
        validate(environment);
        log.warn("Modo prueba de carga activo: terceros simulados, credenciales y URLs verificadas");
    }

    void validate(Environment environment) {
        List<String> problems = new ArrayList<>();

        List<String> active = Arrays.asList(environment.getActiveProfiles());
        for (String profile : FORBIDDEN_PROFILES) {
            if (active.contains(profile)) {
                problems.add("el perfil '" + profile + "' no puede combinarse con loadtest");
            }
        }

        for (String key : URL_KEYS) {
            String url = environment.getProperty(key);
            if (url == null || url.isBlank()) {
                // Un endpoint de R2 vacío hace que R2Config construya el del R2 real.
                problems.add(key + " está vacía: debe apuntar a un simulador");
                continue;
            }
            String lower = url.toLowerCase(Locale.ROOT);
            for (String domain : REAL_DOMAINS) {
                if (lower.contains(domain)) {
                    problems.add(key + " apunta a un dominio real (" + domain + ")");
                }
            }
        }

        for (String key : CREDENTIAL_KEYS) {
            String value = environment.getProperty(key);
            if (value == null || !value.startsWith(CREDENTIAL_PREFIX)) {
                problems.add(key + " no empieza por '" + CREDENTIAL_PREFIX + "' (¿credencial real?)");
            }
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "LoadTestSafetyGuard: configuración insegura para la prueba de carga: "
                            + String.join("; ", problems));
        }
    }
}
