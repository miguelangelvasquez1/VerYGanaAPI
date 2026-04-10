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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.verygana2.models.enums.finance.CopaymentStatus;
import com.verygana2.models.products.Purchase;
import com.verygana2.models.userDetails.ConsumerDetails;

@Entity
@Table(name = "copayments")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Copayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false, unique = true)
    @NotNull
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id", nullable = false)
    @NotNull
    private ConsumerDetails consumer;

    /**
     * Cantidad de llaves usadas. Siempre > 0 porque la regla de negocio
     * exige que todo copago tenga una parte en llaves.
     * IMPORTANTE: esta restricción se valida en CopaymentService antes de
     * persistir. @Positive aquí actúa como segunda línea de defensa.
     */
    @Column(name = "keys_used", nullable = false)
    @Positive
    @NotNull
    private Long keysUsed;

    /**
     * Valor en centavos de las llaves usadas. keysUsed × 10 (1 llave = $10 COP).
     */
    @Column(name = "keys_value_cents", nullable = false)
    @Positive
    @NotNull
    private Long keysValueCents;

    /**
     * Parte pagada con dinero real vía Wompi. Siempre > 0 porque la regla
     * de negocio exige que todo copago tenga una parte en dinero real.
     * IMPORTANTE: igual que keysUsed, se valida en el servicio antes de persistir.
     */
    @Column(name = "cash_amount_cents", nullable = false)
    @Positive
    @NotNull
    private Long cashAmountCents;

    /**
     * Precio total del producto en el momento de la compra.
     * = keysValueCents + cashAmountCents.
     * Se persiste como snapshot porque el precio del producto puede cambiar.
     */
    @Column(name = "total_amount_cents", nullable = false)
    @Positive
    @NotNull
    private Long totalAmountCents;

    /**
     * Transacción de Wompi que corresponde a cashAmountCents.
     *
     * POR QUÉ ES NULLABLE en la BD aunque la regla de negocio diga que siempre debe existir:
     *
     * El copago se persiste en status=PENDING *antes* de crear la transacción en Wompi.
     * Esto es intencional: si el servidor cae después de que Wompi cobra pero antes de que
     * guardemos la referencia, necesitamos el registro del copago para poder hacer la
     * reconciliación y evitar cobrar dos veces al usuario.
     *
     * El flujo correcto en CopaymentService es:
     *   1. Validar que keysUsed > 0 y cashAmountCents > 0 (regla de negocio)
     *   2. Persistir Copayment(status=PENDING, wompiTransaction=null)
     *   3. Llamar a Wompi y obtener wompiId
     *   4. Actualizar Copayment con wompiTransaction
     *   5. Cuando Wompi confirma vía webhook → pasar a PROCESSING → COMPLETED
     *
     * La constraint de negocio "siempre debe haber wompiTransaction" se garantiza
     * en el servicio, no en la columna de BD. Nunca debe llegarse a COMPLETED sin ella.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wompi_transaction_id")
    private WompiTransaction wompiTransaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @NotNull
    private CopaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}