package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.verygana2.models.enums.finance.MovementConcept;

@Entity
@Table(name = "treasury_movements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreasuryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * Cuenta de origen del movimiento (de dónde sale el dinero).
     * Ejemplo: KEYS_RESERVE cuando la app asume la parte de llaves de un copago.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id", nullable = false)
    @NotNull
    private TreasuryAccount fromAccount;

    /**
     * Cuenta de destino del movimiento (a dónde va el dinero).
     * Ejemplo: PAYOUTS_PENDING cuando se acumula dinero para el pago al empresario.
     * Modelo de partida doble: todo movimiento tiene origen Y destino dentro del sistema.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id", nullable = false)
    @NotNull
    private TreasuryAccount toAccount;

    /**
     * Monto en centavos de COP. Siempre positivo.
     * El sentido del movimiento lo dan fromAccount y toAccount.
     */
    @Column(name = "amount_cents", nullable = false)
    @Positive
    @NotNull
    private Long amountCents;

    /**
     * Tipo de operación que originó este movimiento.
     * Permite filtrar el libro contable por categoría.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @NotNull
    private MovementConcept concept;

    /**
     * ID de la entidad que causó este movimiento (UUID de orden, copago, depósito, etc.).
     * Junto con referenceType permite trazabilidad completa: dado un movimiento
     * siempre puedes encontrar exactamente qué operación de negocio lo generó.
     */
    @Column(name = "reference_id", nullable = false, updatable = false)
    @NotNull
    private UUID referenceId;

    /**
     * Tipo de entidad referenciada. Complementa referenceId para saber
     * en qué tabla buscar el origen del movimiento.
     */
    @Column(name = "reference_type", nullable = false, updatable = false, length = 40)
    @NotNull
    private String referenceType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}