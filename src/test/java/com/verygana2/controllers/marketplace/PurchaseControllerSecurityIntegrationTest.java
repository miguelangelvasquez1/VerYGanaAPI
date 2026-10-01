package com.verygana2.controllers.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.marketplace.PurchaseService;

/**
 * Los 3 endpoints de {@code PurchaseController} exigen
 * {@code hasAuthority('ROLE_CONSUMER')} — a diferencia de {@code hasRole},
 * aquí se compara directamente contra la authority completa, pero el
 * resultado práctico es el mismo que exigir el rol CONSUMER.
 */
@WebMvcTest(PurchaseController.class)
@Import({ SecurityConfig.class, PurchaseControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("PurchaseController — autorización (integración MockMvc + Spring Security real)")
class PurchaseControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private PurchaseService purchaseService;
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

    // ---- POST /purchases/buy (CONSUMER) ----

    private static final String BUY_BODY =
            "{\"items\":[{\"productId\":1,\"quantity\":1}],\"keysToUse\":0}";

    @Test
    @DisplayName("POST /purchases/buy sin token se deniega y no llega al service")
    void createPurchase_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/purchases/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BUY_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(purchaseService, never()).createPurchase(any(), any());
    }

    @Test
    @DisplayName("POST /purchases/buy con rol distinto a CONSUMER se deniega")
    void createPurchase_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/purchases/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BUY_BODY)
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(purchaseService, never()).createPurchase(any(), any());
    }

    @Test
    @DisplayName("POST /purchases/buy con CONSUMER autenticado responde 200")
    void createPurchase_asConsumer_respondsOk() throws Exception {
        int status = mockMvc.perform(post("/purchases/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BUY_BODY)
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(purchaseService).createPurchase(any(Long.class), any());
    }

    // ---- GET /purchases/{purchaseId} (CONSUMER) ----

    @Test
    @DisplayName("GET /purchases/{id} sin token se deniega y no llega al service")
    void getPurchaseById_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/purchases/5"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(purchaseService, never()).getPurchaseResponseDTO(anyLong(), anyLong());
    }

    @Test
    @DisplayName("GET /purchases/{id} con CONSUMER autenticado responde 200")
    void getPurchaseById_asConsumer_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/purchases/5")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(purchaseService).getPurchaseResponseDTO(5L, 9L);
    }

    // ---- GET /purchases (CONSUMER) ----

    @Test
    @DisplayName("GET /purchases sin token se deniega y no llega al service")
    void getPurchases_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/purchases"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(purchaseService, never()).getConsumerPurchases(anyLong(), any());
    }

    @Test
    @DisplayName("GET /purchases con CONSUMER autenticado responde 200")
    void getPurchases_asConsumer_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/purchases")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }
}
