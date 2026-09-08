package com.verygana2.controllers.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
import com.verygana2.dtos.keys.KeyBalanceResponseDTO;
import com.verygana2.dtos.keys.SpendKeysResponseDTO;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.finance.KeyWalletService;

/**
 * {@code KeyWalletController} tiene {@code @PreAuthorize("hasRole('CONSUMER')")}
 * a nivel de CLASE, aplicando a sus 2 endpoints ({@code GET /balance},
 * {@code POST /spend}). Se prueba el trío completo (sin token / rol
 * incorrecto / CONSUMER) en ambos.
 */
@WebMvcTest(KeyWalletController.class)
@Import({ SecurityConfig.class, KeyWalletControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("KeyWalletController — autorización (integración MockMvc + Spring Security real)")
class KeyWalletControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private KeyWalletService keyWalletService;
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

    private String consumerToken(Long userId) {
        return tokenWithRole(userId, "ROLE_CONSUMER");
    }

    private String commercialToken(Long userId) {
        return tokenWithRole(userId, "ROLE_COMMERCIAL");
    }

    // ---- GET /consumer/wallet/keys/balance ----

    @Test
    @DisplayName("GET /consumer/wallet/keys/balance sin token se deniega y no llega al service")
    void getBalance_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/consumer/wallet/keys/balance"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(keyWalletService, never()).getBalance(anyLong());
    }

    @Test
    @DisplayName("GET /consumer/wallet/keys/balance con rol distinto a CONSUMER se deniega")
    void getBalance_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/consumer/wallet/keys/balance")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(keyWalletService, never()).getBalance(anyLong());
    }

    @Test
    @DisplayName("GET /consumer/wallet/keys/balance con CONSUMER autenticado responde 200")
    void getBalance_asConsumer_respondsOk() throws Exception {
        when(keyWalletService.getBalance(9L)).thenReturn(new KeyBalanceResponseDTO(100L, "KEYS"));

        int status = mockMvc.perform(get("/consumer/wallet/keys/balance")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(keyWalletService).getBalance(9L);
    }

    // ---- POST /consumer/wallet/keys/spend ----

    private static final String SPEND_BODY = "{"
            + "\"amount\":1,"
            + "\"itemId\":14,"
            + "\"itemName\":\"14\""
            + "}";

    @Test
    @DisplayName("POST /consumer/wallet/keys/spend sin token se deniega y no llega al service")
    void spend_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/consumer/wallet/keys/spend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SPEND_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(keyWalletService, never()).spendKeysForPetGame(any(), any());
    }

    @Test
    @DisplayName("POST /consumer/wallet/keys/spend con rol distinto a CONSUMER se deniega")
    void spend_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/consumer/wallet/keys/spend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SPEND_BODY)
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(keyWalletService, never()).spendKeysForPetGame(any(), any());
    }

    @Test
    @DisplayName("POST /consumer/wallet/keys/spend con CONSUMER autenticado responde 200")
    void spend_asConsumer_respondsOk() throws Exception {
        when(keyWalletService.spendKeysForPetGame(any(), any()))
                .thenReturn(SpendKeysResponseDTO.ok(99L));

        int status = mockMvc.perform(post("/consumer/wallet/keys/spend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SPEND_BODY)
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(keyWalletService).spendKeysForPetGame(any(Long.class), any());
    }
}
