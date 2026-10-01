package com.verygana2.controllers.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.marketplace.ProductCategoryService;

/**
 * {@code GET /productCategories} no tiene {@code @PreAuthorize} y no está en
 * {@link com.verygana2.security.PublicPaths} (solo {@code /categories/all}
 * lo está, un path distinto) — por lo tanto cae en la regla por defecto de
 * {@code SecurityConfig}: {@code anyRequest().authenticated()}. Cualquier
 * usuario autenticado, sin importar el rol, puede listar las categorías.
 */
@WebMvcTest(ProductCategoryController.class)
@Import({ SecurityConfig.class, ProductCategoryControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("ProductCategoryController — autorización (integración MockMvc + Spring Security real)")
class ProductCategoryControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private ProductCategoryService productCategoryService;
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

    private String consumerToken(Long userId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("VerYGanaAPI")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("user@test.com")
                .audience(List.of("verygana-frontend"))
                .claim("type", "access")
                .claim("scope", "ROLE_CONSUMER")
                .claim("userId", userId)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    @Test
    @DisplayName("GET /productCategories sin token se deniega y no llega al service")
    void getActiveProductCategories_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/productCategories"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(productCategoryService, never()).getActiveProductCategories();
    }

    @Test
    @DisplayName("GET /productCategories con cualquier rol autenticado responde 200")
    void getActiveProductCategories_withAnyAuthenticatedRole_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/productCategories")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }
}
