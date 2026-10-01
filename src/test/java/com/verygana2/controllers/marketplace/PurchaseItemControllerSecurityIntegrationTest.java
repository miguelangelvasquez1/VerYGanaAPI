package com.verygana2.controllers.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.finance.CashRefundService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;
import com.verygana2.services.interfaces.pqrs.PqrsService;

/**
 * {@code PurchaseItemController} mezcla endpoints COMMERCIAL (métricas de
 * ventas, reclamar entrega física, {@code GET /pending-claims} — endpoint
 * nuevo) y CONSUMER (código de entrega, reportar incidencia, datos
 * bancarios de reembolso en efectivo).
 */
@WebMvcTest(PurchaseItemController.class)
@Import({ SecurityConfig.class, PurchaseItemControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("PurchaseItemController — autorización (integración MockMvc + Spring Security real)")
class PurchaseItemControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private PurchaseItemService purchaseItemService;
    @MockitoBean private PqrsService pqrsService;
    @MockitoBean private CashRefundService cashRefundService;
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

    // ---- GET /purchaseItems/totalSales (COMMERCIAL) ----

    @Test
    @DisplayName("GET /purchaseItems/totalSales sin token se deniega y no llega al service")
    void getTotalCommercialSales_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/purchaseItems/totalSales"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(purchaseItemService, never()).getTotalSalesbyCommercial(anyLong());
    }

    @Test
    @DisplayName("GET /purchaseItems/totalSales con rol distinto a COMMERCIAL se deniega")
    void getTotalCommercialSales_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/purchaseItems/totalSales")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(purchaseItemService, never()).getTotalSalesbyCommercial(anyLong());
    }

    @Test
    @DisplayName("GET /purchaseItems/totalSales con COMMERCIAL autenticado responde 200")
    void getTotalCommercialSales_asCommercial_respondsOk() throws Exception {
        when(purchaseItemService.getTotalSalesbyCommercial(9L)).thenReturn(42L);

        int status = mockMvc.perform(get("/purchaseItems/totalSales")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- GET /purchaseItems/topSelling (COMMERCIAL) ----

    @Test
    @DisplayName("GET /purchaseItems/topSelling con COMMERCIAL autenticado responde 200")
    void getTopSellingProductsPage_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/purchaseItems/topSelling")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- GET /purchaseItems/{id}/delivered-code (CONSUMER) ----

    @Test
    @DisplayName("GET /purchaseItems/{id}/delivered-code sin token se deniega y no llega al service")
    void getDeliveredCode_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/purchaseItems/5/delivered-code"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(purchaseItemService, never()).getDeliveredCode(anyLong(), anyLong());
    }

    @Test
    @DisplayName("GET /purchaseItems/{id}/delivered-code con rol distinto a CONSUMER se deniega")
    void getDeliveredCode_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/purchaseItems/5/delivered-code")
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(purchaseItemService, never()).getDeliveredCode(anyLong(), anyLong());
    }

    @Test
    @DisplayName("GET /purchaseItems/{id}/delivered-code con CONSUMER autenticado responde 200")
    void getDeliveredCode_asConsumer_respondsOk() throws Exception {
        when(purchaseItemService.getDeliveredCode(5L, 9L)).thenReturn("ABC123");

        int status = mockMvc.perform(get("/purchaseItems/5/delivered-code")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- POST /purchaseItems/{id}/claim (COMMERCIAL) ----

    private static final String CLAIM_BODY = "{\"pin\":\"1234\"}";

    @Test
    @DisplayName("POST /purchaseItems/{id}/claim sin token se deniega y no llega al service")
    void claimPhysicalItem_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/purchaseItems/5/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLAIM_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(purchaseItemService, never()).claimPhysicalItem(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("POST /purchaseItems/{id}/claim con rol distinto a COMMERCIAL se deniega")
    void claimPhysicalItem_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/purchaseItems/5/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLAIM_BODY)
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(purchaseItemService, never()).claimPhysicalItem(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("POST /purchaseItems/{id}/claim con COMMERCIAL autenticado responde 204")
    void claimPhysicalItem_asCommercial_respondsNoContent() throws Exception {
        int status = mockMvc.perform(post("/purchaseItems/5/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLAIM_BODY)
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(204);
        verify(purchaseItemService).claimPhysicalItem(5L, 9L, "1234");
    }

    // ---- GET /purchaseItems/pending-claims (COMMERCIAL, endpoint nuevo) ----

    @Test
    @DisplayName("GET /purchaseItems/pending-claims sin token se deniega y no llega al service")
    void getPendingClaims_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/purchaseItems/pending-claims"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(purchaseItemService, never())
                .getPendingClaims(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /purchaseItems/pending-claims con rol distinto a COMMERCIAL se deniega")
    void getPendingClaims_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/purchaseItems/pending-claims")
                        .header("Authorization", "Bearer " + consumerToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(purchaseItemService, never())
                .getPendingClaims(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /purchaseItems/pending-claims sin query params, con COMMERCIAL, responde 200")
    void getPendingClaims_withoutQueryParams_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/purchaseItems/pending-claims")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(purchaseItemService)
                .getPendingClaims(eq(9L), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /purchaseItems/pending-claims con documentType/documentNumber, con COMMERCIAL, responde 200")
    void getPendingClaims_withQueryParams_asCommercial_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/purchaseItems/pending-claims")
                        .param("documentType", "CC")
                        .param("documentNumber", "123456789")
                        .header("Authorization", "Bearer " + commercialToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(purchaseItemService)
                .getPendingClaims(eq(9L), eq(DocumentType.CC), eq("123456789"), any(Pageable.class));
    }

    // ---- POST /purchaseItems/{id}/report (CONSUMER) ----

    private static final String REPORT_BODY =
            "{\"reason\":\"NOT_DELIVERED\",\"description\":\"Nunca llego el producto\"}";

    @Test
    @DisplayName("POST /purchaseItems/{id}/report sin token se deniega y no llega al service")
    void reportIssue_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/purchaseItems/5/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(purchaseItemService, never()).getReportableItem(anyLong(), anyLong());
    }

    @Test
    @DisplayName("POST /purchaseItems/{id}/report con rol distinto a CONSUMER se deniega")
    void reportIssue_withWrongRole_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/purchaseItems/5/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT_BODY)
                        .header("Authorization", "Bearer " + commercialToken(1L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(purchaseItemService, never()).getReportableItem(anyLong(), anyLong());
    }

    @Test
    @DisplayName("POST /purchaseItems/{id}/report con CONSUMER autenticado responde 201")
    void reportIssue_asConsumer_respondsCreated() throws Exception {
        PurchaseItem item = new PurchaseItem();
        when(purchaseItemService.getReportableItem(5L, 9L)).thenReturn(item);

        int status = mockMvc.perform(post("/purchaseItems/5/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT_BODY)
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(201);
    }

    // ---- POST /purchaseItems/{id}/cash-refund/bank-details (CONSUMER) ----

    private static final String BANK_DETAILS_BODY = "{"
            + "\"accountHolderName\":\"Juan Perez\","
            + "\"accountHolderDoc\":\"123456789\","
            + "\"accountHolderDocType\":\"CC\","
            + "\"bankName\":\"Bancolombia\","
            + "\"accountNumber\":\"1234567890\","
            + "\"accountType\":\"SAVINGS\""
            + "}";

    @Test
    @DisplayName("POST /purchaseItems/{id}/cash-refund/bank-details sin token se deniega y no llega al service")
    void submitCashRefundBankDetails_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/purchaseItems/5/cash-refund/bank-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BANK_DETAILS_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(cashRefundService, never()).submitBankDetails(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("POST /purchaseItems/{id}/cash-refund/bank-details con CONSUMER autenticado responde 204")
    void submitCashRefundBankDetails_asConsumer_respondsNoContent() throws Exception {
        int status = mockMvc.perform(post("/purchaseItems/5/cash-refund/bank-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BANK_DETAILS_BODY)
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(204);
        verify(cashRefundService).submitBankDetails(eq(5L), eq(9L), any());
    }
}
