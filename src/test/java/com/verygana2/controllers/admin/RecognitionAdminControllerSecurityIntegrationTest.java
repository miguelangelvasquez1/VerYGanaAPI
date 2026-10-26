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
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
import com.verygana2.controllers.marketplace.RecognitionController;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.recognitions.RecognitionService;

/**
 * Reconocimientos (Contrato B 10.16): comprar, anular y elegir candidatos es solo de ADMIN;
 * ver lo recibido es solo del CONSUMER. Cadena de seguridad real con JWT firmados en el test.
 */
@WebMvcTest({ RecognitionAdminController.class, RecognitionController.class })
@Import({ SecurityConfig.class, RecognitionAdminControllerSecurityIntegrationTest.TestKeysConfig.class })
@DisplayName("Reconocimientos — autorización (integración MockMvc + Spring Security real)")
class RecognitionAdminControllerSecurityIntegrationTest {

    private static final String CREATE_BODY = """
            {"beneficiaryPublicId":"00000000-0000-0000-0000-000000000009","productId":5,"quantity":1,
             "criterion":"KEYS_EARNED","metricValue":1500,"reason":"Usuario destacado"}
            """;
    private static final String CANCEL_BODY = "{\"cause\":\"error del admin\"}";
    private static final UUID RECOGNITION = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private RecognitionService recognitionService;
    // Requerido por SecurityConfig.authenticationProvider(), no se invoca en este flujo.
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private UserIdResolver userIdResolver;
    // Requerido por el FeatureFlagInterceptor global (WebMvcConfigurer), no relevante aquí.
    @MockitoBean private FeatureFlagService featureFlagService;

    /** El token solo lleva publicId; en estos tests publicId = UUID(0, userId) y el resolver lo invierte. */
    @BeforeEach
    void stubUserIdResolver() {
        when(userIdResolver.toInternalId(any(UUID.class)))
                .thenAnswer(inv -> inv.<UUID>getArgument(0).getLeastSignificantBits());
        when(recognitionService.receivedBy(anyLong(), any())).thenReturn(new PageImpl<>(List.of()));
        when(recognitionService.candidates(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
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

    private int createAs(String role) throws Exception {
        var request = post("/admin/recognitions")
                .contentType("application/json")
                .header("Idempotency-Key", "clave-1")
                .content(CREATE_BODY);
        if (role != null) {
            request.header("Authorization", token(role));
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    // ---- POST /admin/recognitions (solo ADMIN) ----

    @Test
    @DisplayName("POST /admin/recognitions sin token se deniega y no compra nada")
    void create_withoutToken_isDenied() throws Exception {
        assertThat(createAs(null)).isIn(401, 403);
        verify(recognitionService, never()).create(any(), any(), anyString());
    }

    @Test
    @DisplayName("POST /admin/recognitions con COMMERCIAL se deniega: un empresario no se compra a sí mismo con el fondo")
    void create_asCommercial_isForbidden() throws Exception {
        assertThat(createAs("ROLE_COMMERCIAL")).isEqualTo(403);
        verify(recognitionService, never()).create(any(), any(), anyString());
    }

    @Test
    @DisplayName("POST /admin/recognitions con CONSUMER se deniega: el beneficiario no se otorga reconocimientos")
    void create_asConsumer_isForbidden() throws Exception {
        assertThat(createAs("ROLE_CONSUMER")).isEqualTo(403);
        verify(recognitionService, never()).create(any(), any(), anyString());
    }

    @Test
    @DisplayName("POST /admin/recognitions con ADMIN llega al service con la clave de origen")
    void create_asAdmin_reachesService() throws Exception {
        assertThat(createAs("ROLE_ADMIN")).isEqualTo(200);
        verify(recognitionService).create(any(), any(), org.mockito.ArgumentMatchers.eq("clave-1"));
    }

    @Test
    @DisplayName("POST /admin/recognitions sin Idempotency-Key no llega al service")
    void create_withoutIdempotencyKey_isRejected() throws Exception {
        int status = mockMvc.perform(post("/admin/recognitions")
                        .contentType("application/json")
                        .header("Authorization", token("ROLE_ADMIN"))
                        .content(CREATE_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(400);
        verify(recognitionService, never()).create(any(), any(), anyString());
    }

    // ---- POST /admin/recognitions/{id}/cancel y GET /candidates (solo ADMIN) ----

    @Test
    @DisplayName("POST /admin/recognitions/{id}/cancel con COMMERCIAL se deniega")
    void cancel_asCommercial_isForbidden() throws Exception {
        int status = mockMvc.perform(post("/admin/recognitions/" + RECOGNITION + "/cancel")
                        .contentType("application/json")
                        .header("Authorization", token("ROLE_COMMERCIAL"))
                        .content(CANCEL_BODY))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(recognitionService, never()).cancel(any(), anyString(), any());
    }

    @Test
    @DisplayName("GET /admin/recognitions/candidates con CONSUMER se deniega: el ranking expone datos de otros usuarios")
    void candidates_asConsumer_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/admin/recognitions/candidates").param("criterion", "XP")
                        .header("Authorization", token("ROLE_CONSUMER")))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(recognitionService, never()).candidates(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /admin/recognitions/candidates con ADMIN responde 200")
    void candidates_asAdmin_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/admin/recognitions/candidates").param("criterion", "XP")
                        .header("Authorization", token("ROLE_ADMIN")))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    // ---- GET /recognitions/me (solo CONSUMER) ----

    @Test
    @DisplayName("GET /recognitions/me con CONSUMER responde 200")
    void received_asConsumer_respondsOk() throws Exception {
        int status = mockMvc.perform(get("/recognitions/me").header("Authorization", token("ROLE_CONSUMER")))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /recognitions/me con COMMERCIAL se deniega")
    void received_asCommercial_isForbidden() throws Exception {
        int status = mockMvc.perform(get("/recognitions/me").header("Authorization", token("ROLE_COMMERCIAL")))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(recognitionService, never()).receivedBy(anyLong(), any());
    }
}
