package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.verygana2.exceptions.InvalidAmountException;
import com.verygana2.models.enums.finance.KeyPocket;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Llaves de una bolsa que vencen en la misma fecha. Las de compra vencen todas el día 1
 * del mes siguiente, así que hay un lote por billetera y mes que crece con cada emisión.
 *
 * Existe para que el gasto se impute a lo que vence antes y el vencimiento extinga
 * exactamente lo que queda de ese lote (Contrato B 6.22.7). Solo se modifica con la
 * billetera bloqueada, desde KeyLedgerServiceImpl.
 */
@Entity
@Table(name = "key_lots", uniqueConstraints = @UniqueConstraint(
        name = "uk_key_lot_wallet_pocket_expiry", columnNames = { "key_wallet_id", "pocket", "expires_at" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeyLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key_wallet_id", nullable = false, updatable = false)
    private KeyWallet keyWallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "pocket", nullable = false, updatable = false)
    private KeyPocket pocket;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private ZonedDateTime expiresAt;

    /** Total emitido a este lote; no baja con el gasto. */
    @Column(name = "issued_cents", nullable = false)
    private Long issuedCents;

    @Column(name = "remaining_cents", nullable = false)
    private Long remainingCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public static KeyLot open(KeyWallet wallet, KeyPocket pocket, ZonedDateTime expiresAt) {
        KeyLot lot = new KeyLot();
        lot.keyWallet = wallet;
        lot.pocket = pocket;
        lot.expiresAt = expiresAt;
        lot.issuedCents = 0L;
        lot.remainingCents = 0L;
        return lot;
    }

    /** Emisión nueva: sube lo emitido y lo disponible. */
    public void issue(long cents) {
        requirePositive(cents);
        this.issuedCents += cents;
        this.remainingCents += cents;
    }

    /** Devolución de algo que ya se había emitido a este lote (reserva liberada). */
    public void giveBack(long cents) {
        requirePositive(cents);
        this.remainingCents += cents;
    }

    public void consume(long cents) {
        requirePositive(cents);
        if (cents > remainingCents) {
            throw new IllegalStateException(
                    "El lote " + id + " no tiene " + cents + " centavos disponibles (remanente " + remainingCents + ")");
        }
        this.remainingCents -= cents;
    }

    public boolean isExpiredAt(ZonedDateTime instant) {
        return !expiresAt.isAfter(instant);
    }

    private static void requirePositive(long cents) {
        if (cents <= 0) {
            throw new InvalidAmountException("El movimiento de un lote de llaves debe ser positivo");
        }
    }

    @PrePersist
    void prePersist() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
