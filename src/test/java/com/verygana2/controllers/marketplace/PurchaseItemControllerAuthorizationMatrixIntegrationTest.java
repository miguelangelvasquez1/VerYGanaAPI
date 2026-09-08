package com.verygana2.controllers.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.verygana2.services.interfaces.finance.CashRefundService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;
import com.verygana2.services.interfaces.pqrs.PqrsService;

/**
 * Barrido de autorización sobre <b>todos</b> los endpoints de
 * {@code PurchaseItemController} (7 en total, todos con {@code @PreAuthorize}),
 * complementando la cobertura por "muestra representativa" de
 * {@link PurchaseItemControllerSecurityIntegrationTest}.
 *
 * <p>Para cada endpoint verifica solo las dos garantías de autorización:
 * <ul>
 *   <li>sin token → 401/403;</li>
 *   <li>con un token de un rol que el endpoint no acepta → 403.</li>
 * </ul>
 * No ejercita la lógica de negocio de cada endpoint (eso vive en
 * {@code PurchaseItemControllerTest}).
 */
@WebMvcTest(PurchaseItemController.class)
@Import({ SecurityConfig.class, PurchaseItemControllerAuthorizationMatrixIntegrationTest.TestKeysConfig.class })
@DisplayName("PurchaseItemController — matriz de autorización (todos los endpoints)")
class PurchaseItemControllerAuthorizationMatrixIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private PurchaseItemService purchaseItemService;
    @MockitoBean private PqrsService pqrsService;
    @MockitoBean private CashRefundService cashRefundService;
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

    record Endpoint(String method, String path, String body, Set<String> allowedRoles) {
        @Override
        public String toString() {
            return method + " " + path + "  (" + String.join("/", allowedRoles) + ")";
        }
    }

    private static final String CLAIM_BODY = "{\"pin\":\"1234\"}";
    private static final String REPORT_BODY =
            "{\"reason\":\"NOT_DELIVERED\",\"description\":\"Nunca llego el producto\"}";
    private static final String BANK_DETAILS_BODY = "{"
            + "\"accountHolderName\":\"Juan Perez\","
            + "\"accountHolderDoc\":\"123456789\","
            + "\"accountHolderDocType\":\"CC\","
            + "\"bankName\":\"Bancolombia\","
            + "\"accountNumber\":\"1234567890\","
            + "\"accountType\":\"SAVINGS\""
            + "}";

    private static final Set<String> COMMERCIAL = Set.of("COMMERCIAL");
    private static final Set<String> CONSUMER = Set.of("CONSUMER");

    static Stream<Endpoint> protectedEndpoints() {
        return Stream.of(
                new Endpoint("GET", "/purchaseItems/totalSales", null, COMMERCIAL),
                new Endpoint("GET", "/purchaseItems/topSelling", null, COMMERCIAL),
                new Endpoint("GET", "/purchaseItems/pending-claims", null, COMMERCIAL),
                new Endpoint("POST", "/purchaseItems/5/claim", CLAIM_BODY, COMMERCIAL),
                new Endpoint("GET", "/purchaseItems/5/delivered-code", null, CONSUMER),
                new Endpoint("POST", "/purchaseItems/5/report", REPORT_BODY, CONSUMER),
                new Endpoint("POST", "/purchaseItems/5/cash-refund/bank-details", BANK_DETAILS_BODY, CONSUMER));
    }

    private MockHttpServletRequestBuilder request(Endpoint e) {
        MockHttpServletRequestBuilder b = switch (e.method()) {
            case "GET" -> get(e.path());
            case "POST" -> post(e.path());
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

    @ParameterizedTest(name = "rol no autorizado → 403: {0}")
    @MethodSource("protectedEndpoints")
    @DisplayName("cada endpoint rechaza un token de un rol que no acepta")
    void withWrongRole_isForbidden(Endpoint e) throws Exception {
        String wrongRole = e.allowedRoles().contains("CONSUMER") ? "ROLE_COMMERCIAL" : "ROLE_CONSUMER";

        int status = mockMvc.perform(request(e)
                        .header("Authorization", "Bearer " + tokenWithRole(1L, wrongRole)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }
}
