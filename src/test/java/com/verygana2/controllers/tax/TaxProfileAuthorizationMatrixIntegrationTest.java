package com.verygana2.controllers.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.verygana2.controllers.commercial.CommercialExpedienteController;
import com.verygana2.controllers.compliance.ComplianceExpedienteController;
import com.verygana2.controllers.declarations.DeclarationController;
import com.verygana2.services.interfaces.declarations.DeclarationService;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.commercial.CommercialExpedienteDocumentService;
import com.verygana2.services.interfaces.tax.CommercialEstablishmentService;
import com.verygana2.services.interfaces.tax.TaxCatalogService;
import com.verygana2.services.interfaces.tax.TaxProfileService;

/**
 * Ficha tributaria y expediente (4.7 / 4.8): el empresario declara y consulta lo suyo;
 * solo COMPLIANCE_OFFICER revisa documentos y secciones y administra el catálogo. Un
 * empresario nunca revisa su propia información. Cadena de seguridad real con JWT firmados.
 */
@WebMvcTest({ CommercialTaxProfileController.class, ComplianceTaxProfileController.class,
        CommercialExpedienteController.class, ComplianceExpedienteController.class, DeclarationController.class })
@Import({ SecurityConfig.class, TaxProfileAuthorizationMatrixIntegrationTest.TestKeysConfig.class })
@DisplayName("Ficha tributaria y expediente — matriz de autorización")
class TaxProfileAuthorizationMatrixIntegrationTest {

    private static final String SUBMIT_BODY = """
            {"activities":[{"ciiuCode":"4711","main":true}],"invoicingObligated":true,"withholdingAgent":false,
             "incomeReports":[{"concept":"VENTAS","periodStart":"2026-01-01","periodEnd":"2026-01-31",
               "amountCents":100,"currency":"COP","scope":"EXTERNAL_ONLY"}],
             "currentCapacity":"50 pedidos diarios","hasAccountingAdvisor":false}
            """;
    private static final String DOC_REVIEW_BODY = "{\"result\":\"OBSERVED\",\"notes\":\"Ilegible\"}";
    private static final String SECTION_REVIEW_BODY = "{\"result\":\"VERIFIED\"}";
    private static final String CATALOG_BODY = """
            {"catalogType":"REGIME","code":"ORD","label":"Ordinario","legalBasis":"Estatuto Tributario",
             "validFrom":"2026-01-01"}
            """;
    private static final UUID COMMERCIAL = new UUID(0L, 7L);

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private TaxProfileService taxProfileService;
    @MockitoBean private TaxCatalogService catalogService;
    @MockitoBean private CommercialEstablishmentService establishmentService;
    @MockitoBean private CommercialExpedienteDocumentService expedienteService;
    @MockitoBean private DeclarationService declarationService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private UserIdResolver userIdResolver;
    @MockitoBean private FeatureFlagService featureFlagService;

    @BeforeEach
    void stubUserIdResolver() {
        when(userIdResolver.toInternalId(any(UUID.class)))
                .thenAnswer(inv -> inv.<UUID>getArgument(0).getLeastSignificantBits());
        when(catalogService.listActive()).thenReturn(List.of());
        when(catalogService.listAll()).thenReturn(List.of());
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

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType("application/json").content(body);
    }

    // ---- Ficha: la declara el empresario ----

    @Test
    @DisplayName("GET /commercials/tax-profile sin token se deniega")
    void getProfile_withoutToken_isDenied() throws Exception {
        assertThat(status(get("/commercials/tax-profile"), null)).isIn(401, 403);
    }

    @Test
    @DisplayName("GET /commercials/tax-profile con COMMERCIAL responde 204 si todavía no la envió")
    void getProfile_asCommercial_noContent() throws Exception {
        assertThat(status(get("/commercials/tax-profile"), "ROLE_COMMERCIAL")).isEqualTo(204);
    }

