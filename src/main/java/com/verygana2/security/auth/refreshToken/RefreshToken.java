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

@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
        @Index(name = "idx_rt_username", columnList = "username"),
        @Index(name = "idx_rt_jti", columnList = "jti"),
        @Index(name = "idx_rt_ip", columnList = "ip_address"),
        @Index(name = "idx_rt_created_at", columnList = "created_at")
    }
)
@Data
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, length = 1024)
    private String token;

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

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    // ── Métodos helper ─────────────────────────────────────────────────────────

    public boolean isActive() {
        return !Boolean.TRUE.equals(revoked) && expiresAt.isAfter(Instant.now());
    }

    public void revoke() {
        this.revoked = true;
    }
}
