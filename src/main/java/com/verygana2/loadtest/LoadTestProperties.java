package com.verygana2.loadtest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Parámetros del modo prueba de carga. Sin {@code @Validated}: el escaneo de
 * {@code ProfileConfigurationTest} enlazaría la clase en dev y prod. La validación la
 * hace {@link LoadTestSafetyGuard}, que solo actúa con el perfil {@code loadtest}.
 */
@Data
@Component
@Profile("loadtest")
@ConfigurationProperties(prefix = "loadtest")
public class LoadTestProperties {

    private Stubs stubs = new Stubs();
    private Seed seed = new Seed();

    @Data
    public static class Stubs {
        /** Base de WireMock: {@code http://wiremock:8080} en local, IP privada en la nube. */
        private String baseUrl = "";
        /** Único código que acepta el falso de SMS. */
        private String otpCode = "000000";
        private long emailLatencyMs;
        private long smsLatencyMs;
    }

    @Data
    public static class Seed {
        /** 0 = no siembra. */
        private int users;
        private String password = "";
    }
}
