package com.verygana2.controllers.finance.plans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import com.verygana2.dtos.finance.plans.responses.OpenRechargeResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.commercial.CommercialContractService;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.PlanService;

/**
 * Endpoints de recuperación de una recarga interrumpida en {@code /plans/recharge/**}
 * (consultar la recarga en curso, conciliarla con Wompi, cancelarla): exigen
 * {@code hasRole('COMMERCIAL')}. Levanta el filtro de seguridad real, mismo patrón
 * que {@code PlanChangeRequestControllerSecurityIntegrationTest}.
 */
@WebMvcTest(PlanController.class)
@Import({ SecurityConfig.class, PlanControllerRechargeSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("PlanController (recarga en curso) — autorización (integración MockMvc + Spring Security real)")
class PlanControllerRechargeSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private PlanService planService;
    @MockitoBean private CommercialDetailsService commercialDetailsService;
    @MockitoBean private CommercialContractService commercialContractService;
    // Requerido por SecurityConfig.authenticationProvider(), no se invoca en este flujo.
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private UserIdResolver userIdResolver;
    // Requerido por el FeatureFlagInterceptor global (WebMvcConfigurer), no relevante aquí.
    @MockitoBean private FeatureFlagService featureFlagService;

    private final CommercialDetails commercial = new CommercialDetails();

    /** El token solo lleva publicId; en estos tests publicId = UUID(0, userId) y el resolver lo invierte. */
    @BeforeEach
    void stubUserIdResolver() {
        when(userIdResolver.toInternalId(any(UUID.class)))
                .thenAnswer(inv -> inv.<UUID>getArgument(0).getLeastSignificantBits());
        when(commercialDetailsService.getCommercialById(9L)).thenReturn(commercial);
    }

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
                .claim("publicId", new UUID(0L, userId).toString())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String commercialToken() {
        return tokenWithRole(9L, "ROLE_COMMERCIAL");
    }

    private String consumerToken() {
        return tokenWithRole(1L, "ROLE_CONSUMER");
    }

    // ---- GET /plans/recharge/current ----

    @Test
    @DisplayName("GET /plans/recharge/current sin token se deniega y no llega al service")
    void getOpenRecharge_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/plans/recharge/current")).andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(planService, never()).getOpenRecharge(any());
    }

    @Test
    @DisplayName("GET /plans/recharge/current con rol distinto a COMMERCIAL se deniega")
    void getOpenRecharge_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/plans/recharge/current")
                        .header("Authorization", "Bearer " + consumerToken()))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(planService, never()).getOpenRecharge(any());
    }

    @Test
    @DisplayName("GET /plans/recharge/current con COMMERCIAL: 200 si hay recarga en curso, 204 si no")
    void getOpenRecharge_asCommercial_respondsOkOrNoContent() throws Exception {
        when(planService.getOpenRecharge(commercial))
                .thenReturn(Optional.of(OpenRechargeResponseDTO.builder().contractId(7L).build()))
                .thenReturn(Optional.empty());

        int withOpen = mockMvc.perform(get("/plans/recharge/current")
                        .header("Authorization", "Bearer " + commercialToken()))
                .andReturn().getResponse().getStatus();
        int withoutOpen = mockMvc.perform(get("/plans/recharge/current")
                        .header("Authorization", "Bearer " + commercialToken()))
                .andReturn().getResponse().getStatus();

        assertThat(withOpen).isEqualTo(200);
        assertThat(withoutOpen).isEqualTo(204);
        // "current" no debe caer en /recharge/{contractId}
        verify(commercialContractService, never()).getForCommercial(any(), any());
    }

    // ---- POST /plans/recharge/{contractId}/reconcile ----

    @Test
    @DisplayName("POST /plans/recharge/{id}/reconcile sin token se deniega y no llega al service")
    void reconcile_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/plans/recharge/7/reconcile")).andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(planService, never()).reconcileRecharge(any(), any());
    }

    @Test
    @DisplayName("POST /plans/recharge/{id}/reconcile con rol distinto a COMMERCIAL se deniega")
    void reconcile_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/plans/recharge/7/reconcile")
                        .header("Authorization", "Bearer " + consumerToken()))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(planService, never()).reconcileRecharge(any(), any());
    }

    @Test
    @DisplayName("POST /plans/recharge/{id}/reconcile con COMMERCIAL: 204 cuando la recarga quedó pagada")
    void reconcile_asCommercial_respondsNoContentWhenPaid() throws Exception {
        when(planService.reconcileRecharge(7L, commercial)).thenReturn(Optional.empty());

        int status = mockMvc.perform(post("/plans/recharge/7/reconcile")
                        .header("Authorization", "Bearer " + commercialToken()))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(204);
    }

    // ---- POST /plans/recharge/{contractId}/cancel ----

    @Test
    @DisplayName("POST /plans/recharge/{id}/cancel sin token se deniega y no llega al service")
    void cancel_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/plans/recharge/7/cancel")).andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(planService, never()).cancelRecharge(any(), any());
    }

    @Test
    @DisplayName("POST /plans/recharge/{id}/cancel con rol distinto a COMMERCIAL se deniega")
    void cancel_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/plans/recharge/7/cancel")
                        .header("Authorization", "Bearer " + consumerToken()))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(planService, never()).cancelRecharge(any(), any());
    }

    @Test
    @DisplayName("POST /plans/recharge/{id}/cancel con COMMERCIAL pasa por PlanService (que concilia con Wompi), no directo al contrato")
    void cancel_asCommercial_goesThroughPlanService() throws Exception {
        when(planService.cancelRecharge(7L, commercial)).thenReturn(new ContractSummaryResponseDTO());

        int status = mockMvc.perform(post("/plans/recharge/7/cancel")
                        .header("Authorization", "Bearer " + commercialToken()))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(commercialContractService, never()).cancelForCommercial(any(), any());
    }
}
