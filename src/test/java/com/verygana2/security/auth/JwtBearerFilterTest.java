package com.verygana2.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Pruebas de seguridad del filtro de autenticación por Bearer.
 * Propiedades que blindan:
 *  - solo autentica tokens con claim type=access (un refresh token NO sirve).
 *  - las autoridades vienen del claim scope.
 *  - el token por query param (?token=) solo se admite en el SSE, no en
 *    endpoints normales (evita tokens en URLs que se loguean/cachean).
 *  - un token inválido no autentica pero no rompe la cadena (→ 401 luego).
 */
@DisplayName("JwtBearerFilter — autenticación por Bearer (seguridad)")
class JwtBearerFilterTest {

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final JwtBearerFilter filter = new JwtBearerFilter(decoder);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Jwt jwt(String type, String scope) {
        return Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("user@test.com")
                .claim("type", type)
                .claim("scope", scope)
                .build();
    }

    private MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    @Test
    @DisplayName("access token válido: autentica con las autoridades del scope")
    void authenticatesValidAccessToken() throws Exception {
        when(decoder.decode("tok")).thenReturn(jwt("access", "ROLE_ADMIN"));
        MockHttpServletRequest req = request("/api/admin/users");
        req.addHeader("Authorization", "Bearer tok");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("refresh token NO autentica (solo se acepta type=access)")
    void refreshTokenDoesNotAuthenticate() throws Exception {
        when(decoder.decode("tok")).thenReturn(jwt("refresh", "ROLE_ADMIN"));
        MockHttpServletRequest req = request("/api/admin/users");
        req.addHeader("Authorization", "Bearer tok");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("sin token: no autentica y no invoca el decoder")
    void noTokenNoAuth() throws Exception {
        MockHttpServletRequest req = request("/api/admin/users");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(decoder, never()).decode(anyString());
    }

    @Test
    @DisplayName("token por ?token= NO se acepta en un endpoint normal")
    void queryParamTokenRejectedOnNormalEndpoint() throws Exception {
        MockHttpServletRequest req = request("/api/admin/users");
        req.setParameter("token", "tok");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(decoder, never()).decode(anyString());
    }

    @Test
    @DisplayName("token por ?token= SÍ se acepta en el SSE de notificaciones")
    void queryParamTokenAcceptedOnSse() throws Exception {
        when(decoder.decode("tok")).thenReturn(jwt("access", "ROLE_CONSUMER"));
        MockHttpServletRequest req = request("/notifications/stream");
        req.setParameter("token", "tok");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("token inválido: no autentica pero la cadena continúa")
    void invalidTokenClearsContextAndContinues() throws Exception {
        when(decoder.decode("tok")).thenThrow(new BadJwtException("firma inválida"));
        MockHttpServletRequest req = request("/api/admin/users");
        req.addHeader("Authorization", "Bearer tok");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // la cadena siguió → llegará al entrypoint (401)
    }
}
