package com.verygana2.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.treasury.TreasuryBalanceResponseDTO;
import com.verygana2.dtos.treasury.TreasuryMovementResponseDTO;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.finance.TreasuryService;

/**
 * {@code /admin/treasury/**} exige {@code hasRole('ADMIN')} a nivel de clase,
 * PERO {@code GET /config/keys-reserve-pct} tiene un override de método con
 * {@code @PreAuthorize("hasAnyRole('ADMIN','COMMERCIAL')")}. Este test cubre
 * autorización diferencial: {@code /balance} y {@code /movements/{code}}
 * solo permiten ADMIN (COMMERCIAL se deniega), mientras que
 * {@code /config/keys-reserve-pct} permite tanto ADMIN como COMMERCIAL, y
 * solo deniega un tercer rol (CONSUMER). Levanta el filtro de seguridad real
 * (JwtBearerFilter + SecurityConfig), siguiendo el mismo patrón que
 * {@code RaffleAdminControllerSecurityIntegrationTest}.
 */
@WebMvcTest(TreasuryAdminController.class)
@Import({ SecurityConfig.class, TreasuryAdminControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("TreasuryAdminController — autorización (integración MockMvc + Spring Security real)")
class TreasuryAdminControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private TreasuryService treasuryService;
    // TreasuryConfig es un @Component con @ConfigurationProperties, no un bean
    // web — @WebMvcTest no lo levanta automáticamente, así que se mockea.
    @MockitoBean private TreasuryConfig treasuryConfig;
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
                .subject("user@test.com")
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

    private String consumerToken(Long userId) {
        return tokenWithRole(userId, "ROLE_CONSUMER");
    }

    // ---- GET /admin/treasury/balance (solo ADMIN) ----

    @Test
    @DisplayName("GET /admin/treasury/balance sin token se deniega y no llega al service")
    void getBalance_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/admin/treasury/balance"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(treasuryService, never()).getBalanceReport();
    }

    @Test
    @DisplayName("GET /admin/treasury/balance con COMMERCIAL se deniega (solo ADMIN)")
    void getBalance_withCommercial_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/admin/treasury/balance")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(treasuryService, never()).getBalanceReport();
    }

    @Test
    @DisplayName("GET /admin/treasury/balance con ADMIN autenticado responde 200")
    void getBalance_asAdmin_respondsOk() throws Exception {
        var expected = new TreasuryBalanceResponseDTO(
                60_000_000L, 10_000_000L, 30_000_000L, 5_000_000L, 105_000_000L, 100.0, "OK", false);
        when(treasuryService.getBalanceReport()).thenReturn(expected);

        int status = mockMvc.perform(get("/admin/treasury/balance")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- GET /admin/treasury/movements/{code} (solo ADMIN) ----

    @Test
    @DisplayName("GET /admin/treasury/movements/{code} sin token se deniega y no llega al service")
    void getMovements_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/admin/treasury/movements/KEYS_RESERVE"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(treasuryService, never()).getMovements(any(), any());
    }

    @Test
    @DisplayName("GET /admin/treasury/movements/{code} con COMMERCIAL se deniega (solo ADMIN)")
    void getMovements_withCommercial_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/admin/treasury/movements/KEYS_RESERVE")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(treasuryService, never()).getMovements(any(), any());
    }

    @Test
    @DisplayName("GET /admin/treasury/movements/{code} con ADMIN autenticado responde 200")
    void getMovements_asAdmin_respondsOk() throws Exception {
        Page<TreasuryMovementResponseDTO> page = new PageImpl<>(List.of());
        when(treasuryService.getMovements(any(TreasuryAccountCode.class), any())).thenReturn(page);

        int status = mockMvc.perform(get("/admin/treasury/movements/KEYS_RESERVE")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- GET /admin/treasury/config/keys-reserve-pct (ADMIN o COMMERCIAL) ----

    @Test
    @DisplayName("GET /admin/treasury/config/keys-reserve-pct sin token se deniega")
    void getKeysReservePct_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/admin/treasury/config/keys-reserve-pct"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("GET /admin/treasury/config/keys-reserve-pct con CONSUMER se deniega")
    void getKeysReservePct_withConsumer_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/admin/treasury/config/keys-reserve-pct")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /admin/treasury/config/keys-reserve-pct con ADMIN autenticado responde 200")
    void getKeysReservePct_asAdmin_respondsOk() throws Exception {
        when(treasuryConfig.getKeysReservePct()).thenReturn(60);

        int status = mockMvc.perform(get("/admin/treasury/config/keys-reserve-pct")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /admin/treasury/config/keys-reserve-pct con COMMERCIAL autenticado también responde 200")
    void getKeysReservePct_asCommercial_respondsOk() throws Exception {
        when(treasuryConfig.getKeysReservePct()).thenReturn(60);

        int status = mockMvc.perform(get("/admin/treasury/config/keys-reserve-pct")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }
}
