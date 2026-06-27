package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.verygana2.models.enums.finance.KushkiTransactionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "kushki_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KushkiTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * ID de transferencia que devuelve Kushki al llamar /payouts/transfer/v1/init.
     * Null mientras el estado es INITIALIZED (antes de que Kushki responda).
     * Se usa para consultar estado y reconciliar webhooks entrantes.
     */
    @Column(name = "kushki_transfer_id", unique = true, length = 100)
    private String kushkiTransferId;

    /**
     * Referencia interna que enviamos a Kushki.
     * Generada por nosotros: "VG-PAYOUT-{payoutId}".
     * Permite encontrar el Payout correspondiente desde el webhook.
     */
    @Column(name = "internal_reference", nullable = false, unique = true, length = 100)
    @NotBlank
    private String internalReference;

    @Column(name = "amount_cents", nullable = false)
    @Positive
    @NotNull
    private Long amountCents;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "COP";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @NotNull
    private KushkiTransactionStatus status;

    /**
     * Payload completo del webhook de Kushki o respuesta de /init.
     * Se guarda tal como llega para auditoría y soporte.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private Map<String, Object> metadata;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "kushki_created_at")
    private ZonedDateTime kushkiCreatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
        if (this.currency == null) this.currency = "COP";
        if (this.status == null) this.status = KushkiTransactionStatus.INITIALIZED;
    }

    public boolean isTerminal() {
        return status == KushkiTransactionStatus.APPROVED
                || status == KushkiTransactionStatus.DECLINED
                || status == KushkiTransactionStatus.FAILED;
    }
}
