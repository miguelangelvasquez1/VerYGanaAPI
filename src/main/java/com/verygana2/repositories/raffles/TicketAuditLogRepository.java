package com.verygana2.repositories.raffles;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.raffles.TicketAuditLog;

@Repository
public interface TicketAuditLogRepository extends JpaRepository<TicketAuditLog, Long> {
    
    /**
     * Logs de un ticket específico
     */
    List<TicketAuditLog> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
    
    /**
     * Logs por acción
     */
    Page<TicketAuditLog> findByAction(String action, Pageable pageable);
    
    /**
     * Logs por fuente
     */
    Page<TicketAuditLog> findBySourceType(RaffleTicketSource sourceType, Pageable pageable);
    
    /**
     * Logs en un rango de fechas (para auditorías)
     */
    @Query("SELECT l FROM TicketAuditLog l WHERE l.createdAt BETWEEN :from AND :to " +
           "ORDER BY l.createdAt DESC")
    Page<TicketAuditLog> findLogsBetweenDates(
        @Param("from") ZonedDateTime from,
        @Param("to") ZonedDateTime to,
        Pageable pageable
    );
    
    /**
     * Logs sospechosos (misma IP, múltiples tickets en corto tiempo)
     */
    @Query("SELECT l.ipAddress, COUNT(l) FROM TicketAuditLog l " +
           "WHERE l.createdAt >= :since " +
           "GROUP BY l.ipAddress HAVING COUNT(l) > :threshold")
    List<Object[]> findSuspiciousActivity(
        @Param("since") ZonedDateTime since,
        @Param("threshold") long threshold
    );
}