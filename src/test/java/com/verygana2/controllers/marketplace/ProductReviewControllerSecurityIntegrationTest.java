package com.verygana2.controllers.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.verygana2.services.interfaces.marketplace.ProductReviewService;

/**
 * {@code ProductReviewController} tiene 4 endpoints con 3 niveles de
 * autorización distintos: COMMERCIAL (promedio de calificación), CONSUMER
 * (crear reseña), ADMIN (ocultar reseña) y un endpoint de lectura sin
 * {@code @PreAuthorize} que cae en la regla por defecto
 * {@code anyRequest().authenticated()} de {@code SecurityConfig}.
 */
@WebMvcTest(ProductReviewController.class)
@Import({ SecurityConfig.class, ProductReviewControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("ProductReviewController — autorización (integración MockMvc + Spring Security real)")
class ProductReviewControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private ProductReviewService productReviewService;
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

    private String adminToken(Long userId) {
        return tokenWithRole(userId, "ROLE_ADMIN");
    }

    // ---- GET /productsReviews/commercial/avg (COMMERCIAL) ----

    @Test
    @DisplayName("GET /productsReviews/commercial/avg sin token se deniega y no llega al service")
    void getCommercialAvgRating_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/productsReviews/commercial/avg"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(productReviewService, never()).getCommercialAvgRating(anyLong());
    }

    @Test
    @DisplayName("GET /productsReviews/commercial/avg con rol distinto a COMMERCIAL se deniega")
    void getCommercialAvgRating_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/productsReviews/commercial/avg")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(productReviewService, never()).getCommercialAvgRating(anyLong());
    }

    @Test
    @DisplayName("GET /productsReviews/commercial/avg con COMMERCIAL autenticado responde 200")
    void getCommercialAvgRating_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/productsReviews/commercial/avg")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- POST /productsReviews/create (CONSUMER) ----

    private static final String REVIEW_BODY =
            "{\"purchaseItemId\":1,\"comment\":\"Buen producto\",\"rating\":5}";

    @Test
    @DisplayName("POST /productsReviews/create sin token se deniega y no llega al service")
    void createProductReview_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/productsReviews/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REVIEW_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(productReviewService, never()).createProductReview(any(), any());
    }

    @Test
    @DisplayName("POST /productsReviews/create con rol distinto a CONSUMER se deniega")
    void createProductReview_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/productsReviews/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REVIEW_BODY)
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(productReviewService, never()).createProductReview(any(), any());
    }

    @Test
    @DisplayName("POST /productsReviews/create con CONSUMER autenticado responde 200")
    void createProductReview_asConsumer_respondsOk() throws Exception {
        int status = mockMvc.perform(post("/productsReviews/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REVIEW_BODY)
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(productReviewService).createProductReview(any(Long.class), any());
    }

    // ---- GET /productsReviews/{productId} (sin @PreAuthorize, requiere autenticación) ----

    @Test
    @DisplayName("GET /productsReviews/{id} sin token se deniega")
    void getProductReviewsByProductId_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/productsReviews/1"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("GET /productsReviews/{id} con cualquier rol autenticado responde 200")
    void getProductReviewsByProductId_withAnyAuthenticatedRole_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/productsReviews/1")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- PATCH /productsReviews/{reviewId}/hide (ADMIN) ----

    @Test
    @DisplayName("PATCH /productsReviews/{id}/hide sin token se deniega y no llega al service")
    void hideProductReview_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(patch("/productsReviews/5/hide"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(productReviewService, never()).hideProductReview(anyLong());
    }

    @Test
    @DisplayName("PATCH /productsReviews/{id}/hide con rol distinto a ADMIN se deniega")
    void hideProductReview_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(patch("/productsReviews/5/hide")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(productReviewService, never()).hideProductReview(anyLong());
    }

    @Test
    @DisplayName("PATCH /productsReviews/{id}/hide con ADMIN autenticado responde 204")
    void hideProductReview_asAdmin_respondsNoContent() throws Exception {
        int status = mockMvc.perform(patch("/productsReviews/5/hide")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(204);
        verify(productReviewService).hideProductReview(5L);
    }
}
