package com.verygana2.utils.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
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
        @Param("fromDate") LocalDateTime fromDate,
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
        @Param("fromDate") LocalDateTime fromDate
    );

    // ==================== Estadísticas ====================

    @Query("SELECT COUNT(al) FROM AuditLog al WHERE al.userId = :userId " +
           "AND al.action = :action " +
           "AND al.createdAt >= :fromDate")
    long countByUserAndActionSince(
        @Param("userId") Long userId,
        @Param("action") String action,
        @Param("fromDate") LocalDateTime fromDate
    );

    @Query("SELECT al.action, COUNT(al) FROM AuditLog al " +
           "WHERE al.createdAt >= :fromDate " +
           "GROUP BY al.action " +
           "ORDER BY COUNT(al) DESC")
    List<Object[]> getTopActionsSince(@Param("fromDate") LocalDateTime fromDate);

    @Query("SELECT COUNT(al) FROM AuditLog al WHERE al.level = :level " +
           "AND al.createdAt >= :fromDate")
    long countByLevelSince(
        @Param("level") AuditLevel level,
        @Param("fromDate") LocalDateTime fromDate
    );

    // ==================== Limpieza ====================

    @Modifying
    @Query("DELETE FROM AuditLog al WHERE al.createdAt < :cutoffDate " +
           "AND al.level = :level")
    int deleteOldLogsByLevel(
        @Param("cutoffDate") LocalDateTime cutoffDate,
        @Param("level") AuditLevel level
    );

    @Modifying
    @Query("DELETE FROM AuditLog al WHERE al.createdAt < :cutoffDate")
    int deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);

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
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
}