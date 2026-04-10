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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.verygana2.models.enums.finance.KeyTransactionType;

@Entity
@Table(name = "key_transactions")
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
    @Column(nullable = false, length = 30)
    @NotNull
    private KeyTransactionType type;

    /**
     * Delta de llaves de compra. Positivo = crédito, negativo = débito.
     * Nullable porque una transacción puede no afectar este tipo.
     */
    @Column(name = "purchase_keys_delta")
    private Long purchaseKeysDelta;

    /**
     * Delta de llaves de conectividad. Positivo = crédito, negativo = débito.
     */
    @Column(name = "connectivity_keys_delta")
    private Long connectivityKeysDelta;

    @Column(nullable = false, length = 255)
    @NotBlank
    private String reason;

    @Column(name = "reference_id", nullable = false, updatable = false)
    @NotNull
    private UUID referenceId;

    /**
     * Fecha y hora exacta de vencimiento de este lote de llaves.
     *
     * REGLA DE VENCIMIENTO (por cambio de día, no por 24h rodantes):
     *
     * Para connectivity_keys:
     *   expires_at = inicio del día siguiente en zona Colombia (America/Bogota, UTC-5)
     *   convertido a UTC para almacenamiento.
     *   Ejemplo: llaves ganadas el 8 de abril a las 9 AM → expires_at = 9 abril 05:00 UTC
     *   Ejemplo: llaves ganadas el 8 de abril a las 11 PM → expires_at = 9 abril 05:00 UTC
     *   AMBOS lotes vencen exactamente al mismo tiempo. El usuario siempre ve
     *   "vencen hoy a medianoche" sin importar la hora en que las ganó.
     *
     * Para purchase_keys:
     *   expires_at = inicio del primer día del mes siguiente en zona Colombia.
     *   Ejemplo: llaves ganadas cualquier día de abril → expires_at = 1 mayo 05:00 UTC.
     *   Así todos los lotes del mes vencen juntos y el usuario ve un solo contador.
     *
     * NULLABLE: null significa que este lote nunca vence.
     * Hoy solo aplica a llaves del fondo de fortalecimiento (que ya no están en
     * este wallet, pero se deja nullable por extensibilidad futura).
     *
     * CÁLCULO EN EL SERVICIO (KeyTransactionService):
     *   ZoneId colombia = ZoneId.of("America/Bogota");
     *   // Para connectivity:
     *   ZonedDateTime expiresAt = ZonedDateTime.now(colombia)
     *       .toLocalDate()
     *       .plusDays(1)
     *       .atStartOfDay(colombia)
     *       .withZoneSameInstant(ZoneOffset.UTC);
     *   // Para purchase:
     *   ZonedDateTime expiresAt = ZonedDateTime.now(colombia)
     *       .toLocalDate()
     *       .withDayOfMonth(1)
     *       .plusMonths(1)
     *       .atStartOfDay(colombia)
     *       .withZoneSameInstant(ZoneOffset.UTC);
     */
    @Column(name = "expires_at")
    private ZonedDateTime expiresAt;

    /**
     * Marca si el job de vencimientos ya procesó este lote.
     * Previene doble procesamiento si el job se reinicia a mitad de ejecución.
     * El job filtra: WHERE expires_at < NOW() AND expiry_processed = false.
     * Solo aplica a registros con expiresAt no nulo.
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
}