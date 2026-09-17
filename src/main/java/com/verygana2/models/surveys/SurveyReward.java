package com.verygana2.models.surveys;

import java.time.ZonedDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "survey_rewards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private SurveySession session;

    /**
     * Lo que el anunciante FINANCIÓ por esta encuesta (preguntas × precio), en
     * centavos. No es lo que recibió el consumidor: el multiplicador de nivel
     * los separa. Para "cuánto ganó el usuario" usar {@link #creditedAmountCents}.
     */
    @Column(name = "amount", nullable = false)
    private Long amountCents;

    /**
     * Lo que realmente se ACREDITÓ en la billetera del consumidor, en centavos
     * (amountCents × multiplicador de nivel, redondeado).
     *
     * Nullable: las filas anteriores a esta columna no tienen el dato.
     */
    @Column(name = "credited_amount")
    private Long creditedAmountCents;

    /**
     * Si el diferencial (financiado − acreditado) de esta fila ya se liquidó en
     * tesorería. Lo pone el job por lotes, no el envío de la encuesta.
     */
    @Column(name = "issuance_settled", nullable = false)
    @Builder.Default
    private boolean issuanceSettled = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RewardStatus status = RewardStatus.PENDING;

    @CreationTimestamp
    @Column(name = "granted_at", updatable = false)
    private ZonedDateTime grantedAt;

    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    public enum RewardStatus {
        PENDING,
        PROCESSED,
        /**
         * Ya no se escribe: si el crédito falla, la transacción de submitSurvey
         * revierte entera y no queda fila. Se conserva el valor porque puede
         * existir en filas históricas y nada lo reprocesa.
         */
        FAILED
    }
}
