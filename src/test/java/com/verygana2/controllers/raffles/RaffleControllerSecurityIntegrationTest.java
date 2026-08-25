package com.verygana2.controllers.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.hibernate.ObjectNotFoundException;
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
import com.verygana2.models.raffles.Raffle;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.WaitingRoomService;
import com.verygana2.services.raffles.RaffleDrawStateCache;

/**
 * {@code /api/raffles/**} está en {@link com.verygana2.security.PublicPaths}
 * como permitAll(), incluyendo {@code /me} y {@code /me/count}, que dependen
 * de {@code @AuthenticationPrincipal Jwt} para extraer el consumerId. Sin
 * token, el principal no es un Jwt y {@code jwt.getClaim("userId")} revienta
 * con NPE, que el {@code GlobalExceptionHandler} mapea a 500 en vez de un
 * 401/403 — este test levanta el filtro de seguridad real (JwtBearerFilter +
 * SecurityConfig, sin mockear autenticación) para probarlo end-to-end.
 *
 * <p>También cubre el hallazgo de auditoría 6.2: {@code GET /api/raffles}
 * (listado admin con filtro de estado) y {@code GET /api/raffles/{id}}
 * (detalle público) no distinguían DRAFT/CANCELLED/etc. de los estados
 * públicos, exponiendo rifas administrativas a clientes sin token.
 */
@WebMvcTest(RaffleController.class)
@Import({ SecurityConfig.class, RaffleDrawStateCache.class })
@EnableConfigurationProperties(RsaKeyProperties.class)
@TestPropertySource(properties = {
        "rsa.private-key=classpath:certs/private.pem",
        "rsa.public-key=classpath:certs/public.pem"
})
@DisplayName("RaffleController — autorización (integración MockMvc + Spring Security real)")
class RaffleControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RaffleService raffleService;
    @MockitoBean private WaitingRoomService waitingRoomService;
    // Requerido por SecurityConfig.authenticationProvider(), no se invoca en este flujo.
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    // Requerido por el FeatureFlagInterceptor global (WebMvcConfigurer), no relevante aquí.
    @MockitoBean private FeatureFlagService featureFlagService;

    @Test
    @DisplayName("6.2: GET /api/raffles?status=DRAFT sin token se deniega y no llega al service")
    void listRafflesDraftFilter_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/api/raffles").param("status", "DRAFT"))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("sin token, el listado administrativo debe responder 401/403, no 200")
                .isIn(401, 403);

        verify(raffleService, never()).getSummaryRafflesByFilters(
                any(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("6.2: GET /api/raffles/{id} de una rifa DRAFT sin token no expone sus datos")
    void getRaffleById_draftRaffle_withoutToken_doesNotExposeData() throws Exception {
        // El service (RaffleServiceImpl.getRaffleResponseDTOById) trata las rifas en
        // estados no públicos como inexistentes para isAdmin=false. El controller
        // debe pasar isAdmin=false para una request sin token/rol ADMIN.
        when(raffleService.getRaffleResponseDTOById(910L, false))
                .thenThrow(new ObjectNotFoundException("Raffle with id: 910 not found ", Raffle.class));

        var result = mockMvc.perform(get("/api/raffles/910")).andReturn().getResponse();

        // GlobalExceptionHandler mapea ObjectNotFoundException a 400, no 200 con datos.
        assertThat(result.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET /api/raffles/me sin Authorization responde 401/403, no 500")
    void meWithoutToken_respondsUnauthorizedNotServerError() throws Exception {
        int status = mockMvc.perform(get("/api/raffles/me").param("status", "ACTIVE"))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("sin token, /me debe responder 401/403, no 500")
                .isIn(401, 403);
    }

    @Test
    @DisplayName("GET /api/raffles/me/count sin Authorization responde 401/403, no 500")
    void meCountWithoutToken_respondsUnauthorizedNotServerError() throws Exception {
        int status = mockMvc.perform(get("/api/raffles/me/count").param("status", "ACTIVE"))
                .andReturn().getResponse().getStatus();

        assertThat(status)
                .as("sin token, /me/count debe responder 401/403, no 500")
                .isIn(401, 403);
    }
}
