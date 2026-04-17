package com.verygana2.models.plans;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registro detallado de cada consumo del presupuesto.
 * Permite auditar cómo se gastó el Budget por tipo de interacción.
 *
 * Cada vez que un usuario interactúa con un anuncio o un juego branded,
 * se crea una BudgetTransaction que:
 *  1. Registra el monto descontado del Budget.
 *  2. Clasifica el gasto (publicidad vs recompensa de juego).
 *  3. Vincula la transacción con el evento que la originó (ad view, game session).
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

    @ManyToOne(optional = false)
    private Investment budget;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    /**
     * ID externo del evento que originó el gasto:
     *  - Para AD_VIEW: id de la impresión / visualización del anuncio
     *  - Para GAME_REWARD: id de la sesión de juego
     *  - Para MANUAL_ADJUSTMENT: id del ajuste administrativo
     */
    private String referenceId;

    private String description;

    @Column(nullable = false)
    private ZonedDateTime createdAt;

    public enum TransactionType {
        /** Costo por mostrar un anuncio a un usuario. */
        AD_VIEW,
        /** Recompensa pagada al usuario por completar un juego branded. */
        GAME_REWARD,
        /** Ajuste manual por soporte o corrección administrativa. */
        MANUAL_ADJUSTMENT
    }
}