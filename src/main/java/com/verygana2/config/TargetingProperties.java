package com.verygana2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Reglas globales de segmentación de audiencia (ads, surveys, campañas, branding).
 * Se llena desde {@code app.targeting.*} en application.yml.
 */
@Component
@ConfigurationProperties(prefix = "app.targeting")
@Data
public class TargetingProperties {

    /**
     * Edad mínima que se puede configurar como {@code minAge}/{@code maxAge} al
     * segmentar una audiencia. Se valida con {@code @MinTargetAge}.
     */
    private int minAge = 18;
}
