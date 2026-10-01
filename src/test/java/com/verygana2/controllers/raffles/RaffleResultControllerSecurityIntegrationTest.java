package com.verygana2.controllers.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
import com.verygana2.dtos.raffle.responses.DrawProofResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleResultResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResultResponseDTO;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.raffles.RaffleResultService;

/**
 * Hallazgo de auditoría 6.3: {@code /results/**} (resultado por rifa, último
 * resultado, y prueba/evidencia del sorteo) están diseñados como públicos —
 * confirmado por el {@code endpoint_prefix = '/results'} de la feature
 * RAFFLE_RESULTS en system-features.sql — pero no estaban en
 * {@link com.verygana2.security.PublicPaths}, así que la cadena de seguridad
 * los rechazaba con 401 "Invalid or expired JWT" antes de llegar al
 * controller. Este test levanta el filtro de seguridad real (sin mockear
 * autenticación) para probar que ahora responden sin token.
 */
@WebMvcTest(RaffleResultController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(RsaKeyProperties.class)
@TestPropertySource(properties = {
        "rsa.private-key=classpath:certs/private.pem",
        "rsa.public-key=classpath:certs/public.pem"
})
@DisplayName("RaffleResultController — /results/** sin token (integración MockMvc + Spring Security real)")
class RaffleResultControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RaffleResultService raffleResultService;
    // Requerido por SecurityConfig.authenticationProvider(), no se invoca en este flujo.
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    // Requerido por el FeatureFlagInterceptor global (WebMvcConfigurer), no relevante aquí.
    @MockitoBean private FeatureFlagService featureFlagService;

    @Test
    @DisplayName("6.3: GET /results/raffle/{id} sin token responde 200, no 401")
    void getRaffleResultByRaffleId_withoutToken_respondsOk() throws Exception {
        when(raffleResultService.getResultByRaffleId(932L)).thenReturn(new RaffleResultResponseDTO());

        int status = mockMvc.perform(get("/results/raffle/932")).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("6.3: GET /results/last sin token responde 200, no 401")
    void getLastRaffleResults_withoutToken_respondsOk() throws Exception {
        when(raffleResultService.getLastRaffleResults())
                .thenReturn(List.of(new RaffleSummaryResultResponseDTO()));

        int status = mockMvc.perform(get("/results/last")).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("6.3: GET /results/raffle/{id}/draw-proof sin token responde 200, no 401")
    void getDrawProofByRaffleId_withoutToken_respondsOk() throws Exception {
        when(raffleResultService.getDrawProofByRaffleId(932L)).thenReturn(DrawProofResponseDTO.builder().build());

        int status = mockMvc.perform(get("/results/raffle/932/draw-proof")).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }
}
