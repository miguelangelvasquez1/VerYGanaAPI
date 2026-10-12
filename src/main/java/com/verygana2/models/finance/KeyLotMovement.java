package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Efecto de un movimiento del libro sobre un lote concreto (positivo = entra al lote).
 * Un movimiento puede tocar varios lotes: un gasto que agota el que vence antes y sigue
 * con el siguiente.
 */
@Entity
@Immutable
@Table(name = "key_lot_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeyLotMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key_transaction_id", nullable = false, updatable = false)
    private KeyTransaction keyTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key_lot_id", nullable = false, updatable = false)
    private KeyLot keyLot;

    @Column(name = "delta_cents", nullable = false, updatable = false)
    private Long deltaCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public static KeyLotMovement of(KeyTransaction transaction, KeyLot lot, long deltaCents) {
        KeyLotMovement movement = new KeyLotMovement();
        movement.keyTransaction = transaction;
        movement.keyLot = lot;
        movement.deltaCents = deltaCents;
        return movement;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
