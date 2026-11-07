package com.verygana2.storage.config;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Tumba el arranque en prod (y por tanto en beta, que se monta encima) si el dominio del CDN
 * ({@code R2_CDN_DOMAIN} / {@code cloudflare.r2.cdn-domain}) falta o está en blanco.
 *
 * <p>Es un {@link EnvironmentPostProcessor} (registrado en {@code META-INF/spring.factories}):
 * falla antes de crear el contexto, así que Flyway, los {@code @Scheduled} y los clientes de R2
 * no llegan a arrancar. El mensaje nombra la variable y nunca un valor del entorno.
 * En dev no actúa: allí la variable ya es obligatoria y {@code PublicUrlBuilder} es la red.
 */
public class CdnDomainStartupGuard implements EnvironmentPostProcessor, Ordered {

    static final String PROFILE = "prod";

    static final String KEY = "cloudflare.r2.cdn-domain";

    static final String MESSAGE = "Falta R2_CDN_DOMAIN (" + KEY + "): "
            + "en prod y beta la API no arranca sin el dominio del CDN de R2";

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
        String domain;
        try {
            domain = environment.getProperty(KEY);
        } catch (IllegalArgumentException e) {
            // Placeholder sin valor (por ejemplo, ${R2_CDN_DOMAIN} sin default): no se propaga la causa
            // para no arrastrar texto del entorno al mensaje.
            throw new IllegalStateException(MESSAGE);
        }
        if (domain == null || domain.isBlank()) {
            throw new IllegalStateException(MESSAGE);
        }
    }
}
