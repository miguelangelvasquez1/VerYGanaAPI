package com.verygana2.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.pqrs.responses.PqrsAdminDetailDTO;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.pqrs.PqrsService;

/**
 * {@code PqrsAdminController} exige {@code hasRole('ADMIN')} a nivel de
 * clase. Mismo patrón MockMvc + Spring Security real que
 * {@code TreasuryAdminControllerSecurityIntegrationTest}: sin token
 * denegado, con rol distinto a ADMIN forbidden (403), con ADMIN 200.
 */
@WebMvcTest(PqrsAdminController.class)
@Import({ SecurityConfig.class, PqrsAdminControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("PqrsAdminController — autorización (integración MockMvc + Spring Security real)")
class PqrsAdminControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private PqrsService pqrsService;
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

    private String adminToken(Long userId) {
        return tokenWithRole(userId, "ROLE_ADMIN");
    }

    private String commercialToken(Long userId) {
        return tokenWithRole(userId, "ROLE_COMMERCIAL");
    }

    private String consumerToken(Long userId) {
        return tokenWithRole(userId, "ROLE_CONSUMER");
    }

    // ---- GET /admin/pqrs ----

    @Test
    @DisplayName("GET /admin/pqrs sin token se deniega y no llega al service")
    void getAssignedPqrs_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/admin/pqrs"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(pqrsService, never()).getAssignedPqrs(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /admin/pqrs con rol distinto a ADMIN (COMMERCIAL) se deniega")
    void getAssignedPqrs_withCommercial_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/admin/pqrs")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(pqrsService, never()).getAssignedPqrs(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /admin/pqrs con rol distinto a ADMIN (CONSUMER) se deniega")
    void getAssignedPqrs_withConsumer_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/admin/pqrs")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(pqrsService, never()).getAssignedPqrs(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /admin/pqrs con ADMIN autenticado responde 200")
    void getAssignedPqrs_asAdmin_respondsOk() throws Exception {
        when(pqrsService.getAssignedPqrs(any(), any(), any(), any()))
                .thenReturn(PagedResponse.<PqrsAdminDetailDTO>builder().build());

        int status = mockMvc.perform(get("/admin/pqrs")
                        .header("Authorization", "Bearer " + adminToken(99L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- GET /admin/pqrs/{id} ----

    @Test
    @DisplayName("GET /admin/pqrs/{id} sin token se deniega y no llega al service")
    void getPqrsDetail_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/admin/pqrs/1"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(pqrsService, never()).getPqrsDetailForAdmin(anyLong(), anyLong());
    }

    @Test
    @DisplayName("GET /admin/pqrs/{id} con rol distinto a ADMIN se deniega")
    void getPqrsDetail_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/admin/pqrs/1")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(pqrsService, never()).getPqrsDetailForAdmin(anyLong(), anyLong());
    }

    @Test
    @DisplayName("GET /admin/pqrs/{id} con ADMIN autenticado responde 200")
    void getPqrsDetail_asAdmin_respondsOk() throws Exception {
        when(pqrsService.getPqrsDetailForAdmin(1L, 99L)).thenReturn(new PqrsAdminDetailDTO());

        int status = mockMvc.perform(get("/admin/pqrs/1")
                        .header("Authorization", "Bearer " + adminToken(99L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- PATCH /admin/pqrs/{id}/review ----

    @Test
    @DisplayName("PATCH /admin/pqrs/{id}/review sin token se deniega y no llega al service")
    void markUnderReview_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(patch("/admin/pqrs/1/review"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(pqrsService, never()).markUnderReview(anyLong(), anyLong());
    }

    @Test
    @DisplayName("PATCH /admin/pqrs/{id}/review con rol distinto a ADMIN se deniega")
    void markUnderReview_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(patch("/admin/pqrs/1/review")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(pqrsService, never()).markUnderReview(anyLong(), anyLong());
    }

    @Test
    @DisplayName("PATCH /admin/pqrs/{id}/review con ADMIN autenticado responde 200")
    void markUnderReview_asAdmin_respondsOk() throws Exception {
        int status = mockMvc.perform(patch("/admin/pqrs/1/review")
                        .header("Authorization", "Bearer " + adminToken(99L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(pqrsService).markUnderReview(1L, 99L);
    }

    // ---- PATCH /admin/pqrs/{id}/respond ----

    private static final String RESPOND_BODY = "{\"response\":\"Ya se solucionó tu caso\"}";

    @Test
    @DisplayName("PATCH /admin/pqrs/{id}/respond sin token se deniega y no llega al service")
    void respondToPqrs_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(patch("/admin/pqrs/1/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RESPOND_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(pqrsService, never()).respondToPqrs(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("PATCH /admin/pqrs/{id}/respond con rol distinto a ADMIN se deniega")
    void respondToPqrs_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(patch("/admin/pqrs/1/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RESPOND_BODY)
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(pqrsService, never()).respondToPqrs(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("PATCH /admin/pqrs/{id}/respond con ADMIN autenticado responde 200")
    void respondToPqrs_asAdmin_respondsOk() throws Exception {
        int status = mockMvc.perform(patch("/admin/pqrs/1/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RESPOND_BODY)
                        .header("Authorization", "Bearer " + adminToken(99L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(pqrsService).respondToPqrs(org.mockito.ArgumentMatchers.eq(1L), any(), org.mockito.ArgumentMatchers.eq(99L));
    }
}
