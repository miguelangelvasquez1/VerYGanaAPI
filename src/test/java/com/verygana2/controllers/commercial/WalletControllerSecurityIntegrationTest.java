package com.verygana2.controllers.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
import com.verygana2.dtos.wallet.responses.BillingSummaryResponseDTO;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.finance.WalletService;

/**
 * {@code WalletController} tiene {@code @PreAuthorize("hasRole('COMMERCIAL')")}
 * a nivel de CLASE, aplicando a sus 3 endpoints (todos GET). Se prueba el
 * trío completo (sin token / rol incorrecto / COMMERCIAL) en
 * {@code /me/billing-summary}, y sin-token + COMMERCIAL en los otros dos, ya
 * que la regla de autorización es la misma para los 3.
 */
@WebMvcTest(WalletController.class)
@Import({ SecurityConfig.class, WalletControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("WalletController — autorización (integración MockMvc + Spring Security real)")
class WalletControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private WalletService walletService;
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

    private String commercialToken(Long userId) {
        return tokenWithRole(userId, "ROLE_COMMERCIAL");
    }

    private String consumerToken(Long userId) {
        return tokenWithRole(userId, "ROLE_CONSUMER");
    }

    // ---- GET /commercial/wallet/me/billing-summary ----

    @Test
    @DisplayName("GET /commercial/wallet/me/billing-summary sin token se deniega y no llega al service")
    void getBillingSummary_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/commercial/wallet/me/billing-summary"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(walletService, never()).getBillingSummary(anyLong());
    }

    @Test
    @DisplayName("GET /commercial/wallet/me/billing-summary con rol distinto a COMMERCIAL se deniega")
    void getBillingSummary_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/commercial/wallet/me/billing-summary")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(walletService, never()).getBillingSummary(anyLong());
    }

    @Test
    @DisplayName("GET /commercial/wallet/me/billing-summary con COMMERCIAL autenticado responde 200")
    void getBillingSummary_asCommercial_respondsOk() throws Exception {
        BillingSummaryResponseDTO dto = BillingSummaryResponseDTO.builder()
                .balanceCents(1000L)
                .spentThisMonthCents(0L)
                .earnedThisMonthCents(0L)
                .build();
        org.mockito.Mockito.when(walletService.getBillingSummary(9L)).thenReturn(dto);

        int status = mockMvc.perform(get("/commercial/wallet/me/billing-summary")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(walletService).getBillingSummary(9L);
    }

    // ---- GET /commercial/wallet/me/deposits ----

    @Test
    @DisplayName("GET /commercial/wallet/me/deposits sin token se deniega y no llega al service")
    void getDeposits_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/commercial/wallet/me/deposits")
                        .param("year", "2026")
                        .param("month", "8"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(walletService, never()).getDeposits(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("GET /commercial/wallet/me/deposits con COMMERCIAL autenticado responde 200")
    void getDeposits_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/commercial/wallet/me/deposits")
                        .param("year", "2026")
                        .param("month", "8")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(walletService).getDeposits(eq(9L), eq(2026), eq(8), any());
    }

    // ---- GET /commercial/wallet/me/payouts ----

    @Test
    @DisplayName("GET /commercial/wallet/me/payouts sin token se deniega y no llega al service")
    void getPayouts_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/commercial/wallet/me/payouts")
                        .param("year", "2026")
                        .param("month", "8"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(walletService, never()).getPayouts(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("GET /commercial/wallet/me/payouts con COMMERCIAL autenticado responde 200")
    void getPayouts_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/commercial/wallet/me/payouts")
                        .param("year", "2026")
                        .param("month", "8")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(walletService).getPayouts(eq(9L), eq(2026), eq(8), any());
    }
}
