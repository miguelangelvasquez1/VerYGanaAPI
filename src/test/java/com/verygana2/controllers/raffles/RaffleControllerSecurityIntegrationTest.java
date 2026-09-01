package com.verygana2.controllers.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.verygana2.testsupport.TestRsaKeys;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
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
 */
@WebMvcTest(RaffleController.class)
@Import({ SecurityConfig.class, RaffleDrawStateCache.class })
@EnableConfigurationProperties(RsaKeyProperties.class)

@DisplayName("RaffleController — /api/raffles/me sin token (integración MockMvc + Spring Security real)")
class RaffleControllerSecurityIntegrationTest {

    @DynamicPropertySource
    static void rsaKeys(DynamicPropertyRegistry registry) {
        TestRsaKeys.register(registry);
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RaffleService raffleService;
    @MockitoBean private WaitingRoomService waitingRoomService;
    // Requerido por SecurityConfig.authenticationProvider(), no se invoca en este flujo.
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    // Requerido por el FeatureFlagInterceptor global (WebMvcConfigurer), no relevante aquí.
    @MockitoBean private FeatureFlagService featureFlagService;

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
