package com.verygana2.security.auth;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.auth.AuthRequest;
import com.verygana2.dtos.auth.AuthResponse;
import com.verygana2.dtos.auth.TokenPairDTO;
import com.verygana2.dtos.user.AdvertiserRegisterDTO;
import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.dtos.user.SellerRegisterDTO;
import com.verygana2.exceptions.authExceptions.InvalidTokenException;
import com.verygana2.services.interfaces.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final TokenService tokenService;
    private final AuthenticationManager authManager;
    private final UserService userService;

    @Value("${jwt.access-token.expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    public AuthController(TokenService tokenService, AuthenticationManager authManager, UserService userService) {
        this.tokenService = tokenService;
        this.authManager = authManager;
        this.userService = userService;
    }

    /**
     * Login: Autentica al usuario y genera un par de tokens (access + refresh)
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request)
            throws InterruptedException {

        log.info("Login attempt for user: {}", request.getIdentifier());

        Authentication authentication = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getIdentifier(), request.getPassword())); // -> Aquí se llama a UserDetailsService

        TokenPairDTO tokens = tokenService.generateTokenPair(authentication);

        String refreshTokenCookie = generateCookie(tokens.getRefreshToken(), "refreshToken");

        String scope = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        log.info("Login successful for user: {}", authentication.getName());
        return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshTokenCookie)
        .body(new AuthResponse(tokens.getAccessToken(), scope));
    }

    /**
     * Refresh: Usa el refresh token para generar un nuevo par de tokens
     */
    @PostMapping("/refresh") //Proteger contra CSRF
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        // Leer refresh token desde la cookie
        String refreshToken = tokenService.extractRefreshTokenFromCookie(request);

        if (refreshToken == null) {
            log.warn("Refresh token cookie not found");
            throw new InvalidTokenException("Refresh token not found");
        }

        TokenPairDTO tokenResponse = tokenService.refreshAccessToken(refreshToken);

        String refreshTokenCookie = generateCookie(tokenResponse.getRefreshToken(), "refreshToken");

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie)
            .body(new AuthResponse(tokenResponse.getAccessToken(), null));
    }

    /**
     * Logout: Invalida el refresh token y limpia cookies
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        log.info("Logout attempt");

        // Extraer refresh token para invalidarlo en Redis
        String refreshToken = tokenService.extractRefreshTokenFromCookie(request);
        
        if (refreshToken != null) {
            try {
                tokenService.revokeRefreshToken(refreshToken);
                log.info("Refresh token revoked successfully");
            } catch (Exception e) {
                log.warn("Error revoking refresh token", e);
            }
        }

        // Limpiar cookie
        clearRefreshTokenCookie(response);

        return ResponseEntity.noContent().build();
    }

    private String generateCookie(String token, String name) {
        java.util.Objects.requireNonNull(token, "token cannot be null");
        java.util.Objects.requireNonNull(name, "token name cannot be null");
        return ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(false) // Set to true if using HTTPS in PRODUCTION
                .path("/")
                .maxAge((name.equals("accessToken") ? accessTokenExpiration : refreshTokenExpiration) / 1000)
                .sameSite("Strict")
                .build()
                .toString();
    }

    /**
     * Limpia cookie de refresh token (logout)
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        String clearCookie = ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(0)  // Expira inmediatamente
            .sameSite("Strict")
            .build()
            .toString();

        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie);
    }

    @PostMapping("/register/consumer") //Devolver UserResponse o ConsumerRespone
    public ResponseEntity<?> registerConsumer(@Valid @RequestBody ConsumerRegisterDTO consumerRegisterRequest) {
        userService.registerConsumer(consumerRegisterRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body("Consumer registered successfully");
    }

    @PostMapping("/register/advertiser") //Devolver UserResponse o ConsumerRespone
    public ResponseEntity<?> registerAdvertiser(@Valid @RequestBody AdvertiserRegisterDTO consumerRegisterRequest) {
        userService.registerAdvertiser(consumerRegisterRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body("Advertiser registered successfully");
    }

    @PostMapping("/register/seller") //Devolver UserResponse o ConsumerRespone
    public ResponseEntity<?> registerSeller(@Valid @RequestBody SellerRegisterDTO consumerRegisterRequest) {
        userService.registerSeller(consumerRegisterRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body("Seller registered successfully");
    }
}