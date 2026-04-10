package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.models.enums.finance.WompiTransactionStatus;

@Entity
@Table(name = "wompi_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WompiTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * ID de la transacción en el sistema de Wompi (ej: "1234567-1234567890-12345").
     * Se usa para reconciliar con el dashboard de Wompi y para consultar estado
     * en tiempo real vía su API. Único e inmutable una vez recibido.
     */
    @Column(name = "wompi_id", nullable = false, unique = true, updatable = false, length = 100)
    @NotBlank
    private String wompiId;

    /**
     * Tipo de operación Wompi: cobro al usuario (CHARGE) o transferencia
     * al empresario (TRANSFER). Determina qué flujo generó este registro.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    @NotNull
    private WompiTransactionType type;

    /**
     * Monto en centavos de COP, tal como lo reporta Wompi.
     * Wompi trabaja siempre en centavos para evitar errores de punto flotante.
     */
    @Column(name = "amount_cents", nullable = false, updatable = false)
    @Positive
    @NotNull
    private Long amountCents;

    /**
     * Moneda ISO 4217. En Colombia siempre "COP" pero se persiste
     * explícitamente para evitar asumir en queries y reportes.
     */
    @Column(nullable = false, updatable = false, length = 3)
    @NotBlank
    @Builder.Default
    private String currency = "COP";

    /**
     * Estado actual de la transacción según Wompi.
     * Se actualiza vía webhook: PENDING → APPROVED / DECLINED / ERROR / VOIDED.
     * Es el único campo mutable porque Wompi notifica cambios de estado.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @NotNull
    private WompiTransactionStatus status;

    /**
     * Referencia interna que enviamos a Wompi al crear la transacción.
     * Debe ser única por transacción y es la clave para reconciliar
     * webhooks entrantes con nuestros registros internos.
     */
    @Column(nullable = false, updatable = false, unique = true, length = 100)
    @NotBlank
    private String reference;

    /**
     * Payload completo del webhook o respuesta de Wompi guardado como JSONB.
     * Imprescindible para auditoría, disputas y depuración. No filtrar ni
     * truncar: guardar todo tal como llega de Wompi.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * Momento en que Wompi procesó la transacción (viene en el payload).
     * Distinto de createdAt que es cuando la registramos nosotros.
     */
    @Column(name = "wompi_created_at", updatable = false)
    private ZonedDateTime wompiCreatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    /**
     * Última vez que se actualizó el status vía webhook.
     */
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
        if (this.currency == null) this.currency = "COP";
    }
}