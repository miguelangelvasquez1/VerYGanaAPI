package com.verygana2.models.raffles;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad RaffleConfig - Configuración de regla para una rifa específica
 * 
 * Responsabilidades:
 * - Conectar una rifa con una regla de obtención
 * - Permitir activar/desactivar regla para rifa específica
 * - Establecer límites específicos de esta regla en esta rifa
 * - Trackear cuántos tickets se han emitido por esta regla en esta rifa
 * - Sobrescribir parámetros de la regla global si es necesario
 * 
 * NO es responsable de:
 * - Lógica de validación de la regla (eso es TicketEarningRule)
 * - Emisión física de tickets (eso es TicketIssuanceService)
 * - Validar elegibilidad de usuarios (eso es TicketIssuanceService)
 */
@Entity
@Table(name = "raffle_rules", indexes = {
        @Index(name = "idx_config_raffle_rule", columnList = "raffle_id, rule_id"),
        @Index(name = "idx_config_active", columnList = "raffle_id, is_active"),
        @Index(name = "idx_config_raffle", columnList = "raffle_id"),
        @Index(name = "idx_config_rule", columnList = "rule_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_raffle_rule", columnNames = { "raffle_id", "rule_id" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RaffleRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raffle_id", nullable = false)
    private Raffle raffle;
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private TicketEarningRule ticketEarningRule;

    @Column(name = "max_tickets_by_source")
    private Long maxTicketsBySource;

    @Column(name = "current_tickets_by_source")
    private Long currentTicketsBySource = 0L;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy; // ID del admin que creó la configuración

    // ==================== LIFECYCLE HOOKS ====================

    @PrePersist
    public void onCreate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        this.createdAt = now;
        this.updatedAt = now;
        this.isActive = true;
        this.currentTicketsBySource = 0L;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }

    public boolean canIssueTickets(int quantity) {
        // 1. Verificar que la config esté activa
        if (!isActive) {
            return false;
        }

        // 2. Verificar que la regla esté activa
        if (!ticketEarningRule.isActive()) {
            return false;
        }

        // 3. Verificar límite específico de esta fuente
        if (maxTicketsBySource != null) {
            return (currentTicketsBySource + quantity) <= maxTicketsBySource;
        }

        return true;
    }
    /**
     * Obtiene cuántos tickets quedan disponibles para esta configuración
     */
    public Long getRemainingTickets() {
        if (maxTicketsBySource == null) {
            return null; // Sin límite específico
        }
        return Math.max(0, maxTicketsBySource - currentTicketsBySource);
    }

    /**
     * Obtiene el porcentaje de tickets ya otorgados
     */
    public Double getUsagePercentage() {
        if (maxTicketsBySource == null || maxTicketsBySource == 0) {
            return null;
        }
        return (currentTicketsBySource.doubleValue() / maxTicketsBySource.doubleValue()) * 100.0;
    }

    /**
     * Incrementa el contador de tickets otorgados
     */
    public void incrementIssuedCount(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.currentTicketsBySource += quantity;
    }

    /**
     * Verifica si está cerca de alcanzar el límite (>80%)
     */
    public boolean isNearingLimit() {
        if (maxTicketsBySource == null) {
            return false;
        }
        Double percentage = getUsagePercentage();
        return percentage != null && percentage >= 80.0;
    }

    public void updateCurrentTicketsFromPurchases(int newTickets) {
        this.currentTicketsBySource = currentTicketsBySource + newTickets;
    }


}
