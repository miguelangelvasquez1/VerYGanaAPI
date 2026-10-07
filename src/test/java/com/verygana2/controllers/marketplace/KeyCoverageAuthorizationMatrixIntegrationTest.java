package com.verygana2.controllers.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.verygana2.controllers.admin.KeyCoveragePromotionAdminController;
import com.verygana2.controllers.commercial.KeyCoveragePromotionOfferController;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.marketplace.KeyCoveragePromotionService;
import com.verygana2.services.interfaces.marketplace.KeyCoverageService;
import com.verygana2.services.interfaces.marketplace.PurchaseService;

/**
 * Barrido de autorización sobre los endpoints de MP-12: administración de promociones
 * especiales de llaves (ADMIN), respuesta del empresario a sus invitaciones (COMMERCIAL)
 * y cotización previa a la compra (CONSUMER). Con la cadena de seguridad real y JWT
 * firmados aquí.
 *
 * <p>Además comprueba que la compra y la cotización validan el cuerpo antes de llegar al
 * service: un carrito vacío responde 400 sin reservar nada.
 */
@WebMvcTest({ KeyCoveragePromotionAdminController.class, KeyCoveragePromotionOfferController.class,
        KeyCoverageController.class, PurchaseController.class })
@Import({ SecurityConfig.class, KeyCoverageAuthorizationMatrixIntegrationTest.TestKeysConfig.class })
@DisplayName("MP-12 — matriz de autorización de promociones de llaves y cotización")
class KeyCoverageAuthorizationMatrixIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private KeyCoveragePromotionService promotionService;
    @MockitoBean private KeyCoverageService keyCoverageService;
    @MockitoBean private PurchaseService purchaseService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private UserIdResolver userIdResolver;
    @MockitoBean private FeatureFlagService featureFlagService;

    /** El token solo lleva publicId; en estos tests publicId = UUID(0, userId) y el resolver lo invierte. */
    @BeforeEach
    void stubUserIdResolver() {
        when(userIdResolver.toInternalId(any(UUID.class)))
                .thenAnswer(inv -> inv.<UUID>getArgument(0).getLeastSignificantBits());
        // El listado envuelve la página del service: con el mock sin respuesta daría 500.
        when(promotionService.list(any())).thenReturn(org.springframework.data.domain.Page.empty());
    }

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
                .claim("publicId", new UUID(0L, userId).toString())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    record Endpoint(String method, String path, String body, String allowedRole) {
        @Override
        public String toString() {
            return method + " " + path + "  (" + allowedRole + ")";
        }
    }

    private static final String PROMOTION_BODY = "{"
            + "\"name\":\"Semana del 70\","
            + "\"coveragePct\":70,"
            + "\"startsAt\":\"2030-01-01T00:00:00Z\","
            + "\"endsAt\":\"2030-01-08T00:00:00Z\","
            + "\"budgetCents\":5000000"
            + "}";
    private static final String ADD_OFFER_BODY = "{\"productId\":5}";
    private static final String QUOTE_BODY = "{\"items\":[{\"productId\":5,\"quantity\":1}]}";
    private static final String EMPTY_CART_BODY = "{\"items\":[],\"keysToUse\":0}";

    private static final String ADMIN = "ADMIN";
    private static final String COMMERCIAL = "COMMERCIAL";
    private static final String CONSUMER = "CONSUMER";

    static Stream<Endpoint> protectedEndpoints() {
        return Stream.of(
                new Endpoint("GET", "/admin/key-promotions", null, ADMIN),
                new Endpoint("GET", "/admin/key-promotions/3", null, ADMIN),
                new Endpoint("POST", "/admin/key-promotions", PROMOTION_BODY, ADMIN),
                new Endpoint("PUT", "/admin/key-promotions/3", PROMOTION_BODY, ADMIN),
                new Endpoint("POST", "/admin/key-promotions/3/offers", ADD_OFFER_BODY, ADMIN),
                new Endpoint("DELETE", "/admin/key-promotions/3/offers/8", null, ADMIN),
                new Endpoint("POST", "/admin/key-promotions/3/authorize", null, ADMIN),
                new Endpoint("POST", "/admin/key-promotions/3/pause", null, ADMIN),
                new Endpoint("POST", "/admin/key-promotions/3/resume", null, ADMIN),
                new Endpoint("POST", "/admin/key-promotions/3/end", null, ADMIN),
                new Endpoint("GET", "/commercial/key-promotions/offers", null, COMMERCIAL),
                new Endpoint("POST", "/commercial/key-promotions/offers/8/accept", null, COMMERCIAL),
                new Endpoint("POST", "/commercial/key-promotions/offers/8/reject", null, COMMERCIAL),
                new Endpoint("POST", "/commercial/key-promotions/offers/8/withdraw", null, COMMERCIAL),
                new Endpoint("POST", "/purchases/quote", QUOTE_BODY, CONSUMER),
                new Endpoint("GET", "/key-promotions/active", null, CONSUMER));
    }

    /** Cada endpoint contra los dos roles que no debe aceptar. */
    static Stream<Object[]> endpointsWithWrongRoles() {
        return protectedEndpoints().flatMap(e -> Set.of(ADMIN, COMMERCIAL, CONSUMER).stream()
                .filter(role -> !role.equals(e.allowedRole()))
                .map(role -> new Object[] { e, role }));
    }

    private MockHttpServletRequestBuilder request(Endpoint e) {
        MockHttpServletRequestBuilder b = switch (e.method()) {
            case "GET" -> get(e.path());
            case "POST" -> post(e.path());
            case "PUT" -> put(e.path());
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
    @DisplayName("cada endpoint rechaza el request sin token")
    void withoutToken_isDenied(Endpoint e) throws Exception {
        int status = mockMvc.perform(request(e)).andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }

    @ParameterizedTest(name = "{1} → 403: {0}")
    @MethodSource("endpointsWithWrongRoles")
    @DisplayName("cada endpoint rechaza los roles que no acepta")
    void withWrongRole_isForbidden(Endpoint e, String wrongRole) throws Exception {
        int status = mockMvc.perform(request(e)
                        .header("Authorization", "Bearer " + tokenWithRole(1L, "ROLE_" + wrongRole)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }

    @ParameterizedTest(name = "{0} → 200")
    @MethodSource("protectedEndpoints")
    @DisplayName("cada endpoint acepta a su rol")
    void withAllowedRole_isAccepted(Endpoint e) throws Exception {
        int status = mockMvc.perform(request(e)
                        .header("Authorization", "Bearer " + tokenWithRole(1L, "ROLE_" + e.allowedRole())))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("autorizar una promoción registra al admin que la autoriza")
    void authorize_passesTheAdminId() throws Exception {
        mockMvc.perform(post("/admin/key-promotions/3/authorize")
                .header("Authorization", "Bearer " + tokenWithRole(42L, "ROLE_ADMIN")));

        verify(promotionService).authorize(3L, 42L);
    }

    @Test
    @DisplayName("el empresario solo responde con su propio id: sale del token, no de la URL")
    void acceptOffer_usesCommercialIdFromToken() throws Exception {
        mockMvc.perform(post("/commercial/key-promotions/offers/8/accept")
                .header("Authorization", "Bearer " + tokenWithRole(77L, "ROLE_COMMERCIAL")));

        verify(promotionService).acceptOffer(8L, 77L);
    }

    @Test
    @DisplayName("promoción al 71 %: 400, no llega al service")
    void promotionAboveSeventyPercent_isBadRequest() throws Exception {
        int status = mockMvc.perform(post("/admin/key-promotions")
                        .header("Authorization", "Bearer " + tokenWithRole(1L, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROMOTION_BODY.replace("\"coveragePct\":70", "\"coveragePct\":71")))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(400);
        verify(promotionService, never()).create(any());
    }

    @Test
    @DisplayName("cotización con carrito vacío: 400, no llega al service")
    void quoteWithEmptyCart_isBadRequest() throws Exception {
        int status = mockMvc.perform(post("/purchases/quote")
                        .header("Authorization", "Bearer " + tokenWithRole(1L, "ROLE_CONSUMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_CART_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(400);
        verify(keyCoverageService, never()).quote(any(), any());
    }

    /**
     * Antes el body de la compra no llevaba @Valid y las anotaciones del DTO no corrían.
     * Ahora un carrito vacío se rechaza en el controller: no se crea la compra, no se
     * reserva stock ni llaves y no se abre el checkout de Wompi.
     */
    @Test
    @DisplayName("compra con carrito vacío: 400, no llega al service")
    void buyWithEmptyCart_isBadRequest() throws Exception {
        int status = mockMvc.perform(post("/purchases/buy")
                        .header("Authorization", "Bearer " + tokenWithRole(1L, "ROLE_CONSUMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_CART_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(400);
        verify(purchaseService, never()).createPurchase(any(), any());
    }

    @Test
    @DisplayName("compra con llaves negativas: 400, no llega al service")
    void buyWithNegativeKeys_isBadRequest() throws Exception {
        int status = mockMvc.perform(post("/purchases/buy")
                        .header("Authorization", "Bearer " + tokenWithRole(1L, "ROLE_CONSUMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":5,\"quantity\":1}],\"keysToUse\":-1}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(400);
        verify(purchaseService, never()).createPurchase(any(), any());
    }
}
