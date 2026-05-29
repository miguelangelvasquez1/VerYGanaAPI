package com.verygana2.security.auth.refreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.User;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    Boolean existsByToken(String token);

    void deleteByUsername(User user);

    Optional<RefreshToken> findByJti(String jti);

    // ── Consultas de tokens activos ───────────────────────────────────────────

    @Query("SELECT r FROM RefreshToken r WHERE r.username = :username AND r.revoked = false AND r.expiresAt > :now")
    List<RefreshToken> findActiveTokensByUsername(@Param("username") String username,
                                                  @Param("now") Instant now);

    @Query("SELECT r FROM RefreshToken r WHERE r.revoked = false AND r.expiresAt > :now")
    List<RefreshToken> findAllActiveTokens(@Param("now") Instant now);

    @Query("SELECT COUNT(r) FROM RefreshToken r WHERE r.revoked = false AND r.expiresAt > :now")
    long countAllActiveTokens(@Param("now") Instant now);

    // ── Detección de actividad sospechosa ─────────────────────────────────────

    /**
     * Devuelve pares (ipAddress, count) de IPs con más de minAttempts tokens creados
     * desde la ventana de tiempo indicada.
     */
    @Query("""
        SELECT r.ipAddress, COUNT(r)
        FROM RefreshToken r
        WHERE r.createdAt > :since
          AND r.ipAddress IS NOT NULL
        GROUP BY r.ipAddress
        HAVING COUNT(r) >= :minAttempts
        ORDER BY COUNT(r) DESC
        """)
    List<Object[]> findSuspiciousIPs(@Param("since") Instant since,
                                     @Param("minAttempts") int minAttempts);

    /**
     * Tokens activos agrupados por deviceId (para detectar fingerprinting).
     */
    @Query("SELECT r FROM RefreshToken r WHERE r.createdAt > :since AND r.deviceId IS NOT NULL AND r.revoked = false AND r.expiresAt > :now")
    List<RefreshToken> findActiveTokensByDeviceSince(@Param("since") Instant since,
                                                     @Param("now") Instant now);

    // ── Limpieza ──────────────────────────────────────────────────────────────

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff AND r.revoked = false")
    int deleteExpiredTokens(@Param("cutoff") Instant cutoff);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.revoked = true AND r.expiresAt < :cutoff")
    int deleteOldRevokedTokens(@Param("cutoff") Instant cutoff);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.username = :username AND r.revoked = false")
    int revokeAllByUsername(@Param("username") String username);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.ipAddress = :ipAddress AND r.revoked = false")
    int revokeAllByIpAddress(@Param("ipAddress") String ipAddress);
}
