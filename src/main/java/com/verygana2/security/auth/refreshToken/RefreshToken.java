package com.verygana2.security.auth.refreshToken;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
        @Index(name = "idx_rt_username", columnList = "username"),
        @Index(name = "idx_rt_jti", columnList = "jti"),
        @Index(name = "idx_rt_ip", columnList = "ip_address"),
        @Index(name = "idx_rt_created_at", columnList = "created_at"),
        @Index(name = "uk_rt_token_hash", columnList = "token_hash", unique = true)
    }
)
@Data
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    // Huella SHA-256 (hex) del refresh token; el token en claro nunca se guarda.
    @ToString.Exclude
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, unique = true, length = 100)
    private String jti;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Boolean revoked = false;

    @Column(nullable = false, updatable = false, name = "created_at")
    private Instant createdAt = Instant.now();

    // ── Campos de seguridad ────────────────────────────────────────────────────

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public RefreshToken(String username, String tokenHash, String jti, Instant expiresAt, String ipAddress, String userAgent) {
        this.username = username;
        this.tokenHash = tokenHash;
        this.jti = jti;
        this.expiresAt = expiresAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.lastUsedAt = Instant.now();
    }

    // ── Métodos helper ─────────────────────────────────────────────────────────

    public boolean isActive() {
        return !Boolean.TRUE.equals(revoked) && expiresAt.isAfter(Instant.now());
    }

    public void revoke() {
        this.revoked = true;
    }
}
