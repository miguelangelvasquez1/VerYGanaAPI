package com.verygana2.loadtest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Tumba el arranque si el modo prueba de carga podría tocar un tercero real:
 * perfil {@code beta} o {@code dev} activo, una URL saliente con dominio real, o una
 * credencial que no empieza por {@code loadtest-} (por ejemplo, variables reales
 * exportadas por error: el binding relajado de Spring las pondría por encima del yml).
 *
 * <p>Los mensajes nombran la clave, nunca su valor.
 */
@Slf4j
@Component
@Profile("loadtest")
@Order(0)
public class LoadTestSafetyGuard implements ApplicationRunner {

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

    private final Environment environment;

    public LoadTestSafetyGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
        log.warn("Modo prueba de carga activo: terceros simulados, credenciales y URLs verificadas");
    }

    void validate() {
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
