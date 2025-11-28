package com.verygana2.security.auth.refreshToken;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "refresh_tokens")
@Data
public class RefreshToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String username; //email
    
    @Column(nullable = false, length = 1024) // length = 512
    private String token; //guardar HMAC-SHA256 por ejemplo
    
    @Column(nullable = false, unique = true, length = 100)
    private String jti;
    
    @Column(nullable = false)
    private Instant expiresAt;
    
    @Column(nullable = false)
    private Boolean revoked = false;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
