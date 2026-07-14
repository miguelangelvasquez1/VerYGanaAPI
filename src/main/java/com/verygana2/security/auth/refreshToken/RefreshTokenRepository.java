package com.verygana2.security.auth.refreshToken;
import java.time.Instant;
import java.util.Collection;
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
     * Tokens activos creados desde la ventana de tiempo indicada (para detectores
     * que comparan sesiones actualmente vivas: anomalía geográfica, session hijacking).
     */
    @Query("SELECT r FROM RefreshToken r WHERE r.createdAt > :since AND r.revoked = false AND r.expiresAt > :now")
    List<RefreshToken> findActiveTokensCreatedSince(@Param("since") Instant since,
                                                     @Param("now") Instant now);

    /**
     * TODOS los tokens creados desde la ventana indicada, sin filtrar por revoked —
     * a propósito, para token farming: enforceSessionLimit cap a 5 los tokens
     * ACTIVOS por usuario, así que contar solo activos nunca detectaría a alguien
     * creando tokens rápido vía refresh en loop (cada ciclo revoca el anterior,
     * manteniendo el activo bajo el límite mientras el total creado sigue subiendo).
     */
    @Query("SELECT r FROM RefreshToken r WHERE r.createdAt > :since")
    List<RefreshToken> findAllTokensCreatedSince(@Param("since") Instant since);

    /**
     * Tokens activos de un conjunto de usuarios (para evitar N+1 al analizar
     * el historial de varios usuarios de una sola vez).
     */
    @Query("SELECT r FROM RefreshToken r WHERE r.username IN :usernames AND r.revoked = false AND r.expiresAt > :now")
    List<RefreshToken> findActiveTokensByUsernameIn(@Param("usernames") Collection<String> usernames,
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
