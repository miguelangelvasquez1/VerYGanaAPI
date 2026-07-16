package com.verygana2.utils.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // ==================== Búsquedas por Usuario ====================

    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    Page<AuditLog> findByUserIdAndAction(Long userId, String action, Pageable pageable);

    @Query("SELECT al FROM AuditLog al WHERE al.userId = :userId " +
           "AND al.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY al.createdAt DESC")
    Page<AuditLog> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("startDate") ZonedDateTime startDate,
        @Param("endDate") ZonedDateTime endDate,
        Pageable pageable
    );

    // ==================== Búsquedas por Acción ====================

    Page<AuditLog> findByAction(String action, Pageable pageable);

    Page<AuditLog> findByActionAndLevel(String action, AuditLevel level, Pageable pageable);

    // ==================== Búsquedas por Nivel ====================

    Page<AuditLog> findByLevel(AuditLevel level, Pageable pageable);

    @Query("SELECT al FROM AuditLog al WHERE al.level = :level " +
           "AND al.createdAt >= :fromDate " +
           "ORDER BY al.createdAt DESC")
    Page<AuditLog> findRecentByLevel(
        @Param("level") AuditLevel level,
        @Param("fromDate") ZonedDateTime fromDate,
        Pageable pageable
    );

    // ==================== Búsquedas por Categoría ====================

    Page<AuditLog> findByCategory(String category, Pageable pageable);

    // ==================== Búsquedas por Entidad ====================

    Page<AuditLog> findByEntityTypeAndEntityId(
        String entityType, 
        Long entityId, 
        Pageable pageable
    );

    @Query("SELECT al FROM AuditLog al WHERE al.entityType = :entityType " +
           "AND al.entityId = :entityId " +
           "ORDER BY al.createdAt DESC")
    List<AuditLog> findEntityHistory(
        @Param("entityType") String entityType,
        @Param("entityId") Long entityId
    );

    // ==================== Búsquedas por Éxito/Fallo ====================

    Page<AuditLog> findBySuccessFalse(Pageable pageable);

    @Query("SELECT al FROM AuditLog al WHERE al.success = false " +
           "AND al.userId = :userId " +
           "AND al.createdAt >= :fromDate " +
           "ORDER BY al.createdAt DESC")
    List<AuditLog> findRecentFailuresByUser(
        @Param("userId") Long userId,
        @Param("fromDate") ZonedDateTime fromDate
    );

    // ==================== Estadísticas ====================

    @Query("SELECT COUNT(al) FROM AuditLog al WHERE al.userId = :userId " +
           "AND al.action = :action " +
           "AND al.createdAt >= :fromDate")
    long countByUserAndActionSince(
        @Param("userId") Long userId,
        @Param("action") String action,
        @Param("fromDate") ZonedDateTime fromDate
    );

    @Query("SELECT al.action, COUNT(al) FROM AuditLog al " +
           "WHERE al.createdAt >= :fromDate " +
           "GROUP BY al.action " +
           "ORDER BY COUNT(al) DESC")
    List<Object[]> getTopActionsSince(@Param("fromDate") ZonedDateTime fromDate);

    @Query("SELECT al.action, COUNT(al) FROM AuditLog al " +
           "WHERE al.category = :category AND al.createdAt >= :fromDate " +
           "GROUP BY al.action " +
           "ORDER BY COUNT(al) DESC")
    List<Object[]> getTopActionsByCategorySince(@Param("category") String category,
                                                 @Param("fromDate") ZonedDateTime fromDate);

    /**
     * Igual que searchAuditLogs pero agregado (conteo por action) — para que el
     * resumen de un listado filtrado use exactamente los mismos filtros que la
     * búsqueda paginada, en vez de una ventana de tiempo fija independiente.
     */
    @Query("SELECT al.action, COUNT(al) FROM AuditLog al WHERE " +
           "(:action IS NULL OR al.action = :action) AND " +
           "(:level IS NULL OR al.level = :level) AND " +
           "al.category = :category AND " +
           "al.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY al.action " +
           "ORDER BY COUNT(al) DESC")
    List<Object[]> searchTopActionsByCategory(@Param("action") String action,
                                               @Param("level") AuditLevel level,
                                               @Param("category") String category,
                                               @Param("startDate") ZonedDateTime startDate,
                                               @Param("endDate") ZonedDateTime endDate);

    @Query("SELECT COUNT(al) FROM AuditLog al WHERE al.level = :level " +
           "AND al.createdAt >= :fromDate")
    long countByLevelSince(
        @Param("level") AuditLevel level,
        @Param("fromDate") ZonedDateTime fromDate
    );

    // ==================== Fuerza bruta / credential stuffing (LOGIN_FAILED) ====================

    /**
     * IPs con al menos minAttempts logins FALLIDOS desde la ventana indicada — la
     * señal real de credential stuffing (a diferencia de solo contar éxitos, que
     * puede confundir tráfico legítimo de IPs compartidas con un ataque).
     */
    @Query("SELECT al.ipAddress, COUNT(al) FROM AuditLog al " +
           "WHERE al.category = 'AUTH' AND al.action = 'LOGIN_FAILED' " +
           "AND al.createdAt >= :since AND al.ipAddress IS NOT NULL " +
           "GROUP BY al.ipAddress " +
           "HAVING COUNT(al) >= :minAttempts " +
           "ORDER BY COUNT(al) DESC")
    List<Object[]> findIpsWithFailedLoginsSince(@Param("since") ZonedDateTime since,
                                                 @Param("minAttempts") int minAttempts);

    @Query("SELECT al FROM AuditLog al " +
           "WHERE al.ipAddress = :ip AND al.category = 'AUTH' AND al.action = 'LOGIN_FAILED' " +
           "AND al.createdAt >= :since AND al.createdAt <= :now")
    List<AuditLog> findFailedLoginsByIpSince(@Param("ip") String ip,
                                              @Param("since") ZonedDateTime since,
                                              @Param("now") ZonedDateTime now);

    // ==================== Limpieza ====================

    @Modifying
    @Query("DELETE FROM AuditLog al WHERE al.createdAt < :cutoffDate " +
           "AND al.level = :level")
    int deleteOldLogsByLevel(
        @Param("cutoffDate") ZonedDateTime cutoffDate,
        @Param("level") AuditLevel level
    );

    @Modifying
    @Query("DELETE FROM AuditLog al WHERE al.createdAt < :cutoffDate")
    int deleteOldLogs(@Param("cutoffDate") ZonedDateTime cutoffDate);

    // ==================== Búsqueda Avanzada ====================

    @Query("SELECT al FROM AuditLog al WHERE " +
           "(:userId IS NULL OR al.userId = :userId) AND " +
           "(:action IS NULL OR al.action = :action) AND " +
           "(:level IS NULL OR al.level = :level) AND " +
           "(:category IS NULL OR al.category = :category) AND " +
           "(:success IS NULL OR al.success = :success) AND " +
           "al.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY al.createdAt DESC")
    Page<AuditLog> searchAuditLogs(
        @Param("userId") Long userId,
        @Param("action") String action,
        @Param("level") AuditLevel level,
        @Param("category") String category,
        @Param("success") Boolean success,
        @Param("startDate") ZonedDateTime startDate,
        @Param("endDate") ZonedDateTime endDate,
        Pageable pageable
    );

    // ==================== Audit logs no-security (WARNING/CRITICAL) ====================

    /**
     * Eventos WARNING/CRITICAL de cualquier categoría EXCEPTO SECURITY — esa ya se
     * expone aparte en /admin/security-events. Pensado para /admin/audit-logs, donde
     * el front lista el resto de eventos relevantes (ej. PQRS, y lo que se agregue a
     * futuro) sin duplicar lo que ya se ve en el panel de seguridad.
     */
    @Query("SELECT al FROM AuditLog al WHERE " +
           "al.category <> 'SECURITY' AND al.level IN ('WARNING', 'CRITICAL') AND " +
           "(:action IS NULL OR al.action = :action) AND " +
           "(:category IS NULL OR al.category = :category) AND " +
           "(:level IS NULL OR al.level = :level) AND " +
           "al.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY al.createdAt DESC")
    Page<AuditLog> searchNonSecurityAuditLogs(
        @Param("action") String action,
        @Param("category") String category,
        @Param("level") AuditLevel level,
        @Param("startDate") ZonedDateTime startDate,
        @Param("endDate") ZonedDateTime endDate,
        Pageable pageable
    );

    /** Igual que searchNonSecurityAuditLogs pero agregado (conteo por action), mismos filtros. */
    @Query("SELECT al.action, COUNT(al) FROM AuditLog al WHERE " +
           "al.category <> 'SECURITY' AND al.level IN ('WARNING', 'CRITICAL') AND " +
           "(:action IS NULL OR al.action = :action) AND " +
           "(:category IS NULL OR al.category = :category) AND " +
           "(:level IS NULL OR al.level = :level) AND " +
           "al.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY al.action " +
           "ORDER BY COUNT(al) DESC")
    List<Object[]> searchTopNonSecurityActions(
        @Param("action") String action,
        @Param("category") String category,
        @Param("level") AuditLevel level,
        @Param("startDate") ZonedDateTime startDate,
        @Param("endDate") ZonedDateTime endDate
    );
}