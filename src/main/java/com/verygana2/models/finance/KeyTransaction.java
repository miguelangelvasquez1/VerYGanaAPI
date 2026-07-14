package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registro inmutable de cada movimiento de llaves en el wallet de un usuario.
 * Reemplaza completamente la entidad Transaction para operaciones con llaves.
 *
 * PRINCIPIO: una vez creado, ningún campo cambia. Si hay que corregir un error,
 * se crea una nueva KeyTransaction que compensa, nunca se edita la existente.
 * Esto garantiza un libro de llaves auditado e inalterable.
 */
@Entity
@Table(name = "key_transactions", indexes = {
        @Index(name = "idx_kt_wallet_id",       columnList = "key_wallet_id"),
        @Index(name = "idx_kt_type",            columnList = "type"),
        @Index(name = "idx_kt_reference_id",    columnList = "reference_id"),
        @Index(name = "idx_kt_expires_at",      columnList = "expires_at"),
        @Index(name = "idx_kt_expiry_processed",columnList = "expiry_processed"),
        @Index(name = "idx_kt_created_at",      columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "key_wallet_id", nullable = false)
    @NotNull
    private KeyWallet keyWallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    @NotNull
    private KeyTransactionType type;

    /**
     * Delta de llaves de compra afectadas en este movimiento, en centavos.
     * Positivo = crédito, negativo = débito.
     * Null si este movimiento no afecta las llaves de compra.
     */
    @Column(name = "purchase_keys_delta_cents", updatable = false)
    private Long purchaseKeysDeltaCents;

    /**
     * Delta de llaves de conectividad afectadas en este movimiento, en centavos.
     * Positivo = crédito, negativo = débito.
     * Null si este movimiento no afecta las llaves de conectividad.
     */
    @Column(name = "connectivity_keys_delta_cents", updatable = false)
    private Long connectivityKeysDeltaCents;

    /**
     * Descripción legible del motivo para mostrar en el historial del usuario.
     * Ejemplos:
     *   "Interacción con anuncio de Empresa X"
     *   "Copago orden #3fa85f64"
     *   "Recarga 1GB Claro"
     *   "Vencimiento mensual abril 2025"
     *   "Premio rifa #42 - Producto Netflix 1 mes"
     */
    @Column(nullable = false, length = 255, updatable = false)
    @NotBlank
    private String reason;

    /**
     * ID de la entidad que originó este movimiento.
     * Permite trazabilidad completa: dado un KeyTransaction siempre puedes
     * encontrar el origen exacto del movimiento.
     *
     * Ejemplos por tipo:
     *   CREDIT_INTERACTION          → ID del anuncio o juego interactuado
     *   CREDIT_REFERRAL_BONUS       → ID del usuario referido
     *   CREDIT_RAFFLE_PRIZE         → ID del sorteo
     *   DEBIT_COPAYMENT             → ID del Copayment
     *   DEBIT_CONNECTIVITY_RECHARGE → ID de la orden de recarga en Puntored
     *   DEBIT_RAFFLE_PARTICIPATION  → ID del sorteo
     *   DEBIT_EXPIRY                → ID del batch de vencimiento (KeyExpiryBatch)
     *   RESERVE_COPAYMENT_PENDING   → ID del Copayment
     *   RELEASE_COPAYMENT_CANCELLED → ID del Copayment cancelado
     */
    @Column(name = "reference_id", nullable = false, updatable = false)
    @NotNull
    private UUID referenceId;

    /**
     * Fecha exacta de vencimiento de este lote de llaves en UTC.
     *
     * Para purchase_keys:
     *   Primer día del mes siguiente a las 00:00 hora Colombia (05:00 UTC).
     *   Todas las llaves ganadas en abril vencen el 1 de mayo a las 05:00 UTC.
     *
     * Para connectivity_keys:
     *   Inicio del día siguiente a las 00:00 hora Colombia (05:00 UTC).
     *   Llaves ganadas el 8 de abril a cualquier hora vencen el 9 de abril 05:00 UTC.
     *
     * Null para movimientos que no tienen vencimiento:
     *   RESERVE_, RELEASE_, DEBIT_, CREDIT_ADMIN_ADJUSTMENT.
     *
     * CÁLCULO en KeyTransactionService:
     *   ZoneId colombia = ZonedDateTime.now(ZoneOffset.UTC);
     *   // Para purchase:
     *   ZonedDateTime.now(colombia).toLocalDate()
     *       .withDayOfMonth(1).plusMonths(1)
     *       .atStartOfDay(colombia).withZoneSameInstant(ZoneOffset.UTC);
     *   // Para connectivity:
     *   ZonedDateTime.now(colombia).toLocalDate()
     *       .plusDays(1)
     *       .atStartOfDay(colombia).withZoneSameInstant(ZoneOffset.UTC);
     */
    @Column(name = "expires_at", updatable = false)
    private ZonedDateTime expiredAt;

    /**
     * Flag que el job de vencimientos marca como true cuando procesa este lote.
     * Previene doble procesamiento si el job se reinicia a mitad de ejecución.
     * Query del job: WHERE expires_at < NOW() AND expiry_processed = false.
     *
     * Es el único campo mutable de esta entidad: pasa de false a true una sola vez.
     */
    @Column(name = "expiry_processed", nullable = false)
    @Builder.Default
    private Boolean expiryProcessed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
        if (this.expiryProcessed == null) this.expiryProcessed = false;
    }

    // ─── FACTORIES ────────────────────────────────────────────────────────────
    // Reemplazan los métodos estáticos de la Transaction eliminada.
    // Cada factory corresponde a un caso de uso específico del sistema.

    public static KeyTransaction forInteractionPurchaseKeys(
            KeyWallet wallet, long purchaseDeltaCents,
            String reason, UUID referenceId,
            ZonedDateTime expiredAt) {
        return KeyTransaction.builder()
                .keyWallet(wallet)
                .type(KeyTransactionType.CREDIT_INTERACTION)
                .purchaseKeysDeltaCents(purchaseDeltaCents)
                .reason(reason)
                .referenceId(referenceId)
                .expiredAt(expiredAt)
                .build();
    }

    public static KeyTransaction forInteractionConnectivityKeys(
            KeyWallet wallet, long connectivityDeltaCents,
            String reason, UUID referenceId,
            ZonedDateTime expiredAt) {
        return KeyTransaction.builder()
                .keyWallet(wallet)
                .type(KeyTransactionType.CREDIT_INTERACTION)
                .connectivityKeysDeltaCents(connectivityDeltaCents)
                .reason(reason)
                .referenceId(referenceId)
                .expiredAt(expiredAt)
                .build();
    }

    public static KeyTransaction forReferralBonus(
            KeyWallet wallet, long purchaseDeltaCents, String reason,
            UUID referenceId, ZonedDateTime expiredAt) {
        return KeyTransaction.builder()
                .keyWallet(wallet)
                .type(KeyTransactionType.CREDIT_REFERRAL_BONUS)
                .purchaseKeysDeltaCents(purchaseDeltaCents)
                .reason(reason)
                .referenceId(referenceId)
                .expiredAt(expiredAt)
                .build();
    }

    public static KeyTransaction forCopaymentReserve(
            KeyWallet wallet, long amountCentsToReserve, UUID copaymentId) {
        return KeyTransaction.builder()
                .keyWallet(wallet)
                .type(KeyTransactionType.RESERVE_COPAYMENT_PENDING)
                .purchaseKeysDeltaCents(-amountCentsToReserve)
                .reason("Reserva de llaves para copago en proceso")
                .referenceId(copaymentId)
                .build();
    }

    public static KeyTransaction forCopaymentConfirm(
            KeyWallet wallet, long amountCentsDebited, UUID copaymentId, String productName) {
        return KeyTransaction.builder()
                .keyWallet(wallet)
                .type(KeyTransactionType.DEBIT_COPAYMENT)
                .purchaseKeysDeltaCents(-amountCentsDebited)
                .reason("Pago con llaves: " + productName)
                .referenceId(copaymentId)
                .build();
    }

    public static KeyTransaction forCopaymentRelease(
            KeyWallet wallet, long amountCentsReleased, UUID copaymentId) {
        return KeyTransaction.builder()
                .keyWallet(wallet)
                .type(KeyTransactionType.RELEASE_COPAYMENT_CANCELLED)
                .purchaseKeysDeltaCents(amountCentsReleased)
                .reason("Devolución de llaves: copago cancelado o rechazado")
                .referenceId(copaymentId)
                .build();
    }

    public static KeyTransaction forConnectivityRecharge(
            KeyWallet wallet, long amountCentsDebited, String rechargeDescription, UUID rechargeOrderId) {
        return KeyTransaction.builder()
                .keyWallet(wallet)
                .type(KeyTransactionType.DEBIT_CONNECTIVITY_RECHARGE)
                .connectivityKeysDeltaCents(-amountCentsDebited)
                .reason("Recarga: " + rechargeDescription)
                .referenceId(rechargeOrderId)
                .build();
    }

    public static KeyTransaction forExpiry(
            KeyWallet wallet, long purchaseExpiredCents, long connectivityExpiredCents,
            UUID expiryBatchId, String period) {
        return KeyTransaction.builder()
                .keyWallet(wallet)
                .type(KeyTransactionType.EXPIRED)
                .purchaseKeysDeltaCents(purchaseExpiredCents > 0 ? -purchaseExpiredCents : null)
                .connectivityKeysDeltaCents(connectivityExpiredCents > 0 ? -connectivityExpiredCents : null)
                .reason("Vencimiento de llaves - período " + period)
                .referenceId(expiryBatchId)
                .expiryProcessed(true)
                .build();
    }

    // KeyTransaction.java — agregar factory method
    public static KeyTransaction forPetGame(
            KeyWallet wallet, long amountCentsSpent, String itemName) {
        return KeyTransaction.builder()
                .keyWallet(wallet)
                .type(KeyTransactionType.DEBIT_PET_GAME)
                .purchaseKeysDeltaCents(-amountCentsSpent)
                .reason("Mascota virtual: " + itemName)
                .referenceId(UUID.randomUUID()) // no hay entidad externa, se genera aquí
                .build();
    }
}