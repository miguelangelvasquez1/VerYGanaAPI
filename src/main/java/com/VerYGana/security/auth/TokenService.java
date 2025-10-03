package com.VerYGana.security.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import com.VerYGana.dtos2.auth.AuthResponse;
import com.VerYGana.exceptions.authExceptions.InvalidTokenException;
import com.VerYGana.exceptions.authExceptions.TokenBlacklistedException;
import com.VerYGana.security.CustomUserDetails;
import com.VerYGana.security.CustomUserDetailsService;
import com.VerYGana.security.auth.refreshToken.RefreshToken;
import com.VerYGana.security.auth.refreshToken.RefreshTokenRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CustomUserDetailsService customUserDetailsService;

    @Value("${jwt.access-token.expiration}")
    private long accessTokenExpiration;
    
    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    @Value("${spring.application.name}")
    private String issuer;

    public TokenService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, RefreshTokenRepository refreshTokenRepository, CustomUserDetailsService userService) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.customUserDetailsService = userService;
    }

    /**
     * Genera un par de tokens (access + refresh) para un usuario autenticado
     */
    public AuthResponse generateTokenPair(Authentication authentication) {
        Instant now = Instant.now();
        String username = authentication.getName();
        
        // Generar access token con claims completos
        String accessToken = generateAccessToken(authentication, now);
        
        // Generar refresh token simple
        String refreshToken = generateRefreshToken(username, now);
        
        // Almacenar refresh token en Redis
        storeRefreshToken(username, refreshToken);
        
        log.info("Generated token pair for user: {}", username);
        
        return new AuthResponse(accessToken, refreshToken);
    }

    /**
     * Genera un nuevo access token usando el refresh token
     */
    public AuthResponse refreshAccessToken(String refreshToken) {
        try {
            // Validar y decodificar refresh token
            Jwt jwt = validateAndDecodeRefreshToken(refreshToken);
            String username = jwt.getSubject();
            
            // Cargar usuario y crear Authentication
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
            Authentication authentication = createAuthenticationFromUserDetails(userDetails);
            
            Instant now = Instant.now();
            
            // Generar nuevo access token
            String newAccessToken = generateAccessToken(authentication, now);
            
            // Rotar refresh token (recomendado para máxima seguridad)
            String newRefreshToken = rotateRefreshToken(username, refreshToken, now);
            
            log.info("Refreshed tokens for user: {}", username);
            
            return new AuthResponse(newAccessToken, newRefreshToken);
                    
        } catch (JwtException e) {
            log.warn("Invalid refresh token: {}", e.getMessage());
            throw new InvalidTokenException("Invalid refresh token", e);
        }
    }

    /**
     * Genera access token con claims completos usando JwtEncoder
     */
    public String generateAccessToken(Authentication authentication, Instant issuedAt) {
         Instant expiresAt = issuedAt.plusSeconds(accessTokenExpiration);
        String scope = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(authentication.getName()) // <- Principal
                .id(UUID.randomUUID().toString()) // <- JTI Unique
                .audience(List.of("verygana-frontend"))
                .claim("type", "access")
                .claim("userId", ((CustomUserDetails) authentication.getPrincipal()).getId())
                .claim("scope", scope)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Genera refresh token simple con información mínima
     */
    private String generateRefreshToken(String username, Instant issuedAt) {
        Instant expiresAt = issuedAt.plusSeconds(refreshTokenExpiration);
        
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(username)
                .audience(List.of("verygana-frontend"))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString()) // JTI único para tracking
                .claim("type", "refresh")
                .build();
        
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Valida y decodifica refresh token
     */
    private Jwt validateAndDecodeRefreshToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            
            // Verificar que sea un refresh token
            String tokenType = jwt.getClaimAsString("type");
            if (!"refresh".equals(tokenType)) {
                throw new InvalidTokenException("Token is not a refresh token");
            }
            
            // Verificar que sea válido
            if (!isRefreshTokenValid(token)) {
                throw new TokenBlacklistedException("Refresh token not found or expired");
            }
            
            return jwt;
            
        } catch (JwtException e) {
            throw new InvalidTokenException("Invalid refresh token", e);
        }
    }

    /**
     * Rota el refresh token (genera nuevo y blacklist el anterior)
     */
    private String rotateRefreshToken(String username, String oldRefreshToken, Instant now) {
        // Generar nuevo refresh token
        String newRefreshToken = generateRefreshToken(username, now);
        
        // Almacenar nuevo token
        storeRefreshToken(username, newRefreshToken);
        
        // Blacklist token anterior
        revokeRefreshToken(oldRefreshToken);
        
        return newRefreshToken;
    }

    /**
     * Métodos helper
     */
    private RefreshToken storeRefreshToken(String username, String token) {
        Jwt jwt = jwtDecoder.decode(token);
        String jti = jwt.getId();
        Instant expiryDate = jwt.getExpiresAt();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsername(username);
        refreshToken.setToken(token);
        refreshToken.setJti(jti);
        refreshToken.setExpiresAt(expiryDate);

        return refreshTokenRepository.save(refreshToken);
    }

    private boolean isRefreshTokenValid(String token) {
        return refreshTokenRepository.findByToken(token)
                .filter(rt -> !rt.getRevoked() && rt.getExpiresAt().isAfter(Instant.now())).isPresent();
    }

    private void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    private Authentication createAuthenticationFromUserDetails(UserDetails userDetails) {
    return new UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.getAuthorities()
    );
}
}