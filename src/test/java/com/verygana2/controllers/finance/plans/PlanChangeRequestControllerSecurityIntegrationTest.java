package com.verygana2.controllers.finance.plans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import java.time.ZonedDateTime;
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
import com.verygana2.dtos.finance.plans.responses.PlanChangePreviewResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.models.commercial.PlanChangeRequest;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.commercial.CommercialContractService;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.PlanChangeRequestService;
import com.verygana2.services.interfaces.finance.PlanService;

/**
 * {@code /plans/change-request/**} exige {@code hasRole('COMMERCIAL')} a nivel
 * de clase, aplicando a sus 6 endpoints. Este test levanta el filtro de
 * seguridad real (JwtBearerFilter + SecurityConfig) para probar sin token, con
 * un rol distinto (CONSUMER) y con COMMERCIAL autenticado, siguiendo el mismo
 * patrón que {@code AllyPromotionControllerSecurityIntegrationTest}.
 */
@WebMvcTest(PlanChangeRequestController.class)
@Import({ SecurityConfig.class, PlanChangeRequestControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("PlanChangeRequestController — autorización (integración MockMvc + Spring Security real)")
class PlanChangeRequestControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private PlanChangeRequestService planChangeRequestService;
    @MockitoBean private CommercialContractService contractService;
    @MockitoBean private PlanService planService;
    @MockitoBean private CommercialDetailsService commercialDetailsService;
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

    private PlanChangeRequest samplePlanChangeRequest() {
        Plan toPlan = new Plan();
        toPlan.setCode(PlanCode.STANDARD);

        PlanChangeRequest r = new PlanChangeRequest();
        r.setId(5L);
        r.setToPlan(toPlan);
        r.setRequiredTopUpAmountCents(0L);
        r.setStatus(PlanChangeRequestStatus.REQUESTED);
        r.setRequestedAt(ZonedDateTime.now());
        return r;
    }

    // ---- GET /plans/change-request/preview ----

    @Test
    @DisplayName("GET /plans/change-request/preview sin token se deniega y no llega al service")
    void previewPlanChange_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/plans/change-request/preview")
                        .param("targetPlanCode", "STANDARD"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(planChangeRequestService, never()).previewPlanChange(any(), any(), any());
    }

    @Test
    @DisplayName("GET /plans/change-request/preview con rol distinto a COMMERCIAL se deniega")
    void previewPlanChange_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/plans/change-request/preview")
                        .param("targetPlanCode", "STANDARD")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(planChangeRequestService, never()).previewPlanChange(any(), any(), any());
    }

    @Test
    @DisplayName("GET /plans/change-request/preview con COMMERCIAL autenticado responde 200")
    void previewPlanChange_asCommercial_respondsOk() throws Exception {
        var expected = new PlanChangePreviewResponseDTO();
        when(planChangeRequestService.previewPlanChange(9L, PlanCode.STANDARD, null)).thenReturn(expected);

        int status = mockMvc.perform(get("/plans/change-request/preview")
                        .param("targetPlanCode", "STANDARD")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- POST /plans/change-request ----

    @Test
    @DisplayName("POST /plans/change-request sin token se deniega y no llega al service")
    void requestPlanChange_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/plans/change-request")
                        .contentType("application/json")
                        .content("{\"targetPlanCode\":\"STANDARD\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(planChangeRequestService, never()).requestPlanChange(any(), any(), any());
    }

    @Test
    @DisplayName("POST /plans/change-request con rol distinto a COMMERCIAL se deniega")
    void requestPlanChange_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/plans/change-request")
                        .contentType("application/json")
                        .content("{\"targetPlanCode\":\"STANDARD\"}")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(planChangeRequestService, never()).requestPlanChange(any(), any(), any());
    }

    @Test
    @DisplayName("POST /plans/change-request con COMMERCIAL autenticado responde 200")
    void requestPlanChange_asCommercial_respondsOk() throws Exception {
        when(planChangeRequestService.requestPlanChange(9L, PlanCode.STANDARD, null))
                .thenReturn(samplePlanChangeRequest());

        int status = mockMvc.perform(post("/plans/change-request")
                        .contentType("application/json")
                        .content("{\"targetPlanCode\":\"STANDARD\"}")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- GET /plans/change-request/current ----

    @Test
    @DisplayName("GET /plans/change-request/current sin token se deniega y no llega al service")
    void getCurrent_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/plans/change-request/current"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(planChangeRequestService, never()).getCurrent(any());
    }

    @Test
    @DisplayName("GET /plans/change-request/current con COMMERCIAL autenticado responde 200")
    void getCurrent_asCommercial_respondsOk() throws Exception {
        when(planChangeRequestService.getCurrent(9L)).thenReturn(samplePlanChangeRequest());

        int status = mockMvc.perform(get("/plans/change-request/current")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- POST /plans/change-request/{id}/cancel ----

    @Test
    @DisplayName("POST /plans/change-request/{id}/cancel sin token se deniega y no llega al service")
    void cancel_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/plans/change-request/5/cancel"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(planChangeRequestService, never()).cancelPlanChangeRequest(any(), any());
    }

    @Test
    @DisplayName("POST /plans/change-request/{id}/cancel con rol distinto a COMMERCIAL se deniega")
    void cancel_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/plans/change-request/5/cancel")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(planChangeRequestService, never()).cancelPlanChangeRequest(any(), any());
    }

    @Test
    @DisplayName("POST /plans/change-request/{id}/cancel con COMMERCIAL autenticado responde 200")
    void cancel_asCommercial_respondsOk() throws Exception {
        when(planChangeRequestService.cancelPlanChangeRequest(9L, 5L)).thenReturn(samplePlanChangeRequest());

        int status = mockMvc.perform(post("/plans/change-request/5/cancel")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- POST /plans/change-request/contract/{contractId}/approve ----

    @Test
    @DisplayName("POST /plans/change-request/contract/{id}/approve sin token se deniega y no llega al service")
    void approveContract_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/plans/change-request/contract/7/approve"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(contractService, never()).businessApproveContract(any(), any());
    }

    @Test
    @DisplayName("POST /plans/change-request/contract/{id}/approve con rol distinto a COMMERCIAL se deniega")
    void approveContract_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/plans/change-request/contract/7/approve")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(contractService, never()).businessApproveContract(any(), any());
    }

    @Test
    @DisplayName("POST /plans/change-request/contract/{id}/approve con COMMERCIAL autenticado responde 200")
    void approveContract_asCommercial_respondsOk() throws Exception {
        var expected = new ContractSummaryResponseDTO(
                7L, 1, ContractStatus.PENDING_VERYGANA_REVIEW, ZonedDateTime.now(), ZonedDateTime.now(),
                null, null, null, null, null, List.of());
        when(contractService.businessApproveContract(7L, 9L)).thenReturn(expected);

        int status = mockMvc.perform(post("/plans/change-request/contract/7/approve")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- POST /plans/change-request/{id}/top-up-checkout ----

    @Test
    @DisplayName("POST /plans/change-request/{id}/top-up-checkout sin token se deniega y no llega al service")
    void generateTopUpCheckout_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/plans/change-request/5/top-up-checkout"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(commercialDetailsService, never()).getCommercialById(anyLong());
        verify(planService, never()).generatePlanChangeTopUpCheckout(any(), any());
    }

    @Test
    @DisplayName("POST /plans/change-request/{id}/top-up-checkout con rol distinto a COMMERCIAL se deniega")
    void generateTopUpCheckout_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/plans/change-request/5/top-up-checkout")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(planService, never()).generatePlanChangeTopUpCheckout(any(), any());
    }

    @Test
    @DisplayName("POST /plans/change-request/{id}/top-up-checkout con COMMERCIAL autenticado responde 200")
    void generateTopUpCheckout_asCommercial_respondsOk() throws Exception {
        CommercialDetails commercial = new CommercialDetails();
        when(commercialDetailsService.getCommercialById(9L)).thenReturn(commercial);
        var expected = WompiCheckoutResponseDTO.builder()
                .checkoutUrl("https://checkout.wompi.co/p/xyz")
                .reference("VG-COP-abc")
                .amountInCents(100000L)
                .build();
        when(planService.generatePlanChangeTopUpCheckout(5L, commercial)).thenReturn(expected);

        int status = mockMvc.perform(post("/plans/change-request/5/top-up-checkout")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }
}
