package com.verygana2.models.finance.plans;

import java.time.ZonedDateTime;

import com.verygana2.models.finance.Wallet;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registro de cada consumo del presupuesto publicitario.
 * Forma el ledger de salidas junto con Investment (entradas) sobre el Wallet.
 */
@Entity
@Table(name = "budget_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    /**
     * ID externo del evento que originó el gasto:
     *  - AD_VIEW: id de la impresión / visualización del anuncio
     *  - GAME_REWARD: id de la sesión de juego
     *  - MANUAL_ADJUSTMENT: id del ajuste administrativo
     */
    private String referenceId;

    private String description;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    public enum TransactionType {
        AD_VIEW,
        GAME_REWARD,
        MANUAL_ADJUSTMENT
    }
}
