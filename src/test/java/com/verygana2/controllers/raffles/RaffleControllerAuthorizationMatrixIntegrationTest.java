package com.verygana2.controllers.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.verygana2.models.raffles.Raffle;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.WaitingRoomService;
import com.verygana2.services.raffles.RaffleDrawStateCache;

/**
 * Barrido de autorización sobre <b>todos</b> los endpoints de
 * {@code RaffleController}, complementando las pruebas de regresión puntuales
 * de los hallazgos 6.2 en {@link RaffleControllerSecurityIntegrationTest}.
 *
 * <p>{@code /api/raffles/**} está en {@link com.verygana2.security.PublicPaths}
 * como {@code permitAll()}, así que el control de acceso vive en el
 * {@code @PreAuthorize} de cada método. Los buckets:
 * <ul>
 *   <li><b>Públicos</b> ({@code /{id}}, {@code /lives}, {@code /actives},
 *       {@code /{id}/draw-status}) → responden sin token (la cadena de
 *       seguridad NO los bloquea).</li>
 *   <li><b>ADMIN</b> ({@code GET /api/raffles}, listado con filtro de estado) →
 *       sin token 401/403; con token de otro rol 403; con ADMIN 200. Esto
 *       completa el hallazgo 6.2: la ruta es {@code permitAll()} pero el
 *       {@code @PreAuthorize("hasRole('ADMIN')")} debe seguir aplicando.</li>
 *   <li><b>{@code isAuthenticated()}</b> ({@code /me}, {@code /me/count}) → sin
 *       token 401/403 (no 500); con cualquier rol autenticado, pasa.</li>
 * </ul>
 */
@WebMvcTest(RaffleController.class)
@Import({ SecurityConfig.class, RaffleDrawStateCache.class,
        RaffleControllerAuthorizationMatrixIntegrationTest.TestKeysConfig.class })
@DisplayName("RaffleController — matriz de autorización (todos los endpoints)")
class RaffleControllerAuthorizationMatrixIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private RaffleService raffleService;
    @MockitoBean private WaitingRoomService waitingRoomService;
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

    @BeforeEach
    void stubRaffleLookupForDrawStatus() {
        // /{id}/draw-status navega raffle.getDrawDate(); sin este stub el
        // controller lanzaría NPE (500) y ocultaría lo que el test mide (que la
        // cadena de seguridad deja pasar el request).
        Raffle raffle = new Raffle();
        raffle.setDrawDate(ZonedDateTime.now(ZoneOffset.UTC).plusHours(1));
        lenient().when(raffleService.getRaffleById(anyLong())).thenReturn(raffle);
    }

    // ---------------------------------------------------------------------
    // Públicos — la cadena de seguridad no bloquea (no 401/403)
    // ---------------------------------------------------------------------

    @ParameterizedTest(name = "público sin token → no 401/403: GET {0}")
    @ValueSource(strings = {
            "/api/raffles/1",
            "/api/raffles/lives",
            "/api/raffles/actives?pageNumber=0",
            "/api/raffles/1/draw-status"
    })
    @DisplayName("cada endpoint público responde sin token")
    void publicEndpoint_withoutToken_isNotBlocked(String path) throws Exception {
        int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    // ---------------------------------------------------------------------
    // GET /api/raffles — hasRole('ADMIN') (hallazgo 6.2)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/raffles?status=DRAFT sin token → 401/403")
    void adminList_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/api/raffles").param("status", "DRAFT"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("GET /api/raffles?status=DRAFT con token no-ADMIN (CONSUMER) → 403")
    void adminList_withNonAdminToken_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/api/raffles").param("status", "DRAFT")
                        .header("Authorization", "Bearer " + tokenWithRole(1L, "ROLE_CONSUMER")))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /api/raffles?status=DRAFT con ADMIN → 200")
    void adminList_withAdminToken_isOk() throws Exception {
        int status = mockMvc.perform(get("/api/raffles").param("status", "DRAFT")
                        .header("Authorization", "Bearer " + tokenWithRole(9L, "ROLE_ADMIN")))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(200);
    }

    // ---------------------------------------------------------------------
    // /me y /me/count — isAuthenticated()
    // ---------------------------------------------------------------------

    @ParameterizedTest(name = "sin token → 401/403 (no 500): GET {0}")
    @ValueSource(strings = { "/api/raffles/me?status=ACTIVE", "/api/raffles/me/count?status=ACTIVE" })
    @DisplayName("cada endpoint isAuthenticated() rechaza el request sin token con 401/403, no 500")
    void authenticatedEndpoint_withoutToken_isDenied(String path) throws Exception {
        int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }

    @ParameterizedTest(name = "cualquier rol autenticado pasa: GET {0}")
    @ValueSource(strings = { "/api/raffles/me?status=ACTIVE", "/api/raffles/me/count?status=ACTIVE" })
    @DisplayName("isAuthenticated() no exige rol: un token CONSUMER pasa el control")
    void authenticatedEndpoint_withAuthenticatedToken_isAllowed(String path) throws Exception {
        int status = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + tokenWithRole(9L, "ROLE_CONSUMER")))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }
}
