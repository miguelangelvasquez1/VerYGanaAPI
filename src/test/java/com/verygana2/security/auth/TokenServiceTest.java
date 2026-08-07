package com.verygana2.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.config.RsaKeyProperties;
import com.verygana2.config.SecurityConfig;
import com.verygana2.dtos.auth.TokenPairDTO;
import com.verygana2.exceptions.authExceptions.InvalidTokenException;
import com.verygana2.exceptions.authExceptions.TokenBlacklistedException;
import com.verygana2.models.User;
import com.verygana2.models.enums.UserState;
import com.verygana2.security.CustomUserDetails;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.auth.refreshToken.RefreshToken;
import com.verygana2.security.auth.refreshToken.RefreshTokenRepository;
import com.verygana2.security.auth.refreshToken.SecurityAuditService;

import jakarta.servlet.http.Cookie;

/**
 * Pruebas de seguridad del ciclo de vida de tokens en {@link TokenService}.
 * Usa el {@code JwtEncoder}/{@code JwtDecoder} REALES de {@link SecurityConfig}
 * (llaves RSA generadas en el test) para que la firma, el issuer y la audiencia
 * se validen de verdad; solo se mockea la persistencia.
 *
 * <p>Propiedades que blindan:
 * <ul>
 *   <li>rotación: refrescar revoca el refresh token anterior, y reusarlo falla.</li>
 *   <li>separación de tipos: un access token no sirve para refrescar.</li>
 *   <li>la BD manda: revocado o vencido se rechaza aunque el JWT siga firmado
 *       y dentro de su exp.</li>
 *   <li>tope de sesiones concurrentes y detección de reciclaje acelerado.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TokenService — ciclo de vida del refresh token (seguridad)")
class TokenServiceTest {

    private static final String ISSUER = "VerYGanaAPI";
    private static final String USERNAME = "user@test.com";
    private static final long ACCESS_TTL = 900;
    private static final long REFRESH_TTL = 604800;
    private static final int MAX_SESSIONS = 5;
    private static final int RAPID_CYCLE_WINDOW = 60;

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private CustomUserDetailsService customUserDetailsService;
    @Mock private SecurityAuditService securityAuditService;

    @Captor private ArgumentCaptor<RefreshToken> savedToken;
    @Captor private ArgumentCaptor<List<RefreshToken>> revokedTokens;

    private TokenService tokenService;
    private JwtDecoder decoder;
    private JwtEncoder serverEncoder;
    private JwtEncoder attackerEncoder;

    @BeforeEach
    void setUp() throws Exception {
        SecurityConfig server = new SecurityConfig(generateKeys());
        this.serverEncoder = server.jwtEncoder();
        this.decoder = server.jwtDecoder();
        this.attackerEncoder = new SecurityConfig(generateKeys()).jwtEncoder();

        this.tokenService = new TokenService(serverEncoder, decoder, refreshTokenRepository,
                customUserDetailsService, securityAuditService);
        ReflectionTestUtils.setField(tokenService, "issuer", ISSUER);
        ReflectionTestUtils.setField(tokenService, "accessTokenExpiration", ACCESS_TTL);
        ReflectionTestUtils.setField(tokenService, "refreshTokenExpiration", REFRESH_TTL);
        ReflectionTestUtils.setField(tokenService, "maxSessionsPerUser", MAX_SESSIONS);
        ReflectionTestUtils.setField(tokenService, "rapidCycleWindowSeconds", RAPID_CYCLE_WINDOW);

        when(customUserDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails());
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("emisión")
    class Issuing {

        @Test
        @DisplayName("emite un access token con type=access, scope y userId, y un refresh con type=refresh")
        void issuesAccessAndRefreshWithDistinctTypes() {
            TokenPairDTO pair = tokenService.generateTokenPair(authentication());

            Jwt access = decoder.decode(pair.getAccessToken());
            assertThat(access.getClaimAsString("type")).isEqualTo("access");
            assertThat(access.getSubject()).isEqualTo(USERNAME);
            assertThat(access.getClaimAsString("scope")).isEqualTo("ROLE_CONSUMER");
            assertThat(access.getClaims()).containsEntry("userId", 42L);
            assertThat(access.getId()).isNotBlank(); // jti para trazabilidad

            Jwt refresh = decoder.decode(pair.getRefreshToken());
            assertThat(refresh.getClaimAsString("type")).isEqualTo("refresh");
            assertThat(refresh.getClaimAsString("scope")).isNull(); // sin permisos dentro
        }

        @Test
        @DisplayName("persiste el refresh token con su jti y expiración")
        void persistsRefreshToken() {
            TokenPairDTO pair = tokenService.generateTokenPair(authentication());

            verify(refreshTokenRepository).save(savedToken.capture());
            RefreshToken stored = savedToken.getValue();
            assertThat(stored.getUsername()).isEqualTo(USERNAME);
            assertThat(stored.getToken()).isEqualTo(pair.getRefreshToken());
            assertThat(stored.getJti()).isEqualTo(decoder.decode(pair.getRefreshToken()).getId());
            assertThat(stored.getRevoked()).isFalse();
        }
    }

    @Nested
    @DisplayName("rotación")
    class Rotation {

        @Test
        @DisplayName("refrescar emite un par nuevo y revoca el refresh token usado")
        void refreshRotatesAndRevokesPrevious() {
            String oldRefresh = validRefreshTokenRegisteredInDb();

            TokenPairDTO pair = tokenService.refreshAccessToken(oldRefresh);

            assertThat(pair.getRefreshToken()).isNotEqualTo(oldRefresh);
            assertThat(decoder.decode(pair.getAccessToken()).getClaimAsString("type")).isEqualTo("access");

            verify(refreshTokenRepository, atLeastOnce()).save(savedToken.capture());
            assertThat(savedToken.getAllValues())
                    .filteredOn(rt -> oldRefresh.equals(rt.getToken()))
                    .singleElement()
                    .satisfies(rt -> {
                        assertThat(rt.getRevoked()).isTrue();
                        assertThat(rt.getLastUsedAt()).isNotNull();
                    });
        }

        @Test
        @DisplayName("reusar un refresh token ya revocado se rechaza (replay tras rotación)")
        void revokedRefreshTokenCannotBeReused() {
            String token = signedRefreshToken();
            RefreshToken revoked = storedRefreshToken(token);
            revoked.setRevoked(true);
            when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(revoked));

            assertThatThrownBy(() -> tokenService.refreshAccessToken(token))
                    .isInstanceOf(TokenBlacklistedException.class);
        }
    }

    @Nested
    @DisplayName("validación del refresh token")
    class Validation {

        @Test
        @DisplayName("un ACCESS token no sirve para refrescar (separación de tipos)")
        void accessTokenIsNotAcceptedAsRefresh() {
            String access = tokenService.generateAccessToken(authentication(), Instant.now());

            assertThatThrownBy(() -> tokenService.refreshAccessToken(access))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("not a refresh token");

            verify(customUserDetailsService, never()).loadUserByUsername(anyString());
        }

        @Test
        @DisplayName("un refresh token que no está en la BD se rechaza")
        void unknownRefreshTokenIsRejected() {
            String token = signedRefreshToken();
            when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tokenService.refreshAccessToken(token))
                    .isInstanceOf(TokenBlacklistedException.class);
        }

        @Test
        @DisplayName("si la BD lo tiene vencido se rechaza, aunque el JWT siga vigente y firmado")
        void expiredInDatabaseIsRejectedEvenIfJwtStillValid() {
            String token = signedRefreshToken();
            RefreshToken stored = storedRefreshToken(token);
            stored.setExpiresAt(Instant.now().minusSeconds(1));
            when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(stored));

            assertThatThrownBy(() -> tokenService.refreshAccessToken(token))
                    .isInstanceOf(TokenBlacklistedException.class);
        }

        @Test
        @DisplayName("un refresh token firmado con otra llave se rechaza sin tocar la BD")
        void foreignSignatureIsRejected() {
            String forged = refreshTokenSignedWith(attackerEncoder);

            assertThatThrownBy(() -> tokenService.refreshAccessToken(forged))
                    .isInstanceOf(InvalidTokenException.class);

            verify(refreshTokenRepository, never()).findByToken(anyString());
        }
    }

    @Nested
    @DisplayName("límite de sesiones concurrentes")
    class SessionLimit {

        @Test
        @DisplayName("al llegar al tope revoca la sesión más antigua para dejar espacio a la nueva")
        void revokesOldestSessionWhenCapReached() {
            List<RefreshToken> active = activeSessions(MAX_SESSIONS, Instant.now().minusSeconds(3600));
            active.get(0).setLastUsedAt(Instant.now().minusSeconds(7200)); // la más vieja
            when(refreshTokenRepository.findActiveTokensByUsername(eq(USERNAME), any(Instant.class)))
                    .thenReturn(active);

            tokenService.generateTokenPair(authentication());

            verify(refreshTokenRepository).saveAll(revokedTokens.capture());
            assertThat(revokedTokens.getValue()).hasSize(1);
            assertThat(revokedTokens.getValue().get(0).getRevoked()).isTrue();
            assertThat(revokedTokens.getValue().get(0).getJti()).isEqualTo(active.get(0).getJti());
        }

        @Test
        @DisplayName("por debajo del tope no revoca nada")
        void doesNotRevokeBelowCap() {
            when(refreshTokenRepository.findActiveTokensByUsername(eq(USERNAME), any(Instant.class)))
                    .thenReturn(activeSessions(MAX_SESSIONS - 1, Instant.now().minusSeconds(3600)));

            tokenService.generateTokenPair(authentication());

            verify(refreshTokenRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("cupo entero reciclado dentro de la ventana: registra RAPID_SESSION_CYCLING")
        void logsRapidSessionCycling() {
            when(refreshTokenRepository.findActiveTokensByUsername(eq(USERNAME), any(Instant.class)))
                    .thenReturn(activeSessions(MAX_SESSIONS, Instant.now().minusSeconds(5)));

            tokenService.generateTokenPair(authentication());

            verify(securityAuditService).logCriticalEvent(
                    eq(USERNAME), eq("RAPID_SESSION_CYCLING"), anyString(), anyMap());
        }

        @Test
        @DisplayName("sesiones repartidas en el tiempo: no dispara la alerta (uso normal multi-dispositivo)")
        void doesNotLogWhenSessionsAreOldEnough() {
            when(refreshTokenRepository.findActiveTokensByUsername(eq(USERNAME), any(Instant.class)))
                    .thenReturn(activeSessions(MAX_SESSIONS, Instant.now().minusSeconds(RAPID_CYCLE_WINDOW * 10)));

            tokenService.generateTokenPair(authentication());

            verify(securityAuditService, never())
                    .logCriticalEvent(anyString(), anyString(), anyString(), anyMap());
        }
    }

    @Nested
    @DisplayName("revocación y extracción")
    class RevocationAndExtraction {

        @Test
        @DisplayName("revokeRefreshToken marca el token como revocado")
        void revokeMarksTokenRevoked() {
            String token = signedRefreshToken();
            RefreshToken stored = storedRefreshToken(token);
            when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(stored));

            tokenService.revokeRefreshToken(token);

            verify(refreshTokenRepository).save(savedToken.capture());
            assertThat(savedToken.getValue().getRevoked()).isTrue();
        }

        @Test
        @DisplayName("revocar un token desconocido no falla (logout idempotente)")
        void revokeUnknownTokenIsNoOp() {
            when(refreshTokenRepository.findByToken("desconocido")).thenReturn(Optional.empty());

            assertThatCode(() -> tokenService.revokeRefreshToken("desconocido")).doesNotThrowAnyException();
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("lee el refresh token de la cookie 'refreshToken' e ignora las demás")
        void extractsRefreshTokenFromCookie() {
            var request = new org.springframework.mock.web.MockHttpServletRequest();
            request.setCookies(new Cookie("otra", "ruido"), new Cookie("refreshToken", "rt-123"));

            assertThat(tokenService.extractRefreshTokenFromCookie(request)).isEqualTo("rt-123");
        }

        @Test
        @DisplayName("sin cookies devuelve null en vez de romper")
        void returnsNullWhenNoCookies() {
            assertThat(tokenService.extractRefreshTokenFromCookie(
                    new org.springframework.mock.web.MockHttpServletRequest())).isNull();
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** Emite un refresh token real y lo deja registrado y activo en la BD mockeada. */
    private String validRefreshTokenRegisteredInDb() {
        String token = signedRefreshToken();
        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(storedRefreshToken(token)));
        return token;
    }

    /**
     * Un refresh token válido firmado con la llave del servidor. Se firma directo
     * (y no vía {@code generateTokenPair}) para que el escenario bajo prueba sea
     * la única interacción con el repositorio.
     */
    private String signedRefreshToken() {
        return refreshTokenSignedWith(serverEncoder);
    }

    /** Firma un refresh token bien formado con el encoder que se le pase. */
    private String refreshTokenSignedWith(JwtEncoder encoder) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(USERNAME)
                .audience(List.of("verygana-frontend"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(REFRESH_TTL))
                .id(UUID.randomUUID().toString())
                .claim("type", "refresh")
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private RefreshToken storedRefreshToken(String token) {
        Jwt jwt = decoder.decode(token);
        return new RefreshToken(USERNAME, token, jwt.getId(), jwt.getExpiresAt(), "127.0.0.1", "junit");
    }

    private List<RefreshToken> activeSessions(int count, Instant lastUsedAt) {
        List<RefreshToken> sessions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RefreshToken rt = new RefreshToken(USERNAME, "tok-" + i, "jti-" + i,
                    Instant.now().plusSeconds(REFRESH_TTL), "127.0.0.1", "junit");
            rt.setLastUsedAt(lastUsedAt);
            sessions.add(rt);
        }
        return sessions;
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(userDetails(), null,
                AuthorityUtils.createAuthorityList("ROLE_CONSUMER"));
    }

    private CustomUserDetails userDetails() {
        User user = new User();
        user.setId(42L);
        user.setEmail(USERNAME);
        user.setPassword("{bcrypt}irrelevante");
        user.setPasswordConfigured(true);
        user.setUserState(UserState.ACTIVE);
        return new CustomUserDetails(user, AuthorityUtils.createAuthorityList("ROLE_CONSUMER"));
    }

    private RsaKeyProperties generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        return new RsaKeyProperties((RSAPublicKey) kp.getPublic(), (RSAPrivateKey) kp.getPrivate());
    }
}