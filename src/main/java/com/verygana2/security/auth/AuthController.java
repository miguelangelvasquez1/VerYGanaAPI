package com.verygana2.security.auth;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.auth.AuthRequest;
import com.verygana2.dtos.auth.AuthResponse;
import com.verygana2.dtos.user.AdvertiserRegisterDTO;
import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.dtos.user.SellerRegisterDTO;
import com.verygana2.exceptions.authExceptions.InvalidTokenException;
import com.verygana2.services.interfaces.UserService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

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

    @GetMapping
    public String getMethodName() {
        return "Hello, " + "! You are authenticated.";
    }

    /**
     * Login: Autentica al usuario y genera un par de tokens (access + refresh)
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request)
            throws InterruptedException {

        Authentication authentication = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getIdentifier(), request.getPassword())); // -> Aquí se llama a UserDetailsService

        AuthResponse token = tokenService.generateTokenPair(authentication);

        String accessTokenCookie = generateCookie(token.getAccessToken(), "accessToken");
        String refreshTokenCookie = generateCookie(token.getRefreshToken(), "refreshToken");

        return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, accessTokenCookie)
        .header(HttpHeaders.SET_COOKIE, refreshTokenCookie)
        .body(new AuthResponse(token.getAccessToken(), token.getRefreshToken()));
    }

    /**
     * Refresh: Usa el refresh token para generar un nuevo par de tokens
     */
    @PostMapping("/refresh") //Proteger contra CSRF
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        // Leer refresh token desde la cookie
        String refreshToken = Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new InvalidTokenException("Refresh token cookie not found"));

        AuthResponse tokenResponse = tokenService.refreshAccessToken(refreshToken);

        String refreshTokenCookie = generateCookie(tokenResponse.getRefreshToken(), "refreshToken");
        String accessTokenCookie = generateCookie(tokenResponse.getRefreshToken(), "refreshToken");

         return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, accessTokenCookie)
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie)
            .body(new AuthResponse(tokenResponse.getAccessToken(), tokenResponse.getRefreshToken()));
    }

    private String generateCookie(String token, String name) {
        return ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(false) // Set to true if using HTTPS in PRODUCTION
                .path("/auth/**")
                .maxAge((name.equals("accessToken") ? accessTokenExpiration : refreshTokenExpiration) / 1000)
                .sameSite("Strict")
                .build()
                .toString();
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
