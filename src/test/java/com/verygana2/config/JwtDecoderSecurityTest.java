package com.verygana2.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Pruebas de seguridad sobre el {@code jwtDecoder} REAL de {@link SecurityConfig}.
 * Verifica que solo se acepten tokens con issuer, audience, expiración y firma
 * correctos. Un fallo aquí = tokens forjados/expirados/de otra app podrían
 * autenticar. Usa un par de llaves RSA generado en el test (no toca config).
 */
@DisplayName("SecurityConfig.jwtDecoder — aceptación de tokens (seguridad)")
class JwtDecoderSecurityTest {

    private static final String VALID_ISSUER = "VerYGanaAPI";
    private static final String VALID_AUDIENCE = "verygana-frontend";

    private JwtEncoder serverEncoder;    // firma con la llave del servidor
    private JwtDecoder decoder;          // el decoder de producción
    private JwtEncoder attackerEncoder;  // firma con OTRA llave (atacante)

    @BeforeEach
    void setUp() throws Exception {
        SecurityConfig server = new SecurityConfig(generateKeys());
        this.serverEncoder = server.jwtEncoder();
        this.decoder = server.jwtDecoder();
        this.attackerEncoder = new SecurityConfig(generateKeys()).jwtEncoder();
    }

    @Test
    @DisplayName("acepta un access token válido")
    void acceptsValidToken() {
        String token = sign(serverEncoder, VALID_ISSUER, VALID_AUDIENCE,
                Instant.now(), Instant.now().plusSeconds(300));

        assertThatCode(() -> decoder.decode(token)).doesNotThrowAnyException();
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("user@test.com");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("ROLE_CONSUMER");
    }

    @Test
    @DisplayName("rechaza issuer inválido (token de otro emisor)")
    void rejectsWrongIssuer() {
        String token = sign(serverEncoder, "attacker-issuer", VALID_AUDIENCE,
                Instant.now(), Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("rechaza audience inválido (token para otra app)")
    void rejectsWrongAudience() {
        String token = sign(serverEncoder, VALID_ISSUER, "otra-app",
                Instant.now(), Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("rechaza token expirado")
    void rejectsExpiredToken() {
        String token = sign(serverEncoder, VALID_ISSUER, VALID_AUDIENCE,
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(300));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("rechaza token firmado con otra llave (firma forjada)")
    void rejectsForeignSignature() {
        String token = sign(attackerEncoder, VALID_ISSUER, VALID_AUDIENCE,
                Instant.now(), Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private String sign(JwtEncoder encoder, String issuer, String audience,
                        Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject("user@test.com")
                .audience(List.of(audience))
                .claim("type", "access")
                .claim("scope", "ROLE_CONSUMER")
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private RsaKeyProperties generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        return new RsaKeyProperties((RSAPublicKey) kp.getPublic(), (RSAPrivateKey) kp.getPrivate());
    }
}
