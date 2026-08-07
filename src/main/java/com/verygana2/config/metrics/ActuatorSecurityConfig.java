package com.verygana2.config.metrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Cadena de seguridad propia para {@code /actuator/**}, con {@code @Order(0)} para que corra
 * antes que la de {@link com.verygana2.config.SecurityConfig} (sin @Order = LOWEST_PRECEDENCE).
 * Al tener su propio {@code securityMatcher}, las peticiones a actuator no pasan por el
 * {@code JwtBearerFilter} ni por {@code anyRequest().authenticated()}.
 *
 * Reglas:
 * <ul>
 *   <li>{@code /actuator/health} — público, lo necesita el healthcheck de la plataforma.</li>
 *   <li>el resto (incluido {@code /actuator/prometheus}) — exige
 *       {@code Authorization: Bearer <token>}, que Prometheus manda de forma nativa con el
 *       bloque {@code authorization} del scrape config.</li>
 * </ul>
 *
 * Se usa un token de scrapeo en vez de un JWT de la app porque Prometheus no sabe renovar
 * tokens de 15 minutos. Sin {@code observability.scrape-token} configurado, el endpoint queda
 * cerrado: en un backend financiero, exponer métricas por defecto sería peor que no tenerlas.
 */
@Configuration
@Slf4j
public class ActuatorSecurityConfig {

    private final byte[] scrapeToken;

    public ActuatorSecurityConfig(@Value("${observability.scrape-token:}") String scrapeToken) {
        this.scrapeToken = StringUtils.hasText(scrapeToken)
                ? scrapeToken.getBytes(StandardCharsets.UTF_8)
                : null;

        if (this.scrapeToken == null) {
            log.warn("[METRICS] observability.scrape-token vacío: /actuator/prometheus responderá 401. "
                    + "Definir METRICS_SCRAPE_TOKEN para habilitar el scrapeo.");
        }
    }

    @Bean
    @Order(0)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        .anyRequest().access((authentication, context) ->
                                new AuthorizationDecision(hasValidScrapeToken(context.getRequest()))))
                .build();
    }

    private boolean hasValidScrapeToken(HttpServletRequest request) {
        if (scrapeToken == null) {
            return false;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        // Comparación en tiempo constante: el token es un secreto de larga vida.
        return MessageDigest.isEqual(
                header.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8),
                scrapeToken);
    }
}