package com.verygana2.controllers.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.interfaces.marketplace.ProductStockService;

/**
 * Barrido de autorización sobre <b>todos</b> los endpoints protegidos de
 * {@code ProductController}, complementando la cobertura por "muestra
 * representativa" de {@link ProductControllerSecurityIntegrationTest}.
 *
 * <p>Para cada endpoint verifica solo las dos garantías de autorización, no la
 * lógica de negocio:
 * <ul>
 *   <li>sin token → 401/403 (rechazado en el filter chain, antes del controller);</li>
 *   <li>con un token de un rol que el endpoint no acepta → 403 (rechazado por
 *       {@code @PreAuthorize}).</li>
 * </ul>
 *
 * <p>El objetivo es que agregar un endpoint sin {@code @PreAuthorize} —o con el
 * rol equivocado, ej. {@code hasRole('ROLE_COMMERCIAL')} con el prefijo doble—
 * rompa un test aunque no se añada un caso detallado a mano. Los endpoints
 * públicos ({@code GET /products/filter}, {@code GET /products/{id}}) quedan
 * fuera de la lista a propósito.
 */
@WebMvcTest(ProductController.class)
@Import({ SecurityConfig.class, ProductControllerAuthorizationMatrixIntegrationTest.TestKeysConfig.class })
@DisplayName("ProductController — matriz de autorización (todos los endpoints protegidos)")
class ProductControllerAuthorizationMatrixIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private ProductService productService;
    @MockitoBean private ProductStockService productStockService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private FeatureFlagService featureFlagService;

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

    /**
     * Un endpoint protegido. {@code allowedRoles} son los roles que el
     * {@code @PreAuthorize} acepta; {@code body} es un JSON válido para pasar la
     * validación de {@code @Valid} (que corre antes que {@code @PreAuthorize}) o
     * {@code null} si el endpoint no recibe cuerpo.
     */
    record Endpoint(String method, String path, String body, Set<String> allowedRoles) {
        @Override
        public String toString() {
            return method + " " + path + "  (" + String.join("/", allowedRoles) + ")";
        }
    }

    private static final String UPLOAD_BODY =
            "{\"originalFileName\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":1024}";
    private static final String CONFIRM_BODY = "{\"productAssetId\":1,\"productData\":{}}";
    private static final String UPDATE_BODY =
            "{\"name\":\"n\",\"description\":\"d\",\"productCategoryId\":1,\"price\":1000}";
    private static final String IMG_CONFIRM_BODY = "{\"newAssetId\":1}";
    private static final String BULK_BODY = "[{\"code\":\"ABC123\"}]";

    private static final Set<String> COMMERCIAL = Set.of("COMMERCIAL");
    private static final Set<String> CONSUMER = Set.of("CONSUMER");

    static Stream<Endpoint> protectedEndpoints() {
        return Stream.of(
                new Endpoint("POST", "/products/prepare", UPLOAD_BODY, COMMERCIAL),
                new Endpoint("POST", "/products/confirm", CONFIRM_BODY, COMMERCIAL),
                new Endpoint("DELETE", "/products/1", null, COMMERCIAL),
                new Endpoint("GET", "/products/edit/1", null, COMMERCIAL),
                new Endpoint("PATCH", "/products/1", UPDATE_BODY, COMMERCIAL),
                new Endpoint("POST", "/products/1/image/prepare", UPLOAD_BODY, COMMERCIAL),
                new Endpoint("POST", "/products/1/image/confirm", IMG_CONFIRM_BODY, COMMERCIAL),
                new Endpoint("GET", "/products/1/stock", null, COMMERCIAL),
                new Endpoint("DELETE", "/products/1/stock/2", null, COMMERCIAL),
                new Endpoint("POST", "/products/1/stock/bulk", BULK_BODY, COMMERCIAL),
                new Endpoint("GET", "/products/my-products", null, COMMERCIAL),
                new Endpoint("GET", "/products/total-products?status=ACTIVE", null, COMMERCIAL),
                new Endpoint("PATCH", "/products/1/gameReward", null, COMMERCIAL),
                new Endpoint("GET", "/products/1/stock/2/code", null, COMMERCIAL),
                new Endpoint("GET", "/products/favorites", null, CONSUMER),
                new Endpoint("POST", "/products/favorites/1", null, CONSUMER),
                new Endpoint("DELETE", "/products/favorites/1", null, CONSUMER),
                new Endpoint("GET", "/products/favorites/count", null, CONSUMER),
                new Endpoint("GET", "/products/1/private-image", null, Set.of("ADMIN", "COMMERCIAL")));
    }

    private MockHttpServletRequestBuilder request(Endpoint e) {
        MockHttpServletRequestBuilder b = switch (e.method()) {
            case "GET" -> get(e.path());
            case "POST" -> post(e.path());
            case "PATCH" -> patch(e.path());
            case "DELETE" -> delete(e.path());
            default -> throw new IllegalArgumentException("Método no soportado: " + e.method());
        };
        if (e.body() != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(e.body());
        }
        return b;
    }

    @ParameterizedTest(name = "sin token → 401/403: {0}")
    @MethodSource("protectedEndpoints")
    @DisplayName("cada endpoint protegido rechaza el request sin token")
    void withoutToken_isDenied(Endpoint e) throws Exception {
        int status = mockMvc.perform(request(e)).andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }

    @ParameterizedTest(name = "rol no autorizado → 403: {0}")
    @MethodSource("protectedEndpoints")
    @DisplayName("cada endpoint protegido rechaza un token de un rol que no acepta")
    void withWrongRole_isForbidden(Endpoint e) throws Exception {
        // Si el endpoint es de CONSUMER, el rol equivocado es COMMERCIAL; en el
        // resto de casos (COMMERCIAL, o ADMIN/COMMERCIAL) el rol equivocado es CONSUMER.
        String wrongRole = e.allowedRoles().contains("CONSUMER") ? "ROLE_COMMERCIAL" : "ROLE_CONSUMER";

        int status = mockMvc.perform(request(e)
                        .header("Authorization", "Bearer " + tokenWithRole(1L, wrongRole)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }
}
