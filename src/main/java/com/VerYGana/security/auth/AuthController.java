package com.VerYGana.security.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.VerYGana.services.interfaces.UserService;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final TokenService tokenService;
    private final AuthenticationManager authManager;
    private final UserService userService;

    public AuthController(TokenService tokenService, AuthenticationManager authManager, UserService userService) {
        this.tokenService = tokenService;
        this.authManager = authManager;
        this.userService = userService;
    }

    @GetMapping
    public String getMethodName() {
        return "Hello, " + "! You are authenticated.";
    }

    @PostMapping("/token")
    public String postMethodName(Authentication authorization) {
        String token = tokenService.generateToken(authorization);
        return token;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpServletResponse response) throws InterruptedException {
        try {
            Authentication authentication = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getIdentifier(), request.getPassword())); // -> Aquí se llama a UserDetailsService

            String token = tokenService.generateToken(authentication);

            ResponseCookie cookie = ResponseCookie.from("accessToken", token)
                    .httpOnly(true)
                    .secure(false) // Set to true if using HTTPS PRODUCTION
                    .path("/")
                    .maxAge(24 * 60 * 60) // 1 day or 15 minutes
                    .sameSite("Strict")
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            Thread.sleep(3000);
            return ResponseEntity.ok().body("Login successful: " + token);
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage()); //<-- No devolver
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRegisterRequest userRegisterRequest) {
        userService.registerUser(userRegisterRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
    }
}
