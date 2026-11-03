package com.verygana2.controllers.inforequests;

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
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.inforequests.ComplianceAlertService;
import com.verygana2.services.interfaces.inforequests.ComplianceCaseService;
import com.verygana2.services.interfaces.inforequests.InformationRequestService;

/**
 * Requerimientos, hallazgos y alertas (4.8): el empresario ve y responde los suyos; solo
 * COMPLIANCE_OFFICER emite, revisa y decide medidas. Un hallazgo puede suspender una cuenta,
 * así que ni ADMIN ni el empresario pueden registrarlo. Cadena de seguridad real con JWT.
 */
@WebMvcTest({ CommercialInformationRequestController.class, ComplianceInformationRequestController.class })
@Import({ SecurityConfig.class, InformationRequestAuthorizationMatrixIntegrationTest.TestKeysConfig.class })
@DisplayName("Requerimientos y hallazgos — matriz de autorización")
class InformationRequestAuthorizationMatrixIntegrationTest {

    private static final UUID REQUEST = UUID.randomUUID();
    private static final UUID COMMERCIAL = new UUID(0L, 7L);
    private static final String FINDING_BODY = """
            {"commercialPublicId":"%s","factObserved":"x","classification":"VERIFIED_FALSE_INFO","severity":"HIGH",
             "impact":"x","remediable":false,"contractualBasis":"4.8","measure":"SUSPENSION"}
            """.formatted(COMMERCIAL);
    private static final String CREATE_BODY = """
            {"commercialPublicId":"%s","trigger":"NEW_REQUIREMENT","reason":"x","relationToOperation":"x",
             "items":[{"itemType":"CLARIFICATION","description":"x"}]}
            """.formatted(COMMERCIAL);
    private static final String RULE_BODY = """
            {"code":"SALES_GROWTH","ruleType":"PLATFORM_SALES_GROWTH","description":"x","legalBasis":"x",
             "windowDays":30,"minGrowthPct":50,"minCurrentSalesCents":0}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private InformationRequestService requestService;
    @MockitoBean private ComplianceCaseService caseService;
    @MockitoBean private ComplianceAlertService alertService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private UserIdResolver userIdResolver;
    @MockitoBean private FeatureFlagService featureFlagService;

    @BeforeEach
    void stubUserIdResolver() {
        when(userIdResolver.toInternalId(any(UUID.class)))
                .thenAnswer(inv -> inv.<UUID>getArgument(0).getLeastSignificantBits());
        when(requestService.listForCommercial(anyLong())).thenReturn(List.of());
    }

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

    private String token(String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("VerYGanaAPI")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("user@test.com")
                .audience(List.of("verygana-frontend"))
                .claim("type", "access")
                .claim("scope", role)
                .claim("publicId", new UUID(0L, 1L).toString())
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private int status(MockHttpServletRequestBuilder request, String role) throws Exception {
        if (role != null) {
            request.header("Authorization", token(role));
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType("application/json").content(body);
    }

    // ---- Empresario ----

    @Test
    @DisplayName("GET /commercials/information-requests con COMMERCIAL responde 200")
    void list_asCommercial_ok() throws Exception {
        assertThat(status(get("/commercials/information-requests"), "ROLE_COMMERCIAL")).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /commercials/information-requests sin token se deniega")
    void list_withoutToken_isDenied() throws Exception {
        assertThat(status(get("/commercials/information-requests"), null)).isIn(401, 403);
    }

    @Test
    @DisplayName("POST /commercials/information-requests/{id}/responses con CONSUMER se deniega")
    void respond_asConsumer_isForbidden() throws Exception {
        assertThat(status(json(post("/commercials/information-requests/" + REQUEST + "/responses"),
                "{\"message\":\"x\"}"), "ROLE_CONSUMER")).isEqualTo(403);
        verify(requestService, never()).respond(anyLong(), any(), any());
    }

    @Test
    @DisplayName("POST /commercials/information-requests/{id}/responses con COMMERCIAL llega al service")
    void respond_asCommercial_reachesService() throws Exception {
        assertThat(status(json(post("/commercials/information-requests/" + REQUEST + "/responses"),
                "{\"message\":\"x\"}"), "ROLE_COMMERCIAL")).isEqualTo(200);
        verify(requestService).respond(anyLong(), any(), any());
    }

    // ---- Compliance ----

    @Test
    @DisplayName("POST /compliance/information-requests con COMMERCIAL se deniega")
    void create_asCommercial_isForbidden() throws Exception {
        assertThat(status(json(post("/compliance/information-requests"), CREATE_BODY), "ROLE_COMMERCIAL")).isEqualTo(403);
        verify(requestService, never()).create(any(), anyLong());
    }

    @Test
    @DisplayName("POST /compliance/information-requests con COMPLIANCE llega al service")
    void create_asCompliance_reachesService() throws Exception {
        assertThat(status(json(post("/compliance/information-requests"), CREATE_BODY), "ROLE_COMPLIANCE_OFFICER"))
                .isEqualTo(200);
        verify(requestService).create(any(), anyLong());
    }

    @Test
    @DisplayName("POST /compliance/findings con COMMERCIAL se deniega: nadie decide medidas sobre sí mismo")
    void finding_asCommercial_isForbidden() throws Exception {
        assertThat(status(json(post("/compliance/findings"), FINDING_BODY), "ROLE_COMMERCIAL")).isEqualTo(403);
        verify(caseService, never()).recordFinding(any(), anyLong());
    }

    @Test
    @DisplayName("POST /compliance/findings con ADMIN se deniega: las medidas son de compliance")
    void finding_asAdmin_isForbidden() throws Exception {
        assertThat(status(json(post("/compliance/findings"), FINDING_BODY), "ROLE_ADMIN")).isEqualTo(403);
        verify(caseService, never()).recordFinding(any(), anyLong());
    }

    @Test
    @DisplayName("POST /compliance/findings con COMPLIANCE llega al service")
    void finding_asCompliance_reachesService() throws Exception {
        assertThat(status(json(post("/compliance/findings"), FINDING_BODY), "ROLE_COMPLIANCE_OFFICER")).isEqualTo(200);
        verify(caseService).recordFinding(any(), anyLong());
    }

    @Test
    @DisplayName("POST /compliance/alert-rules con COMMERCIAL se deniega")
    void rule_asCommercial_isForbidden() throws Exception {
        assertThat(status(json(post("/compliance/alert-rules"), RULE_BODY), "ROLE_COMMERCIAL")).isEqualTo(403);
        verify(alertService, never()).createRule(any(), anyLong());
    }

    @Test
    @DisplayName("GET /compliance/alerts con CONSUMER se deniega")
    void alerts_asConsumer_isForbidden() throws Exception {
        assertThat(status(get("/compliance/alerts"), "ROLE_CONSUMER")).isEqualTo(403);
    }
}
