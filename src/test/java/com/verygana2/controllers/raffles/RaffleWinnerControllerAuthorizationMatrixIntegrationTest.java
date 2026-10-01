package com.verygana2.controllers.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
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
import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.EmailVerificationService;
import com.verygana2.services.interfaces.TwilioSmsService;
import com.verygana2.services.interfaces.raffles.RaffleWinnerService;

/**
 * Barrido de autorización sobre <b>todos</b> los endpoints de
 * {@code RaffleWinnerController} (5 en total), complementando la prueba de
 * regresión puntual del hallazgo 6.4 en
 * {@link RaffleWinnerControllerSecurityIntegrationTest}.
 *
 * <ul>
 *   <li>{@code GET /winners/last} → público: responde sin token (regresión 6.4).</li>
 *   <li>El resto ({@code /my-prizes}, {@code /claim/**}) → {@code hasRole('CONSUMER')}:
 *       sin token 401/403; con un token de otro rol (COMMERCIAL) 403.</li>
 * </ul>
 *
 * Que alguien agregue un endpoint sin {@code @PreAuthorize} —o mande a
 * {@code PublicPaths} uno que no debería ser público— rompe un test aunque no
 * se añada el caso a mano.
 */
@WebMvcTest(RaffleWinnerController.class)
@Import({ SecurityConfig.class, RaffleWinnerControllerAuthorizationMatrixIntegrationTest.TestKeysConfig.class })
@DisplayName("RaffleWinnerController — matriz de autorización (todos los endpoints)")
class RaffleWinnerControllerAuthorizationMatrixIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private RaffleWinnerService raffleWinnerService;
    @MockitoBean private TwilioSmsService twilioSmsService;
    @MockitoBean private EmailVerificationService emailVerificationService;
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

    // ---------------------------------------------------------------------
    // Endpoint público
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("GET /winners/last es público: sin token responde 200")
    void lastWinners_withoutToken_respondsOk() throws Exception {
        when(raffleWinnerService.getLastRaffleWinners())
                .thenReturn(List.of(WinnerSummaryResponseDTO.builder().build()));

        int status = mockMvc.perform(get("/winners/last")).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---------------------------------------------------------------------
    // Endpoints hasRole('CONSUMER')
    // ---------------------------------------------------------------------

    record Endpoint(String method, String path, String body, Set<String> allowedRoles) {
        @Override
        public String toString() {
            return method + " " + path + "  (" + String.join("/", allowedRoles) + ")";
        }
    }

    private static final String CLAIM_BODY = "{\"prizeId\":1,\"deliveryMethod\":\"EMAIL\"}";
    private static final Set<String> CONSUMER = Set.of("CONSUMER");

    static Stream<Endpoint> consumerEndpoints() {
        return Stream.of(
                new Endpoint("GET", "/winners/my-prizes", null, CONSUMER),
                new Endpoint("POST", "/winners/claim/send-otp?phoneNumber=3001234567", null, CONSUMER),
                new Endpoint("POST", "/winners/claim/send-email-otp?email=nuevo@correo.com", null, CONSUMER),
                new Endpoint("POST", "/winners/claim", CLAIM_BODY, CONSUMER));
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
    @MethodSource("consumerEndpoints")
    @DisplayName("cada endpoint CONSUMER rechaza el request sin token")
    void consumerEndpoint_withoutToken_isDenied(Endpoint e) throws Exception {
        int status = mockMvc.perform(request(e)).andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }

    @ParameterizedTest(name = "rol no autorizado → 403: {0}")
    @MethodSource("consumerEndpoints")
    @DisplayName("cada endpoint CONSUMER rechaza un token COMMERCIAL")
    void consumerEndpoint_withWrongRole_isForbidden(Endpoint e) throws Exception {
        int status = mockMvc.perform(request(e)
                        .header("Authorization", "Bearer " + tokenWithRole(1L, "ROLE_COMMERCIAL")))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }
}
