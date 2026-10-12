package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.verygana2.models.enums.finance.KeyOriginType;
import com.verygana2.models.enums.finance.KeyTransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Asiento del Libro Mayor de Llaves (Contrato B 2.25): append-only, cronológico por
 * billetera ({@code sequence}) y con el saldo disponible de cada bolsa antes y después.
 *
 * Nunca se edita ni se borra (entidad {@link Immutable} + triggers MySQL en
 * V202610061600): toda corrección es un movimiento compensatorio que apunta al original
 * vía {@code reverses}. Solo los escribe KeyLedgerServiceImpl, que deriva los deltas del
 * cambio real de la billetera para que libro y saldo no puedan divergir.
 */
@Entity
@Immutable
@Table(name = "key_transactions", indexes = {
        @Index(name = "idx_kt_wallet_id",       columnList = "key_wallet_id"),
        @Index(name = "idx_kt_type",            columnList = "type"),
        @Index(name = "idx_kt_reference_id",    columnList = "reference_id"),
        @Index(name = "idx_kt_expires_at",      columnList = "expires_at"),
        @Index(name = "idx_kt_created_at",      columnList = "created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_kt_idempotency", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_kt_wallet_sequence", columnNames = { "key_wallet_id", "seq_no" })
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KeyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "key_wallet_id", nullable = false, updatable = false)
    @NotNull
    private KeyWallet keyWallet;

    /** Posición del asiento dentro de su billetera. Null solo en filas anteriores a MP-12. */
    @Column(name = "seq_no", updatable = false)
    private Long sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    @NotNull
    private KeyTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type", nullable = false, updatable = false)
    @NotNull
    @Builder.Default
    private KeyOriginType originType = KeyOriginType.LEGACY;

    /**
     * Cambio del saldo DISPONIBLE de llaves de compra, en centavos (positivo = entra).
     * Null si el movimiento no lo toca: la confirmación de un copago solo baja el
     * bloqueado, porque el disponible ya bajó al reservar.
     */
    @Column(name = "purchase_keys_delta_cents", updatable = false)
    private Long purchaseKeysDeltaCents;

    /** Cambio del saldo disponible de llaves de conectividad, en centavos. Null si no lo toca. */
    @Column(name = "connectivity_keys_delta_cents", updatable = false)
    private Long connectivityKeysDeltaCents;

    /**
     * Cambio del saldo BLOQUEADO de llaves de compra: sube al reservar un copago y baja
     * al confirmarlo o liberarlo. Null si el movimiento no lo toca.
     */
    @Column(name = "blocked_purchase_delta_cents", updatable = false)
    private Long blockedPurchaseDeltaCents;

    @Column(name = "purchase_balance_before_cents", updatable = false)
    private Long purchaseBalanceBeforeCents;

    @Column(name = "purchase_balance_after_cents", updatable = false)
    private Long purchaseBalanceAfterCents;

    @Column(name = "connectivity_balance_before_cents", updatable = false)
    private Long connectivityBalanceBeforeCents;

    @Column(name = "connectivity_balance_after_cents", updatable = false)
    private Long connectivityBalanceAfterCents;

    /**
     * Ítem del catálogo de mascotas que originó el gasto, o null si la compra no se
     * pudo resolver (ítem sin registrar) o la transacción no es del juego.
     *
     * Existe para poder medir ventas por producto sin partir el texto de {@code reason}.
     * Se guarda el id suelto y no una relación JPA a propósito: finanzas no debe
     * depender del modelo de mascotas, y para agregar por producto basta con el join.
     */
    @Column(name = "pet_catalog_item_id", updatable = false)
    private Long petCatalogItemId;

    /** Descripción legible del motivo para mostrar en el historial del usuario. */
    @Column(nullable = false, length = 255, updatable = false)
    @NotBlank
    private String reason;

    /**
     * ID de la operación que originó el movimiento, según {@code originType}: sesión de
     * anuncio, juego o encuesta, Copayment, o el lote de vencimiento de la billetera.
     */
    @Column(name = "reference_id", nullable = false, updatable = false)
    @NotNull
    private UUID referenceId;

    /** Movimiento que este compensa: la reserva que se libera, el pago que se reembolsa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_id", updatable = false)
    private KeyTransaction reverses;

    /** Misma clave → mismo asiento: reintentar una operación no la aplica dos veces. */
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 120)
    @NotBlank
    private String idempotencyKey;

    /**
     * Vencimiento, en UTC, de las llaves que este movimiento acredita. Null en los
     * movimientos que no acreditan. Es informativo para el historial: el remanente que
     * realmente vence se lleva en {@link KeyLot}.
     */
    @Column(name = "expires_at", updatable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
