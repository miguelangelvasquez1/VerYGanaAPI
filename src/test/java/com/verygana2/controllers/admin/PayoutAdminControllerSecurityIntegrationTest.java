package com.verygana2.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.finance.PayoutService;

/**
 * {@code /admin/payouts/**} exige {@code hasRole('ADMIN')} a nivel de clase,
 * aplicando a sus 4 endpoints. Este test levanta el filtro de seguridad real
 * (JwtBearerFilter + SecurityConfig) para probar sin token, con un rol
 * distinto (COMMERCIAL) y con ADMIN autenticado, siguiendo el mismo patrón
 * que {@code RaffleAdminControllerSecurityIntegrationTest}.
 */
@WebMvcTest(PayoutAdminController.class)
@Import({ SecurityConfig.class, PayoutAdminControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("PayoutAdminController — autorización (integración MockMvc + Spring Security real)")
class PayoutAdminControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private PayoutService payoutService;
    // Requerido por SecurityConfig.authenticationProvider(), no se invoca en este flujo.
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    // Requerido por el FeatureFlagInterceptor global (WebMvcConfigurer), no relevante aquí.
    @MockitoBean private FeatureFlagService featureFlagService;

    /** SecurityConfig necesita llaves RSA reales para construir encoder/decoder. */
    @TestConfiguration
    static class TestKeysConfig {
        @Bean
        @Primary
        RsaKeyProperties testRsaKeyProperties() throws Exception {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            return new RsaKeyProperties((RSAPublicKey) kp.getPublic(), (RSAPrivateKey) kp.getPrivate());
        }
    }

    private String tokenWithRole(Long userId, String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("VerYGanaAPI")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("admin@test.com")
                .audience(List.of("verygana-frontend"))
                .claim("type", "access")
                .claim("scope", role)
                .claim("userId", userId)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String adminToken(Long userId) {
        return tokenWithRole(userId, "ROLE_ADMIN");
    }

    private String commercialToken(Long userId) {
        return tokenWithRole(userId, "ROLE_COMMERCIAL");
    }

    // ---- GET /admin/payouts ----

    @Test
    @DisplayName("GET /admin/payouts sin token se deniega y no llega al service")
    void getPayoutsForDate_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/admin/payouts"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutService, never()).getPayoutsForDate(any());
    }

    @Test
    @DisplayName("GET /admin/payouts con rol distinto a ADMIN se deniega")
    void getPayoutsForDate_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/admin/payouts")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(payoutService, never()).getPayoutsForDate(any());
    }

    @Test
    @DisplayName("GET /admin/payouts con ADMIN autenticado responde 200")
    void getPayoutsForDate_asAdmin_respondsOk() throws Exception {
        when(payoutService.getPayoutsForDate(any())).thenReturn(List.of());

        int status = mockMvc.perform(get("/admin/payouts")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- POST /admin/payouts/run-now ----

    @Test
    @DisplayName("POST /admin/payouts/run-now sin token se deniega y no llega al service")
    void runNow_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/admin/payouts/run-now"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutService, never()).scheduleDailyPayouts(any(), any());
        verify(payoutService, never()).processScheduledPayouts();
    }

    @Test
    @DisplayName("POST /admin/payouts/run-now con rol distinto a ADMIN se deniega")
    void runNow_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/admin/payouts/run-now")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(payoutService, never()).scheduleDailyPayouts(any(), any());
        verify(payoutService, never()).processScheduledPayouts();
    }

    @Test
    @DisplayName("POST /admin/payouts/run-now con ADMIN autenticado responde 200")
    void runNow_asAdmin_respondsOk() throws Exception {
        int status = mockMvc.perform(post("/admin/payouts/run-now")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(payoutService).scheduleDailyPayouts(any(), any());
        verify(payoutService).processScheduledPayouts();
    }

    // ---- POST /admin/payouts/retry-now ----

    @Test
    @DisplayName("POST /admin/payouts/retry-now sin token se deniega y no llega al service")
    void retryNow_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/admin/payouts/retry-now"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutService, never()).retryFailedPayouts();
    }

    @Test
    @DisplayName("POST /admin/payouts/retry-now con ADMIN autenticado responde 200")
    void retryNow_asAdmin_respondsOk() throws Exception {
        int status = mockMvc.perform(post("/admin/payouts/retry-now")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(payoutService).retryFailedPayouts();
    }

    // ---- GET /admin/payouts/{id}/wompi-status ----

    @Test
    @DisplayName("GET /admin/payouts/{id}/wompi-status sin token se deniega y no llega al service")
    void getWompiStatus_withoutToken_isDenied() throws Exception {
        UUID id = UUID.randomUUID();

        int status = mockMvc.perform(get("/admin/payouts/" + id + "/wompi-status"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutService, never()).getWompiStatus(any());
    }

    @Test
    @DisplayName("GET /admin/payouts/{id}/wompi-status con rol distinto a ADMIN se deniega")
    void getWompiStatus_withWrongRole_isForbidden() throws Exception {
        UUID id = UUID.randomUUID();

        int status = mockMvc.perform(get("/admin/payouts/" + id + "/wompi-status")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(payoutService, never()).getWompiStatus(any());
    }

    @Test
    @DisplayName("GET /admin/payouts/{id}/wompi-status con ADMIN autenticado responde 200")
    void getWompiStatus_asAdmin_respondsOk() throws Exception {
        UUID id = UUID.randomUUID();
        when(payoutService.getWompiStatus(id)).thenReturn(Map.of("status", "PAID"));

        int status = mockMvc.perform(get("/admin/payouts/" + id + "/wompi-status")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }
}
