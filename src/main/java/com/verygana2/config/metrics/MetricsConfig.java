package com.verygana2.config.metrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Tags comunes a toda métrica exportada. Se hace por código y no por
 * {@code management.metrics.tags.*} para que dev y prod compartan un único punto de verdad
 * y para poder derivar el perfil activo.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonMetricsTags(
            @Value("${spring.application.name:VerYGanaAPI}") String application,
            @Value("${spring.profiles.active:default}") String profile) {

        return registry -> registry.config().commonTags(
                "application", application,
                "profile", profile);
    }
}