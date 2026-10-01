package com.verygana2.controllers.pqrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.pqrs.responses.PqrsAssetResponseDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetUploadPermissionDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.pqrs.PqrsAssetService;
import com.verygana2.services.interfaces.pqrs.PqrsService;

/**
 * {@code PqrsController} solo exige {@code isAuthenticated()} a nivel de
 * clase — sin restricción de rol. Por eso no hay caso "rol incorrecto": cada
 * endpoint solo se prueba sin token (denegado) vs. con un token de cualquier
 * rol autenticado (aquí CONSUMER) que debe pasar. Mismo patrón MockMvc +
 * Spring Security real que {@code PurchaseControllerSecurityIntegrationTest}.
 */
@WebMvcTest(PqrsController.class)
@Import({ SecurityConfig.class, PqrsControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("PqrsController — autorización (integración MockMvc + Spring Security real)")
class PqrsControllerSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private PqrsService pqrsService;
    @MockitoBean private PqrsAssetService pqrsAssetService;
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

    private String consumerToken(Long userId) {
        return tokenWithRole(userId, "ROLE_CONSUMER");
    }

    // ---- POST /pqrs/assets/prepare-upload ----

    private static final String PREPARE_UPLOAD_BODY =
            "{\"originalFileName\":\"evidencia.png\",\"contentType\":\"image/png\",\"sizeBytes\":1024}";

    @Test
    @DisplayName("POST /pqrs/assets/prepare-upload sin token se deniega y no llega al service")
    void prepareAssetUpload_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/pqrs/assets/prepare-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PREPARE_UPLOAD_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(pqrsAssetService, never()).prepareUpload(any(), any());
    }

    @Test
    @DisplayName("POST /pqrs/assets/prepare-upload con usuario autenticado responde 200")
    void prepareAssetUpload_authenticated_respondsOk() throws Exception {
        PqrsAssetUploadPermissionDTO expected = new PqrsAssetUploadPermissionDTO(
                1L, new FileUploadPermissionDTO("https://upload-url", 900L));
        when(pqrsAssetService.prepareUpload(any(), any())).thenReturn(expected);

        int status = mockMvc.perform(post("/pqrs/assets/prepare-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PREPARE_UPLOAD_BODY)
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(pqrsAssetService).prepareUpload(org.mockito.ArgumentMatchers.eq(9L), any());
    }

    // ---- POST /pqrs/assets/{id}/confirm ----

    @Test
    @DisplayName("POST /pqrs/assets/{id}/confirm sin token se deniega y no llega al service")
    void confirmAssetUpload_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/pqrs/assets/1/confirm"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(pqrsAssetService, never()).confirmUpload(any(), any());
    }

    @Test
    @DisplayName("POST /pqrs/assets/{id}/confirm con usuario autenticado responde 200")
    void confirmAssetUpload_authenticated_respondsOk() throws Exception {
        when(pqrsAssetService.confirmUpload(any(), any())).thenReturn(new PqrsAssetResponseDTO());

        int status = mockMvc.perform(post("/pqrs/assets/1/confirm")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(pqrsAssetService).confirmUpload(9L, 1L);
    }

    // ---- GET /pqrs/assets/{id}/view ----
    // La autorización fina dueño-vs-admin vive en el service (mockeado); aquí
    // solo se verifica el límite sin-token/con-token.

    @Test
    @DisplayName("GET /pqrs/assets/{id}/view sin token se deniega y no llega al service")
    void streamAsset_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/pqrs/assets/1/view"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(pqrsAssetService, never()).streamAsset(anyLong(), anyLong(), anyBoolean(), any());
    }

    @Test
    @DisplayName("GET /pqrs/assets/{id}/view con usuario autenticado responde 200")
    void streamAsset_authenticated_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/pqrs/assets/1/view")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(pqrsAssetService).streamAsset(anyLong(), anyLong(), anyBoolean(), any());
    }

    // ---- POST /pqrs ----

    private static final String CREATE_PQRS_BODY =
            "{\"type\":\"QUEJA\",\"subject\":\"Asunto\",\"description\":\"Descripción del problema\"}";

    @Test
    @DisplayName("POST /pqrs sin token se deniega y no llega al service")
    void createPqrs_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(post("/pqrs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_PQRS_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(pqrsService, never()).createPqrs(any(), any());
    }

    @Test
    @DisplayName("POST /pqrs con usuario autenticado responde 201")
    void createPqrs_authenticated_respondsCreated() throws Exception {
        when(pqrsService.createPqrs(any(), any())).thenReturn(new PqrsResponseDTO());

        int status = mockMvc.perform(post("/pqrs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_PQRS_BODY)
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(201);
        verify(pqrsService).createPqrs(any(), org.mockito.ArgumentMatchers.eq(9L));
    }

    // ---- GET /pqrs/mine ----

    @Test
    @DisplayName("GET /pqrs/mine sin token se deniega y no llega al service")
    void getMyPqrs_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/pqrs/mine"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(pqrsService, never()).getMyPqrs(any(), any());
    }

    @Test
    @DisplayName("GET /pqrs/mine con usuario autenticado responde 200")
    void getMyPqrs_authenticated_respondsOk() throws Exception {
        when(pqrsService.getMyPqrs(any(), any())).thenReturn(PagedResponse.<PqrsResponseDTO>builder().build());

        int status = mockMvc.perform(get("/pqrs/mine")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(pqrsService).getMyPqrs(org.mockito.ArgumentMatchers.eq(9L), any());
    }

    // ---- GET /pqrs/{id} ----

    @Test
    @DisplayName("GET /pqrs/{id} sin token se deniega y no llega al service")
    void getMyPqrsDetail_withoutToken_isDenied() throws Exception {
        int status = mockMvc.perform(get("/pqrs/1"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        verify(pqrsService, never()).getMyPqrsDetail(any(), any());
    }

    @Test
    @DisplayName("GET /pqrs/{id} con usuario autenticado responde 200")
    void getMyPqrsDetail_authenticated_respondsOk() throws Exception {
        when(pqrsService.getMyPqrsDetail(any(), any())).thenReturn(new PqrsResponseDTO());

        int status = mockMvc.perform(get("/pqrs/1")
                        .header("Authorization", "Bearer " + consumerToken(9L)))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        verify(pqrsService).getMyPqrsDetail(1L, 9L);
    }
}
