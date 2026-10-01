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
import com.verygana2.dtos.raffle.responses.PrizeWonResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.EmailVerificationService;
import com.verygana2.services.interfaces.TwilioSmsService;
import com.verygana2.services.interfaces.raffles.RaffleWinnerService;

/**
 * Hallazgo de auditoría 6.4: {@code GET /winners/last} debe ser público (los
 * demás endpoints del controller ya validaban correctamente auth+rol
 * CONSUMER). Este test levanta el filtro de seguridad real (JwtBearerFilter +
 * SecurityConfig) para probar las 4 reglas del control end-to-end, incluyendo
 * el caso autenticado con un JWT real firmado por las mismas llaves que usa
 * el decoder (mismo patrón que {@code PublicPathsAuthorizationTest}).
 */
@WebMvcTest(RaffleWinnerController.class)
@Import({ SecurityConfig.class, RaffleWinnerControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("RaffleWinnerController — autorización (integración MockMvc + Spring Security real)")
class RaffleWinnerControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private RaffleWinnerService raffleWinnerService;
    @MockitoBean private TwilioSmsService twilioSmsService;
    @MockitoBean private EmailVerificationService emailVerificationService;
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
                .subject("consumer@test.com")
                .audience(List.of("verygana-frontend"))
                .claim("type", "access")
                .claim("scope", "ROLE_CONSUMER")
                .claim("userId", userId)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    @Test
    @DisplayName("6.4: GET /winners/last sin token responde 200 (debe ser público)")
    void getLastWinners_withoutToken_respondsOk() throws Exception {
        when(raffleWinnerService.getLastRaffleWinners()).thenReturn(List.of(WinnerSummaryResponseDTO.builder().build()));

        int status = mockMvc.perform(get("/winners/last")).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("6.4: GET /winners/my-prizes sin token se deniega")
    void getWonPrizes_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/winners/my-prizes")).andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("6.4: GET /winners/my-prizes con CONSUMER autenticado responde 200 paginado")
    void getWonPrizes_asConsumer_respondsOk() throws Exception {
        when(raffleWinnerService.getWonPrizesList(9L, null, PageRequest.of(0, 10)))
                .thenReturn(PagedResponse.<PrizeWonResponseDTO>builder().build());

        int status = mockMvc.perform(get("/winners/my-prizes")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("6.4: POST /winners/claim sin token se deniega")
    void claimPrize_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/winners/claim")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }
}
