package com.verygana2.models.raffles;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.AuditAction;
import com.verygana2.models.enums.raffles.RaffleTicketSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ticket_audit_log", indexes = {
        @Index(name = "idx_ticket_action", columnList = "ticket_id, action"),
        @Index(name = "idx_source", columnList = "source_type, source_id"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private RaffleTicket ticket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private RaffleTicketSource sourceType;

    @Column(name = "source_id")
    private Long sourceId; // ID de la compra/suscripción/logro

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON con detalles adicionales

    @Column(nullable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
}
