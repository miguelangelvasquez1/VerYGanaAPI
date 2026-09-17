package com.verygana2.config.metrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;

/**
 * Tags comunes a toda métrica exportada. Se hace por código y no por
 * {@code management.metrics.tags.*} para que dev y prod compartan un único punto de verdad
 * y para poder derivar el perfil activo.
 */
@Configuration
public class MetricsConfig {

    /**
     * Techo de valores distintos de {@code uri} en {@code http.server.requests}.
     *
     * El proyecto tiene ~400 endpoints y todos entran plantillados
     * ({@code /users/{id}}, no {@code /users/42}), así que el conjunto es finito y este
     * tope no debería tocarse nunca. Está para el caso que sí explota: un endpoint que
     * escapa sin plantillar, o un escaneo automatizado que inventa rutas. Sin tope, cada
     * ruta nueva son 11 series permanentes en el backend de métricas.
     */
    static final int MAX_URI_TAGS = 150;

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonMetricsTags(
            @Value("${spring.application.name:VerYGanaAPI}") String application,
            @Value("${spring.profiles.active:default}") String profile) {

        return registry -> registry.config().commonTags(
                "application", application,
                "profile", profile);
    }

    /**
     * Corta la explosión de cardinalidad de {@code uri}. Al llegar al tope, Micrometer
     * deja de registrar métricas para URIs nuevas y loguea una advertencia: se pierde
     * visibilidad de esas rutas, pero no se tumba el ingest ni se rebasa el plan.
     *
     * Es deliberadamente una defensa y no la solución: si esta advertencia aparece,
     * lo que hay que arreglar es la URI sin plantillar, no subir el número.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> uriCardinalityCap() {
        return registry -> registry.config().meterFilter(
                MeterFilter.maximumAllowableTags(
                        "http.server.requests", "uri", MAX_URI_TAGS, MeterFilter.deny()));
    }
}