package com.verygana2.controllers.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.interfaces.marketplace.ProductStockService;

/**
 * {@code ProductController} mezcla endpoints de gestión (COMMERCIAL),
 * catálogo de consumidor (CONSUMER), un proxy de imagen privada
 * (ADMIN o COMMERCIAL, {@code hasAnyRole}) y endpoints públicos sin
 * {@code @PreAuthorize} (ej. {@code GET /products/{id}}). Este test cubre
 * una muestra representativa de cada uno de esos 4 niveles de autorización.
 */
@WebMvcTest(ProductController.class)
@Import({ SecurityConfig.class, ProductControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("ProductController — autorización (integración MockMvc + Spring Security real)")
class ProductControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private ProductService productService;
    @MockitoBean private ProductStockService productStockService;
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

    // ---- POST /products/confirm (COMMERCIAL) ----

    private static final String CONFIRM_BODY = "{\"productAssetId\":1,\"productData\":{}}";

    @Test
    @DisplayName("POST /products/confirm sin token se deniega y no llega al service")
    void confirmProductCreation_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/products/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(productService, never()).confirmProductCreation(any(), any());
    }

    @Test
    @DisplayName("POST /products/confirm con rol distinto a COMMERCIAL se deniega")
    void confirmProductCreation_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/products/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_BODY)
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(productService, never()).confirmProductCreation(any(), any());
    }

    @Test
    @DisplayName("POST /products/confirm con COMMERCIAL autenticado responde 200")
    void confirmProductCreation_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(post("/products/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_BODY)
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(productService).confirmProductCreation(any(Long.class), any());
    }

    // ---- DELETE /products/{productId} (COMMERCIAL) ----

    @Test
    @DisplayName("DELETE /products/{id} sin token se deniega y no llega al service")
    void deleteProduct_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(delete("/products/1"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(productService, never()).delete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("DELETE /products/{id} con COMMERCIAL autenticado responde 204")
    void deleteProduct_asCommercial_respondsNoContent() throws Exception {
        int status = mockMvc.perform(delete("/products/1")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(204);
        verify(productService).delete(1L, 9L);
    }

    // ---- GET /products/my-products (COMMERCIAL) ----

    @Test
    @DisplayName("GET /products/my-products sin token se deniega y no llega al service")
    void getMyProducts_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/products/my-products"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(productService, never()).getCommercialProducts(anyLong(), any());
    }

    @Test
    @DisplayName("GET /products/my-products con rol distinto a COMMERCIAL se deniega")
    void getMyProducts_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/products/my-products")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(productService, never()).getCommercialProducts(anyLong(), any());
    }

    @Test
    @DisplayName("GET /products/my-products con COMMERCIAL autenticado responde 200")
    void getMyProducts_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/products/my-products")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- GET /products/favorites (CONSUMER) ----

    @Test
    @DisplayName("GET /products/favorites sin token se deniega y no llega al service")
    void getFavorites_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/products/favorites"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(productService, never()).getFavorites(anyLong(), any());
    }

    @Test
    @DisplayName("GET /products/favorites con rol distinto a CONSUMER se deniega")
    void getFavorites_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/products/favorites")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(productService, never()).getFavorites(anyLong(), any());
    }

    @Test
    @DisplayName("GET /products/favorites con CONSUMER autenticado responde 200")
    void getFavorites_asConsumer_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/products/favorites")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- GET /products/{productId}/private-image (ADMIN o COMMERCIAL) ----

    @Test
    @DisplayName("GET /products/{id}/private-image sin token se deniega y no llega al service")
    void getPrivateProductImage_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/products/1/private-image"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(productService, never()).streamPrivateProductImage(anyLong(), any());
    }

    @Test
    @DisplayName("GET /products/{id}/private-image con CONSUMER se deniega")
    void getPrivateProductImage_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/products/1/private-image")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(productService, never()).streamPrivateProductImage(anyLong(), any());
    }

    @Test
    @DisplayName("GET /products/{id}/private-image con ADMIN responde 200 sin validar dueño")
    void getPrivateProductImage_asAdmin_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/products/1/private-image")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(productService, never()).getByIdAndCommercialId(anyLong(), anyLong());
        verify(productService).streamPrivateProductImage(any(Long.class), any());
    }

    @Test
    @DisplayName("GET /products/{id}/private-image con COMMERCIAL dueño responde 200")
    void getPrivateProductImage_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/products/1/private-image")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(productService).getByIdAndCommercialId(1L, 9L);
        verify(productService).streamPrivateProductImage(any(Long.class), any());
    }

    // ---- GET /products/{productId} (público, sin @PreAuthorize) ----

    @Test
    @DisplayName("GET /products/{id} es público: sin token responde 200")
    void getProductDetail_withoutToken_respondsOk() throws Exception {
        when(productService.detailProduct(1L)).thenReturn(new ProductResponseDTO());

        int status = mockMvc.perform(get("/products/1"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }
}
