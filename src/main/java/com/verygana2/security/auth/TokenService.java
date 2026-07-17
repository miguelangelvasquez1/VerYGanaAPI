package com.verygana2.security.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

import com.verygana2.dtos.auth.TokenPairDTO;
import com.verygana2.exceptions.authExceptions.InvalidTokenException;
import com.verygana2.exceptions.authExceptions.TokenBlacklistedException;
import com.verygana2.security.CustomUserDetails;
import com.verygana2.security.CustomUserDetailsService;
import com.verygana2.security.auth.refreshToken.RefreshToken;
import com.verygana2.security.auth.refreshToken.RefreshTokenRepository;
import com.verygana2.security.auth.refreshToken.SecurityAuditService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@Slf4j
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CustomUserDetailsService customUserDetailsService;
    private final SecurityAuditService securityAuditService;

    @Value("${jwt.access-token.expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    @Value("${spring.application.name}")
    private String issuer;

    @Value("${app.security.sessions.max-per-user:5}")
    private int maxSessionsPerUser;

    @Value("${app.security.sessions.rapid-cycle-window-seconds:60}")
    private int rapidCycleWindowSeconds;

    public TokenService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, RefreshTokenRepository refreshTokenRepository,
                         CustomUserDetailsService userService, SecurityAuditService securityAuditService) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.customUserDetailsService = userService;
        this.securityAuditService = securityAuditService;
    }

    /**
     * Genera un par de tokens (access + refresh) para un usuario autenticado
     */
    public TokenPairDTO generateTokenPair(Authentication authentication) {
        Instant now = Instant.now();
        String username = authentication.getName();
        
        // Generar access token con claims completos
        String accessToken = generateAccessToken(authentication, now);
        
        // Generar refresh token simple
        String refreshToken = generateRefreshToken(username, now);
        
        // Almacenar refresh token en Redis
        storeRefreshToken(username, refreshToken);
        
        log.info("Generated token pair for user: {}", username);
        
        return new TokenPairDTO(accessToken, refreshToken);
    }

    /**
     * Genera un nuevo access token usando el refresh token
     */
    public TokenPairDTO refreshAccessToken(String refreshToken) {
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
            
            return new TokenPairDTO(newAccessToken, newRefreshToken);
                    
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

        // Marcar como usado y blacklist token anterior
        refreshTokenRepository.findByToken(oldRefreshToken).ifPresent(rt -> {
            rt.setLastUsedAt(now);
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });

        return newRefreshToken;
    }

    /**
     * Métodos helper
     */
    private RefreshToken storeRefreshToken(String username, String token) {
        Jwt jwt = jwtDecoder.decode(token);
        String jti = jwt.getId();
        Instant expiryDate = jwt.getExpiresAt();

        enforceSessionLimit(username);

        String ipAddress = null;
        String userAgent = null;
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest req = attrs.getRequest();
            ipAddress = RequestClientInfo.resolveIp(req);
            userAgent = RequestClientInfo.resolveUserAgent(req);
        } catch (Exception ignored) {
            // No hay contexto HTTP (tests, async, etc.)
        }

        RefreshToken refreshToken = new RefreshToken(username, token, jti, expiryDate, ipAddress, userAgent);

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Si el usuario ya tiene maxSessionsPerUser o más sesiones activas, revoca las
     * más antiguas para hacerle espacio a la nueva (en vez de esperar a un job
     * periódico que deja una ventana de hasta 30 min con sesiones de sobra).
     */
    private void enforceSessionLimit(String username) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findActiveTokensByUsername(username, Instant.now());
        if (activeTokens.size() < maxSessionsPerUser) return;

        List<RefreshToken> sortedByAge = activeTokens.stream()
                .sorted(Comparator.comparing(t -> t.getLastUsedAt() != null ? t.getLastUsedAt() : t.getCreatedAt()))
                .toList();

        checkRapidCycling(username, sortedByAge);

        int excess = sortedByAge.size() - maxSessionsPerUser + 1;
        List<RefreshToken> toRevoke = sortedByAge.stream().limit(excess).toList();

        toRevoke.forEach(RefreshToken::revoke);
        refreshTokenRepository.saveAll(toRevoke);

        log.info("Auto-revoked {} oldest session(s) for user {} (limit={})", toRevoke.size(), username, maxSessionsPerUser);
    }

    /**
     * Si aun la sesión MÁS VIEJA de las activas (el cupo completo) fue creada/usada
     * hace muy poco, el usuario está reciclando el cupo de sesiones a una velocidad
     * que ningún uso normal produce — señal de farming vía refresh en loop, que el
     * cap de sesiones concurrentes por sí solo no puede detectar (revoca-y-crea
     * mantiene el conteo activo bajo el límite todo el tiempo). Solo se registra,
     * no bloquea la creación del token nuevo.
     */
    private void checkRapidCycling(String username, List<RefreshToken> sortedByAge) {
        RefreshToken oldestActive = sortedByAge.get(0);
        Instant oldestTimestamp = oldestActive.getLastUsedAt() != null
                ? oldestActive.getLastUsedAt()
                : oldestActive.getCreatedAt();

        Duration span = Duration.between(oldestTimestamp, Instant.now());
        if (span.compareTo(Duration.ofSeconds(rapidCycleWindowSeconds)) >= 0) return;

        log.warn("RAPID SESSION CYCLING - User: {}, {} active sessions all within {}s (oldest: {}s ago)",
                username, sortedByAge.size(), rapidCycleWindowSeconds, span.getSeconds());
        securityAuditService.logCriticalEvent(
                username,
                "RAPID_SESSION_CYCLING",
                String.format("User reached the %d-session cap with the oldest session only %ds old — possible token farming via refresh loop",
                        maxSessionsPerUser, span.getSeconds()),
                Map.of("maxSessionsPerUser", maxSessionsPerUser, "oldestSessionAgeSeconds", span.getSeconds())
        );
    }

    private boolean isRefreshTokenValid(String token) {
        return refreshTokenRepository.findByToken(token)
                .filter(rt -> !rt.getRevoked() && rt.getExpiresAt().isAfter(Instant.now())).isPresent();
    }

    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    /**
     * Extrae refresh token de la cookie
     */
    public String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            log.info("No cookies found in request");
            return null;
        }

        return Arrays.stream(cookies)
            .filter(cookie -> "refreshToken".equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private Authentication createAuthenticationFromUserDetails(UserDetails userDetails) {
    return new UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.getAuthorities()
    );
}
}