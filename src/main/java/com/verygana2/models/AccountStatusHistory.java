package com.verygana2.models;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.AccountStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Auditoría de cada cambio de AccountStatus. Se escribe exclusivamente desde
 * AccountStatusService.transition(); las asignaciones iniciales al construir
 * un User nuevo (antes del primer save) no generan fila aquí porque no hay
 * un estado previo persistido del que partir.
 */
@Entity
@Table(
    name = "account_status_history",
    indexes = {
        @Index(name = "idx_ash_user_id", columnList = "user_id"),
        @Index(name = "idx_ash_user_id_to_status", columnList = "user_id, to_status, occurred_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 30)
    private AccountStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private AccountStatus toStatus;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(nullable = false, length = 255)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private ZonedDateTime occurredAt;
}
