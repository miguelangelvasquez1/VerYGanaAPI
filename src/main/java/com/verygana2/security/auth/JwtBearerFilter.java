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

@Component
public class JwtBearerFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtBearerFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public JwtBearerFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    //shouldFilterInternal?

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractTokenFromHeader(request);

        if (token != null) {
            try {
                // Decode and validate JWT (signature, expiration, issuer, audience) <-- IMPORTANTE
                Jwt jwt = jwtDecoder.decode(token);

                if (!isAccessToken(jwt)) {
                    logger.warn("JWT is not an access token");
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
                JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.debug("JWT authentication successful for user: {}", jwt.getSubject());

            } catch (JwtException e) {
                logger.warn("Invalid JWT token: {}", e.getMessage());
                SecurityContextHolder.clearContext();
                // throw new ServletException("Invalid JWT token", e);
            }
        } else {
            logger.debug("No valid Bearer token found in Authorization header");
        }

        // Always continue the filter chain
        filterChain.doFilter(request, response);
    }

    private String extractTokenFromHeader(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith(BEARER_PREFIX)) {
            String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
            return StringUtils.hasText(token) ? token : null;
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

    private boolean isAccessToken(Jwt jwt) {
        String type = jwt.getClaimAsString("type");
        return "access".equals(type);
    }
}