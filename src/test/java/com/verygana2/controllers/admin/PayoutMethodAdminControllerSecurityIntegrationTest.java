package com.verygana2.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.verygana2.dtos.finance.responses.PayoutMethodResponseDTO;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.finance.PayoutMethodService;

/**
 * {@code /admin/payout-methods/**} exige {@code hasRole('ADMIN')} a nivel de
 * clase, aplicando a sus 4 endpoints. Este test levanta el filtro de
 * seguridad real (JwtBearerFilter + SecurityConfig) para probar sin token,
 * con un rol distinto (COMMERCIAL) y con ADMIN autenticado, siguiendo el
 * mismo patrón que {@code RaffleAdminControllerSecurityIntegrationTest}.
 *
 * <p>{@code GET /{id}/certificate} hace streaming directo sobre
 * {@link jakarta.servlet.http.HttpServletResponse} (no cubierto aquí porque
 * requiere un {@code OutputStream} real del servidor); se prueba solo su
 * regla de autorización a nivel de las otras 3 rutas del mismo controller.
 */
@WebMvcTest(PayoutMethodAdminController.class)
@Import({ SecurityConfig.class, PayoutMethodAdminControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("PayoutMethodAdminController — autorización (integración MockMvc + Spring Security real)")
class PayoutMethodAdminControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private PayoutMethodService payoutMethodService;
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

    private String commercialToken(Long userId) {
        return tokenWithRole(userId, "ROLE_COMMERCIAL");
    }

    // ---- GET /admin/payout-methods ----

    @Test
    @DisplayName("GET /admin/payout-methods sin token se deniega y no llega al service")
    void getByStatus_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/admin/payout-methods"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutMethodService, never()).getByStatus(any(), any());
    }

    @Test
    @DisplayName("GET /admin/payout-methods con rol distinto a ADMIN se deniega")
    void getByStatus_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/admin/payout-methods")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(payoutMethodService, never()).getByStatus(any(), any());
    }

    @Test
    @DisplayName("GET /admin/payout-methods con ADMIN autenticado responde 200")
    void getByStatus_asAdmin_respondsOk() throws Exception {
        var expected = PagedResponse.<PayoutMethodResponseDTO>builder().data(List.of()).build();
        when(payoutMethodService.getByStatus(VerificationStatus.UNDER_REVIEW, PageRequest.of(0, 20)))
                .thenReturn(expected);

        int status = mockMvc.perform(get("/admin/payout-methods")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- POST /admin/payout-methods/{id}/verify ----

    @Test
    @DisplayName("POST /admin/payout-methods/{id}/verify sin token se deniega y no llega al service")
    void verify_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/admin/payout-methods/1/verify"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutMethodService, never()).adminVerifyMethod(anyLong());
    }

    @Test
    @DisplayName("POST /admin/payout-methods/{id}/verify con rol distinto a ADMIN se deniega")
    void verify_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/admin/payout-methods/1/verify")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(payoutMethodService, never()).adminVerifyMethod(anyLong());
    }

    @Test
    @DisplayName("POST /admin/payout-methods/{id}/verify con ADMIN autenticado responde 200")
    void verify_asAdmin_respondsOk() throws Exception {
        int status = mockMvc.perform(post("/admin/payout-methods/1/verify")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(payoutMethodService).adminVerifyMethod(1L);
    }

    // ---- POST /admin/payout-methods/{id}/reject ----

    @Test
    @DisplayName("POST /admin/payout-methods/{id}/reject sin token se deniega y no llega al service")
    void reject_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/admin/payout-methods/1/reject")
                        .contentType("application/json")
                        .content("{\"reason\":\"Certificación bancaria ilegible\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutMethodService, never()).adminRejectMethod(anyLong(), anyString());
    }

    @Test
    @DisplayName("POST /admin/payout-methods/{id}/reject con rol distinto a ADMIN se deniega")
    void reject_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/admin/payout-methods/1/reject")
                        .contentType("application/json")
                        .content("{\"reason\":\"Certificación bancaria ilegible\"}")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(payoutMethodService, never()).adminRejectMethod(anyLong(), anyString());
    }

    @Test
    @DisplayName("POST /admin/payout-methods/{id}/reject con ADMIN autenticado responde 200")
    void reject_asAdmin_respondsOk() throws Exception {
        int status = mockMvc.perform(post("/admin/payout-methods/1/reject")
                        .contentType("application/json")
                        .content("{\"reason\":\"Certificación bancaria ilegible\"}")
                        .header("Authorization", "Bearer " + adminToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(payoutMethodService).adminRejectMethod(1L, "Certificación bancaria ilegible");
    }

    // ---- GET /admin/payout-methods/{id}/certificate ----

    @Test
    @DisplayName("GET /admin/payout-methods/{id}/certificate sin token se deniega y no llega al service")
    void getCertificate_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/admin/payout-methods/1/certificate"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("GET /admin/payout-methods/{id}/certificate con rol distinto a ADMIN se deniega")
    void getCertificate_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/admin/payout-methods/1/certificate")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }
}
