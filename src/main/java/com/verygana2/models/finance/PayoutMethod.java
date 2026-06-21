package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.verygana2.models.userDetails.CommercialDetails;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payout_methods", indexes = {
        @Index(name = "idx_payout_methods_commercial_id", columnList = "commercial_id"),
        @Index(name = "idx_payout_methods_verification_status", columnList = "verification_status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Empresario dueño de este método de pago.
     * Un comercial puede tener varios métodos registrados.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    /**
     * Tipo de canal de pago.
     * Determina qué campos son relevantes para ejecutar la transferencia en Wompi.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutMethodType type;

    /**
     * Alias amigable que el empresario le pone al método para identificarlo.
     * Ej: "Cuenta Bancolombia principal", "Nequi personal".
     */
    @Column(nullable = false, length = 100)
    private String alias;

    // ===== CAMPOS PARA BANK_TRANSFER =====

    /**
     * Código ACH del banco destino.
     * Solo aplica cuando type = BANK_TRANSFER.
     * Ejemplos: "1007" = Bancolombia, "1013" = Davivienda, "1006" = Banco Bogotá.
     * Wompi requiere este código para enrutar la transferencia interbancaria.
     */
    @Column(name = "bank_code", length = 10)
    private String bankCode;

    /**
     * Número de cuenta bancaria.
     * Solo aplica cuando type = BANK_TRANSFER.
     */
    @Column(name = "account_number", length = 30)
    private String accountNumber;

    /**
     * Tipo de cuenta: ahorros o corriente.
     * Solo aplica cuando type = BANK_TRANSFER.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "bank_account_type", length = 20)
    private BankAccountType bankAccountType;

    // ===== CAMPOS PARA NEQUI / DAVIPLATA =====

    /**
     * Número de celular registrado en Nequi o Daviplata.
     * Solo aplica cuando type = NEQUI o DAVIPLATA.
     */
    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    // ===== CAMPOS COMUNES =====

    /**
     * Nombre completo del titular de la cuenta o billetera.
     * Requerido por Wompi para todas las transferencias.
     */
    @Column(name = "account_holder_name", nullable = false, length = 200)
    private String accountHolderName;

    /**
     * Documento de identidad del titular.
     * Requerido para cumplimiento SARLAFT en transferencias bancarias colombianas.
     */
    @Column(name = "account_holder_doc", nullable = false, length = 20)
    private String accountHolderDoc;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_holder_doc_type", nullable = false, length = 10)
    private DocType accountHolderDocType;

    // ===== VERIFICACIÓN =====

    /**
     * Estado actual del flujo de verificación del método de pago.
     *
     * Flujo NEQUI / DAVIPLATA (automático vía Twilio Verify):
     *   PENDING_VERIFICATION → AWAITING_OTP → VERIFIED
     *                                       → REJECTED (si se agotan los intentos)
     *
     * Flujo BANK_TRANSFER (revisión manual admin):
     *   PENDING_VERIFICATION → UNDER_REVIEW → VERIFIED
     *                                       → REJECTED
     *
     * El PayoutScheduler solo ejecuta pagos a métodos en estado VERIFIED y activos.
     * Primer pago: retenido 24h (flag firstPayoutCompleted = false) estilo Airbnb.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING_VERIFICATION;

    /**
     * Motivo de rechazo, si el método fue rechazado por el admin o por OTP fallido.
     * Nulo cuando el estado es distinto de REJECTED.
     */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * Indica si este método está activo. Un comercial puede desactivar
     * un método sin borrarlo para mantener el historial de payouts anteriores
     * que usaron este método.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Bandera estilo Airbnb: el primer payout hacia este método queda retenido
     * 24h para revisión antifraude. Una vez que el primer pago se completa
     * exitosamente, los siguientes se liberan en el ciclo normal.
     */
    @Column(name = "first_payout_completed", nullable = false)
    @Builder.Default
    private boolean firstPayoutCompleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "verified_at")
    private ZonedDateTime verifiedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    // ===== MÉTODOS DE NEGOCIO =====

    public boolean canBeUsedForPayout() {
        return verificationStatus == VerificationStatus.VERIFIED && active;
    }

    public void markVerified() {
        this.verificationStatus = VerificationStatus.VERIFIED;
        this.verifiedAt = ZonedDateTime.now(ZoneOffset.UTC);
        this.rejectionReason = null;
    }

    public void reject(String reason) {
        this.verificationStatus = VerificationStatus.REJECTED;
        this.rejectionReason = reason;
    }

    public void markFirstPayoutCompleted() {
        this.firstPayoutCompleted = true;
    }

    // ===== ENUMS =====

    public enum PayoutMethodType {
        BANK_TRANSFER,
        NEQUI,
        DAVIPLATA
    }

    public enum BankAccountType {
        SAVINGS,   // Ahorros
        CHECKING   // Corriente
    }

    public enum DocType {
        CC,   // Cédula de ciudadanía
        CE,   // Cédula de extranjería
        NIT,  // NIT empresa
        PP    // Pasaporte
    }

    public enum VerificationStatus {
        /** Recién registrado, aún no se ha iniciado verificación. */
        PENDING_VERIFICATION,
        /** OTP enviado vía SMS, esperando confirmación del commercial (NEQUI/DAVIPLATA). */
        AWAITING_OTP,
        /** En revisión manual por admin (BANK_TRANSFER). */
        UNDER_REVIEW,
        /** Verificado y listo para recibir pagos. */
        VERIFIED,
        /** Falló la verificación (max intentos OTP o rechazo admin). */
        REJECTED,
        /** Suspendido por admin, no puede usarse aunque esté verificado. */
        SUSPENDED
    }
}
