package com.verygana2.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.verygana2.dtos.raffle.responses.RaffleStatsResponseDTO;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.raffles.DrawingService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;

/**
 * {@code /api/admin/raffles/**} exige {@code hasRole('ADMIN')} a nivel de
 * clase y no está en {@link com.verygana2.security.PublicPaths}. Como todos
 * los endpoints comparten el mismo nivel de autorización, este test cubre
 * una muestra representativa (GET simple, PATCH sin body, DELETE, POST con
 * body) para las 3 reglas: sin token, con rol distinto a ADMIN y con ADMIN
 * autenticado — siguiendo el mismo patrón que
 * {@code RaffleWinnerControllerSecurityIntegrationTest}.
 */
@WebMvcTest(RaffleAdminController.class)
@Import({ SecurityConfig.class, RaffleAdminControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("RaffleAdminController — autorización (integración MockMvc + Spring Security real)")
class RaffleAdminControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private DrawingService drawingService;
    @MockitoBean private RaffleService raffleService;
    @MockitoBean private RaffleTicketService raffleTicketService;
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
                .subject("admin@test.com")
                .audience(List.of("verygana-frontend"))
                .claim("type", "access")
                .claim("scope", role)
                .claim("userId", userId)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String adminToken(Long userId) {
        return tokenWithRole(userId, "ROLE_ADMIN");
    }

    private String consumerToken(Long userId) {
        return tokenWithRole(userId, "ROLE_CONSUMER");
    }

    // ---- GET /count ----

    @Test
    @DisplayName("GET /api/admin/raffles/count sin token se deniega y no llega al service")
    void countRafflesByStatus_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/api/admin/raffles/count").param("status", "ACTIVE"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(raffleService, never()).countRafflesByStatus(any());
    }

    @Test
    @DisplayName("GET /api/admin/raffles/count con rol distinto a ADMIN se deniega")
    void countRafflesByStatus_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/api/admin/raffles/count").param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(raffleService, never()).countRafflesByStatus(any());
    }

    @Test
    @DisplayName("GET /api/admin/raffles/count con ADMIN autenticado responde 200")
    void countRafflesByStatus_asAdmin_respondsOk() throws Exception {
        when(raffleService.countRafflesByStatus(RaffleStatus.ACTIVE)).thenReturn(7L);

        int status = mockMvc.perform(get("/api/admin/raffles/count").param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- GET /{raffleId}/stats ----

    @Test
    @DisplayName("GET /api/admin/raffles/{id}/stats sin token se deniega y no llega al service")
    void getRaffleStats_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/api/admin/raffles/1/stats"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(raffleService, never()).getRaffleStats(anyLong());
    }

    @Test
    @DisplayName("GET /api/admin/raffles/{id}/stats con ADMIN autenticado responde 200")
    void getRaffleStats_asAdmin_respondsOk() throws Exception {
        var expected = new RaffleStatsResponseDTO(1L, 10L, 5L, 2L, null, null);
        when(raffleService.getRaffleStats(1L)).thenReturn(expected);

        int status = mockMvc.perform(get("/api/admin/raffles/1/stats")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- PATCH /{raffleId}/activate ----

    @Test
    @DisplayName("PATCH /api/admin/raffles/{id}/activate sin token se deniega y no llega al service")
    void activateRaffle_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(patch("/api/admin/raffles/1/activate"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(raffleService, never()).activateRaffle(anyLong());
    }

    @Test
    @DisplayName("PATCH /api/admin/raffles/{id}/activate con rol distinto a ADMIN se deniega")
    void activateRaffle_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(patch("/api/admin/raffles/1/activate")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(raffleService, never()).activateRaffle(anyLong());
    }

    @Test
    @DisplayName("PATCH /api/admin/raffles/{id}/activate con ADMIN autenticado responde 204")
    void activateRaffle_asAdmin_respondsNoContent() throws Exception {
        int status = mockMvc.perform(patch("/api/admin/raffles/1/activate")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(204);
        verify(raffleService).activateRaffle(1L);
    }

    // ---- DELETE /{raffleId} ----

    @Test
    @DisplayName("DELETE /api/admin/raffles/{id} sin token se deniega y no llega al service")
    void deleteRaffle_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(delete("/api/admin/raffles/1"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(raffleService, never()).deleteRaffle(anyLong());
    }

    @Test
    @DisplayName("DELETE /api/admin/raffles/{id} con ADMIN autenticado responde 204")
    void deleteRaffle_asAdmin_respondsNoContent() throws Exception {
        int status = mockMvc.perform(delete("/api/admin/raffles/1")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(204);
        verify(raffleService).deleteRaffle(1L);
    }

    // ---- POST /confirm (endpoint con body, la seguridad debe rechazar antes de @Valid) ----

    @Test
    @DisplayName("POST /api/admin/raffles/confirm sin token se deniega y no llega al service")
    void confirmRaffleCreation_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/api/admin/raffles/confirm")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(raffleService, never()).confirmRaffleCreation(any(), any());
    }

    // Nota: no se prueba aquí "rol distinto a ADMIN" con body inválido en este
    // endpoint porque @PreAuthorize es seguridad a nivel de método (AOP sobre
    // el bean del controller): Spring MVC resuelve y valida (@Valid) los
    // argumentos antes de invocar el método proxied, así que un body vacío
    // responde 400 por validación antes de llegar al chequeo de rol. Ese caso
    // (autenticado con rol incorrecto -> 403) ya está cubierto arriba con
    // /count y /activate, que no dependen de un body válido.
}
