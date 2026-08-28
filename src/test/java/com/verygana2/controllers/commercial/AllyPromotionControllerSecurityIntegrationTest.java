package com.verygana2.controllers.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

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
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.marketplace.AllyPromotionService;

/**
 * {@code AllyPromotionController} tiene {@code @PreAuthorize("hasRole('COMMERCIAL')")}
 * a nivel de CLASE, aplicando a sus 4 endpoints: {@code PATCH /promotions/{productId}},
 * {@code GET /promotions}, {@code GET} (raíz, aliados) y {@code GET /promoters}.
 * Se prueba el caso completo (sin token / rol incorrecto / COMMERCIAL) en el
 * toggle y en {@code GET} (raíz), y sin-token + COMMERCIAL en los otros dos,
 * ya que la regla de autorización es la misma para los 4.
 */
@WebMvcTest(AllyPromotionController.class)
@Import({ SecurityConfig.class, AllyPromotionControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("AllyPromotionController — autorización (integración MockMvc + Spring Security real)")
class AllyPromotionControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private AllyPromotionService allyPromotionService;
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

    // ---- PATCH /commercial/allies/promotions/{productId} ----

    @Test
    @DisplayName("PATCH /commercial/allies/promotions/{id} sin token se deniega y no llega al service")
    void togglePromotion_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(patch("/commercial/allies/promotions/1"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(allyPromotionService, never()).toggleAllyPromotion(anyLong(), anyLong());
    }

    @Test
    @DisplayName("PATCH /commercial/allies/promotions/{id} con rol distinto a COMMERCIAL se deniega")
    void togglePromotion_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(patch("/commercial/allies/promotions/1")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(allyPromotionService, never()).toggleAllyPromotion(anyLong(), anyLong());
    }

    @Test
    @DisplayName("PATCH /commercial/allies/promotions/{id} con COMMERCIAL autenticado responde 204")
    void togglePromotion_asCommercial_respondsNoContent() throws Exception {
        int status = mockMvc.perform(patch("/commercial/allies/promotions/1")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(204);
        verify(allyPromotionService).toggleAllyPromotion(9L, 1L);
    }

    // ---- GET /commercial/allies/promotions ----

    @Test
    @DisplayName("GET /commercial/allies/promotions sin token se deniega y no llega al service")
    void getMyPromotions_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/commercial/allies/promotions"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(allyPromotionService, never()).getMyPromotions(anyLong());
    }

    @Test
    @DisplayName("GET /commercial/allies/promotions con COMMERCIAL autenticado responde 200")
    void getMyPromotions_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/commercial/allies/promotions")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(allyPromotionService).getMyPromotions(9L);
    }

    // ---- GET /commercial/allies (raíz) ----

    @Test
    @DisplayName("GET /commercial/allies sin token se deniega y no llega al service")
    void getMyAllies_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/commercial/allies"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(allyPromotionService, never()).getMyAllies(anyLong());
    }

    @Test
    @DisplayName("GET /commercial/allies con rol distinto a COMMERCIAL se deniega")
    void getMyAllies_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/commercial/allies")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(allyPromotionService, never()).getMyAllies(anyLong());
    }

    @Test
    @DisplayName("GET /commercial/allies con COMMERCIAL autenticado responde 200")
    void getMyAllies_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/commercial/allies")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(allyPromotionService).getMyAllies(9L);
    }

    // ---- GET /commercial/allies/promoters ----

    @Test
    @DisplayName("GET /commercial/allies/promoters sin token se deniega y no llega al service")
    void getMyPromoters_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/commercial/allies/promoters"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(allyPromotionService, never()).getMyPromoters(anyLong());
    }

    @Test
    @DisplayName("GET /commercial/allies/promoters con COMMERCIAL autenticado responde 200")
    void getMyPromoters_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/commercial/allies/promoters")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(allyPromotionService).getMyPromoters(9L);
    }
}
