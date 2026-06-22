package com.verygana2.models.levels;

import java.time.LocalDateTime;

import com.verygana2.models.enums.ActivityType;

import jakarta.persistence.*;
import lombok.*;

/**
 * Log inmutable de cada transacción de XP/llaves.
 * NUNCA se usa para calcular balances (para eso están los totales en UserLevelProfile).
 * Solo sirve para historial, auditoría y misión de reactivación.
 */
@Entity
@Table(
        name = "xp_key_transaction_log",
        indexes = {
                // Índice compuesto para queries de historial paginado — O(log n)
                @Index(name = "idx_xplog_consumer_created", columnList = "consumer_id, created_at DESC")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XpKeyTransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer_id", nullable = false)
    private Long consumerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityType activityType;

    @Column(nullable = false)
    private Long xpEarned;

    /** Multiplicador aplicado en el momento de la transacción */
    @Column(nullable = false)
    private Double multiplierApplied;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}