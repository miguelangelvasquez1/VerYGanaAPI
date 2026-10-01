package com.verygana2.controllers.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;

/**
 * {@code /api/my/raffle-tickets/**} exige {@code hasRole('CONSUMER')} a nivel
 * de clase y no está en {@link com.verygana2.security.PublicPaths}. Este test
 * levanta el filtro de seguridad real (JwtBearerFilter + SecurityConfig) para
 * probar sin token, con un rol distinto (COMMERCIAL) y con CONSUMER
 * autenticado, siguiendo el mismo patrón que
 * {@code RaffleWinnerControllerSecurityIntegrationTest}.
 */
@WebMvcTest(UserRaffleTicketController.class)
@Import({ SecurityConfig.class, UserRaffleTicketControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("UserRaffleTicketController — autorización (integración MockMvc + Spring Security real)")
class UserRaffleTicketControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

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
                .subject("user@test.com")
                .audience(List.of("verygana-frontend"))
                .claim("type", "access")
                .claim("scope", role)
                .claim("userId", userId)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String consumerToken(Long userId) {
        return tokenWithRole(userId, "ROLE_CONSUMER");
    }

    private String commercialToken(Long userId) {
        return tokenWithRole(userId, "ROLE_COMMERCIAL");
    }

    @Test
    @DisplayName("GET /api/my/raffle-tickets/raffle/{id} sin token se deniega y no llega al service")
    void getUserTicketsByRaffle_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/api/my/raffle-tickets/raffle/1"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(raffleTicketService, never()).getUserTicketsByRaffle(any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/my/raffle-tickets/raffle/{id} con rol distinto a CONSUMER se deniega")
    void getUserTicketsByRaffle_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/api/my/raffle-tickets/raffle/1")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(raffleTicketService, never()).getUserTicketsByRaffle(any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/my/raffle-tickets/raffle/{id} con CONSUMER autenticado responde 200")
    void getUserTicketsByRaffle_asConsumer_respondsOk() throws Exception {
        var expected = PagedResponse.<RaffleTicketResponseDTO>builder().build();
        when(raffleTicketService.getUserTicketsByRaffle(9L, 1L, PageRequest.of(0, 10))).thenReturn(expected);

        int status = mockMvc.perform(get("/api/my/raffle-tickets/raffle/1")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /api/my/raffle-tickets/winners sin token se deniega y no llega al service")
    void getUserWinnerTickets_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/api/my/raffle-tickets/winners"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(raffleTicketService, never()).getUserWinnerTickets(any(), any());
    }

    @Test
    @DisplayName("GET /api/my/raffle-tickets/winners con CONSUMER autenticado responde 200")
    void getUserWinnerTickets_asConsumer_respondsOk() throws Exception {
        var expected = PagedResponse.<RaffleTicketResponseDTO>builder().build();
        when(raffleTicketService.getUserWinnerTickets(9L, PageRequest.of(0, 10))).thenReturn(expected);

        int status = mockMvc.perform(get("/api/my/raffle-tickets/winners")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /api/my/raffle-tickets/winners/balance sin token se deniega y no llega al service")
    void getWinnerUserTotalTickets_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/api/my/raffle-tickets/winners/balance"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(raffleTicketService, never()).getUserWinnerTotalTickets(anyLong());
    }

    @Test
    @DisplayName("GET /api/my/raffle-tickets/winners/balance con rol distinto a CONSUMER se deniega")
    void getWinnerUserTotalTickets_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/api/my/raffle-tickets/winners/balance")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(raffleTicketService, never()).getUserWinnerTotalTickets(anyLong());
    }

    @Test
    @DisplayName("GET /api/my/raffle-tickets/winners/balance con CONSUMER autenticado responde 200")
    void getWinnerUserTotalTickets_asConsumer_respondsOk() throws Exception {
        when(raffleTicketService.getUserWinnerTotalTickets(9L)).thenReturn(3L);

        int status = mockMvc.perform(get("/api/my/raffle-tickets/winners/balance")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }
}