    @Test
    @DisplayName("PUT /commercials/tax-profile con COMMERCIAL llega al service")
    void submit_asCommercial_reachesService() throws Exception {
        assertThat(status(json(put("/commercials/tax-profile"), SUBMIT_BODY), "ROLE_COMMERCIAL")).isEqualTo(200);
        verify(taxProfileService).submit(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("PUT /commercials/tax-profile con CONSUMER o COMPLIANCE se deniega: nadie declara por el empresario")
    void submit_asOthers_isForbidden() throws Exception {
        assertThat(status(json(put("/commercials/tax-profile"), SUBMIT_BODY), "ROLE_CONSUMER")).isEqualTo(403);
        assertThat(status(json(put("/commercials/tax-profile"), SUBMIT_BODY), "ROLE_COMPLIANCE_OFFICER")).isEqualTo(403);
        verify(taxProfileService, never()).submit(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /commercials/documents con COMPLIANCE se deniega: usa su propio endpoint por publicId")
    void ownDocuments_asCompliance_isForbidden() throws Exception {
        assertThat(status(get("/commercials/documents"), "ROLE_COMPLIANCE_OFFICER")).isEqualTo(403);
        verify(expedienteService, never()).listForCommercial(anyLong());
    }

    // ---- Revisión: solo compliance ----

    @Test
    @DisplayName("POST /compliance/commercial-documents/{id}/review con COMMERCIAL se deniega: no revisa sus propios documentos")
    void reviewDocument_asCommercial_isForbidden() throws Exception {
        assertThat(status(json(post("/compliance/commercial-documents/1/review"), DOC_REVIEW_BODY), "ROLE_COMMERCIAL"))
                .isEqualTo(403);
        verify(expedienteService, never()).review(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("POST /compliance/commercial-documents/{id}/review con ADMIN se deniega")
    void reviewDocument_asAdmin_isForbidden() throws Exception {
        assertThat(status(json(post("/compliance/commercial-documents/1/review"), DOC_REVIEW_BODY), "ROLE_ADMIN"))
                .isEqualTo(403);
    }

    @Test
    @DisplayName("POST /compliance/commercial-documents/{id}/review con COMPLIANCE llega al service")
    void reviewDocument_asCompliance_reachesService() throws Exception {
        assertThat(status(json(post("/compliance/commercial-documents/1/review"), DOC_REVIEW_BODY),
                "ROLE_COMPLIANCE_OFFICER")).isEqualTo(200);
        verify(expedienteService).review(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("POST /compliance/tax-profiles/{id}/sections/{s}/review con COMMERCIAL se deniega")
    void reviewSection_asCommercial_isForbidden() throws Exception {
        assertThat(status(json(post("/compliance/tax-profiles/" + COMMERCIAL + "/sections/A_RUT/review"),
                SECTION_REVIEW_BODY), "ROLE_COMMERCIAL")).isEqualTo(403);
        verify(taxProfileService, never()).reviewSection(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("POST /compliance/tax-profiles/{id}/sections/{s}/review con COMPLIANCE llega al service")
    void reviewSection_asCompliance_reachesService() throws Exception {
        assertThat(status(json(post("/compliance/tax-profiles/" + COMMERCIAL + "/sections/A_RUT/review"),
                SECTION_REVIEW_BODY), "ROLE_COMPLIANCE_OFFICER")).isEqualTo(200);
        verify(taxProfileService).reviewSection(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("GET /compliance/tax-profiles con CONSUMER se deniega: expone datos de otros empresarios")
    void listProfiles_asConsumer_isForbidden() throws Exception {
        assertThat(status(get("/compliance/tax-profiles"), "ROLE_CONSUMER")).isEqualTo(403);
    }

    // ---- Catálogo: lo administra compliance, el empresario solo lo consulta ----

    @Test
    @DisplayName("POST /compliance/tax-catalog con COMMERCIAL se deniega")
    void createCatalog_asCommercial_isForbidden() throws Exception {
        assertThat(status(json(post("/compliance/tax-catalog"), CATALOG_BODY), "ROLE_COMMERCIAL")).isEqualTo(403);
        verify(catalogService, never()).create(any(), anyLong());
    }

    @Test
    @DisplayName("POST /compliance/tax-catalog con COMPLIANCE llega al service")
    void createCatalog_asCompliance_reachesService() throws Exception {
        assertThat(status(json(post("/compliance/tax-catalog"), CATALOG_BODY), "ROLE_COMPLIANCE_OFFICER")).isEqualTo(200);
        verify(catalogService).create(any(), anyLong());
    }

    @Test
    @DisplayName("GET /commercials/tax-profile/catalog con COMMERCIAL responde 200")
    void activeCatalog_asCommercial_respondsOk() throws Exception {
        assertThat(status(get("/commercials/tax-profile/catalog"), "ROLE_COMMERCIAL")).isEqualTo(200);
    }

    // ---- Declaración de compromiso: el empresario consulta la suya; compliance ve el historial ----

    @Test
    @DisplayName("GET /commercials/declarations/{code} con COMMERCIAL llega al service")
    void declaration_asCommercial_reachesService() throws Exception {
        assertThat(status(get("/commercials/declarations/FORMALIZATION_COMMITMENT"), "ROLE_COMMERCIAL"))
                .isEqualTo(200);
        verify(declarationService).viewForCommercial(anyLong(), any(), any());
    }

    @Test
    @DisplayName("GET /commercials/declarations/{code} con CONSUMER se deniega")
    void declaration_asConsumer_isForbidden() throws Exception {
        assertThat(status(get("/commercials/declarations/FORMALIZATION_COMMITMENT"), "ROLE_CONSUMER"))
                .isEqualTo(403);
        verify(declarationService, never()).viewForCommercial(anyLong(), any(), any());
    }

    @Test
    @DisplayName("GET /compliance/commercials/{id}/declarations/{code} con COMMERCIAL se deniega")
    void declarationHistory_asCommercial_isForbidden() throws Exception {
        assertThat(status(get("/compliance/commercials/" + COMMERCIAL + "/declarations/FORMALIZATION_COMMITMENT"),
                "ROLE_COMMERCIAL")).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /compliance/commercials/{id}/declarations/{code} con COMPLIANCE responde 200")
    void declarationHistory_asCompliance_respondsOk() throws Exception {
        assertThat(status(get("/compliance/commercials/" + COMMERCIAL + "/declarations/FORMALIZATION_COMMITMENT"),
                "ROLE_COMPLIANCE_OFFICER")).isEqualTo(200);
    }
}
