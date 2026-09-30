package com.verygana2.security.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import com.verygana2.services.UserIdResolver;

import jakarta.persistence.EntityNotFoundException;

@Component
public class JwtBearerFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtBearerFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SSE_ENDPOINT = "/notifications/stream";
    private static final String PQRS_ASSETS_PATH_SEGMENT = "/pqrs/assets/";
    private static final String PQRS_ASSET_VIEW_SUFFIX = "/view";
    private static final String PAYOUT_METHODS_PATH_SEGMENT = "/payout-methods/";
    private static final String PAYOUT_CERTIFICATE_SUFFIX = "/certificate";

    private final JwtDecoder jwtDecoder;
    private final UserIdResolver userIdResolver;

    public JwtBearerFilter(JwtDecoder jwtDecoder, UserIdResolver userIdResolver) {
        this.jwtDecoder = jwtDecoder;
        this.userIdResolver = userIdResolver;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null) {
            try {
                // Decode and validate JWT (signature, expiration, issuer, audience) <--
                // IMPORTANTE
                Jwt jwt = jwtDecoder.decode(token);

                if (!isAccessToken(jwt)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                jwt = withInternalUserId(jwt);

                Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
                JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException | EntityNotFoundException | IllegalArgumentException e) {
                logger.warn("Auth error: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        } else {
            logger.debug("No valid Bearer token found in Authorization header");
        }

        // Always continue the filter chain
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith(BEARER_PREFIX)) {
            String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
            if (StringUtils.hasText(token))
                return token;
        }
        String uri = request.getRequestURI();
        boolean isPqrsAssetView = uri.contains(PQRS_ASSETS_PATH_SEGMENT) && uri.endsWith(PQRS_ASSET_VIEW_SUFFIX);
        boolean isPayoutCertificate = uri.contains(PAYOUT_METHODS_PATH_SEGMENT) && uri.endsWith(PAYOUT_CERTIFICATE_SUFFIX);
        if (uri.endsWith(SSE_ENDPOINT) || uri.endsWith("/private-image") || isPqrsAssetView || isPayoutCertificate) {
            String queryToken = request.getParameter("token");
            if (StringUtils.hasText(queryToken)) {
                logger.debug("JWT extracted from query param for: {}", uri);
                return queryToken;
            }
        }
        return null;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        String scopes = jwt.getClaimAsString("scope");
        if (StringUtils.hasText(scopes)) {
            return AuthorityUtils.createAuthorityList(scopes.split("\\s+"));
        }
        return Collections.emptyList();
    }

    /**
     * El token solo transporta el publicId (el payload de un JWT es legible por cualquiera).
     * Aquí se traduce al id interno y se agrega como claim "userId" al Jwt en memoria, que es
     * lo que leen los controllers vía {@code jwt.getClaim("userId")}. El token firmado no cambia.
     */
    private Jwt withInternalUserId(Jwt jwt) {
        String publicId = jwt.getClaimAsString("publicId");
        if (!StringUtils.hasText(publicId)) {
            throw new JwtException("Access token without publicId claim");
        }
        Long userId = userIdResolver.toInternalId(UUID.fromString(publicId));
        return Jwt.withTokenValue(jwt.getTokenValue())
                .headers(h -> h.putAll(jwt.getHeaders()))
                .claims(c -> {
                    c.putAll(jwt.getClaims());
                    c.put("userId", userId);
                })
                .build();
    }

    private boolean isAccessToken(Jwt jwt) {
        String type = jwt.getClaimAsString("type");
        return "access".equals(type);
    }
}