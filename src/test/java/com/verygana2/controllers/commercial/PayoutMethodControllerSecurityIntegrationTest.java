package com.verygana2.controllers.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.finance.PayoutMethodService;

/**
 * {@code PayoutMethodController} tiene {@code @PreAuthorize("hasRole('COMMERCIAL')")}
 * a nivel de CLASE, aplicando a los 8 endpoints. Dado que la regla de
 * autorización es la misma para todos, se prueba el trío completo
 * (sin token / rol incorrecto / COMMERCIAL) en los endpoints con lógica de
 * negocio más sensible — crear, verify-otp, set-default — y sin-token +
 * COMMERCIAL en el resto, siguiendo el mismo criterio que
 * {@code AllyPromotionControllerSecurityIntegrationTest}.
 */
@WebMvcTest(PayoutMethodController.class)
@Import({ SecurityConfig.class, PayoutMethodControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("PayoutMethodController — autorización (integración MockMvc + Spring Security real)")
class PayoutMethodControllerSecurityIntegrationTest {

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
                .subject("user@test.com")
                .audience(List.of("verygana-frontend"))
                .claim("type", "access")
                .claim("scope", role)
                .claim("userId", userId)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String commercialToken(Long userId) {
        return tokenWithRole(userId, "ROLE_COMMERCIAL");
    }

    private String consumerToken(Long userId) {
        return tokenWithRole(userId, "ROLE_CONSUMER");
    }

    // ---- POST /commercial/payout-methods (crear) ----

    private static final String CREATE_BODY = "{"
            + "\"type\":\"NEQUI\","
            + "\"alias\":\"Mi Nequi\","
            + "\"phoneNumber\":\"3001234567\","
            + "\"accountHolderName\":\"Juan Perez\","
            + "\"accountHolderDocType\":\"CC\","
            + "\"accountHolderDoc\":\"123456789\""
            + "}";

    @Test
    @DisplayName("POST /commercial/payout-methods sin token se deniega y no llega al service")
    void create_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/commercial/payout-methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutMethodService, never()).createPayoutMethod(any(), any());
    }

    @Test
    @DisplayName("POST /commercial/payout-methods con rol distinto a COMMERCIAL se deniega")
    void create_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/commercial/payout-methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY)
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(payoutMethodService, never()).createPayoutMethod(any(), any());
    }

    @Test
    @DisplayName("POST /commercial/payout-methods con COMMERCIAL autenticado responde 201")
    void create_asCommercial_respondsCreated() throws Exception {
        when(payoutMethodService.createPayoutMethod(any(), any()))
                .thenReturn(new EntityCreatedResponseDTO(1L, "created", Instant.now()));

        int status = mockMvc.perform(post("/commercial/payout-methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY)
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(201);
        verify(payoutMethodService).createPayoutMethod(any(Long.class), any());
    }

    // ---- GET /commercial/payout-methods/banks ----

    @Test
    @DisplayName("GET /commercial/payout-methods/banks sin token se deniega y no llega al service")
    void getBanks_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/commercial/payout-methods/banks"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutMethodService, never()).getAvailableBanks();
    }

    @Test
    @DisplayName("GET /commercial/payout-methods/banks con COMMERCIAL autenticado responde 200")
    void getBanks_asCommercial_respondsOk() throws Exception {
        when(payoutMethodService.getAvailableBanks()).thenReturn(List.of());

        int status = mockMvc.perform(get("/commercial/payout-methods/banks")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- POST /commercial/payout-methods/{id}/verify-otp ----

    private static final String VERIFY_OTP_BODY = "{\"code\":\"123456\"}";

    @Test
    @DisplayName("POST /commercial/payout-methods/{id}/verify-otp sin token se deniega y no llega al service")
    void verifyOtp_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/commercial/payout-methods/1/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VERIFY_OTP_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutMethodService, never()).verifyOtp(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("POST /commercial/payout-methods/{id}/verify-otp con rol distinto a COMMERCIAL se deniega")
    void verifyOtp_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/commercial/payout-methods/1/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VERIFY_OTP_BODY)
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(payoutMethodService, never()).verifyOtp(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("POST /commercial/payout-methods/{id}/verify-otp con COMMERCIAL autenticado responde 200")
    void verifyOtp_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(post("/commercial/payout-methods/1/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VERIFY_OTP_BODY)
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(payoutMethodService).verifyOtp(9L, 1L, "123456");
    }

    // ---- POST /commercial/payout-methods/{id}/resend-otp ----

    @Test
    @DisplayName("POST /commercial/payout-methods/{id}/resend-otp sin token se deniega y no llega al service")
    void resendOtp_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/commercial/payout-methods/1/resend-otp"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutMethodService, never()).resendOtp(anyLong(), anyLong());
    }

    @Test
    @DisplayName("POST /commercial/payout-methods/{id}/resend-otp con COMMERCIAL autenticado responde 200")
    void resendOtp_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(post("/commercial/payout-methods/1/resend-otp")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(payoutMethodService).resendOtp(9L, 1L);
    }

    // ---- GET /commercial/payout-methods (listar) ----

    @Test
    @DisplayName("GET /commercial/payout-methods sin token se deniega y no llega al service")
    void getAll_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/commercial/payout-methods"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutMethodService, never()).getByCommercialId(anyLong(), any());
    }

    @Test
    @DisplayName("GET /commercial/payout-methods con COMMERCIAL autenticado responde 200")
    void getAll_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/commercial/payout-methods")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- PUT /commercial/payout-methods/{id}/deactivate ----

    @Test
    @DisplayName("PUT /commercial/payout-methods/{id}/deactivate sin token se deniega y no llega al service")
    void deactivate_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(put("/commercial/payout-methods/1/deactivate"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutMethodService, never()).deactivatePayoutMethod(anyLong(), anyLong());
    }

    @Test
    @DisplayName("PUT /commercial/payout-methods/{id}/deactivate con COMMERCIAL autenticado responde 200")
    void deactivate_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(put("/commercial/payout-methods/1/deactivate")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(payoutMethodService).deactivatePayoutMethod(9L, 1L);
    }

    // ---- PUT /commercial/payout-methods/{id}/set-default ----

    @Test
    @DisplayName("PUT /commercial/payout-methods/{id}/set-default sin token se deniega y no llega al service")
    void setDefault_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(put("/commercial/payout-methods/1/set-default"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutMethodService, never()).setDefaultPayoutMethod(anyLong(), anyLong());
    }

    @Test
    @DisplayName("PUT /commercial/payout-methods/{id}/set-default con rol distinto a COMMERCIAL se deniega")
    void setDefault_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(put("/commercial/payout-methods/1/set-default")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(payoutMethodService, never()).setDefaultPayoutMethod(anyLong(), anyLong());
    }

    @Test
    @DisplayName("PUT /commercial/payout-methods/{id}/set-default con COMMERCIAL autenticado responde 200")
    void setDefault_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(put("/commercial/payout-methods/1/set-default")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(payoutMethodService).setDefaultPayoutMethod(9L, 1L);
    }

    // ---- POST /commercial/payout-methods/{id}/certificate/prepare ----

    private static final String PREPARE_CERT_BODY = "{"
            + "\"originalFileName\":\"cert.pdf\","
            + "\"contentType\":\"application/pdf\","
            + "\"sizeBytes\":1024"
            + "}";

    @Test
    @DisplayName("POST /commercial/payout-methods/{id}/certificate/prepare sin token se deniega y no llega al service")
    void prepareCertificateUpload_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/commercial/payout-methods/1/certificate/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PREPARE_CERT_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutMethodService, never()).prepareCertificateUpload(any(), any(), any());
    }

    @Test
    @DisplayName("POST /commercial/payout-methods/{id}/certificate/prepare con COMMERCIAL autenticado responde 200")
    void prepareCertificateUpload_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(post("/commercial/payout-methods/1/certificate/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PREPARE_CERT_BODY)
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(payoutMethodService).prepareCertificateUpload(eq(9L), eq(1L), any());
    }

    // ---- POST /commercial/payout-methods/{id}/certificate/confirm ----

    private static final String CONFIRM_CERT_BODY = "{\"certificateAssetId\":55}";

    @Test
    @DisplayName("POST /commercial/payout-methods/{id}/certificate/confirm sin token se deniega y no llega al service")
    void confirmCertificateUpload_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/commercial/payout-methods/1/certificate/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_CERT_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(payoutMethodService, never()).confirmCertificateUpload(any(), any(), any());
    }

    @Test
    @DisplayName("POST /commercial/payout-methods/{id}/certificate/confirm con COMMERCIAL autenticado responde 200")
    void confirmCertificateUpload_asCommercial_respondsOk() throws Exception {
        when(payoutMethodService.confirmCertificateUpload(any(), any(), any()))
                .thenReturn(new EntityCreatedResponseDTO(1L, "confirmed", Instant.now()));

        int status = mockMvc.perform(post("/commercial/payout-methods/1/certificate/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_CERT_BODY)
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(payoutMethodService).confirmCertificateUpload(9L, 1L, 55L);
    }
}
