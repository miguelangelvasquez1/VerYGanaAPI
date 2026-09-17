package com.verygana2.models.ads;

import java.time.ZonedDateTime;

import com.verygana2.models.userDetails.ConsumerDetails;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "ad_likes",
    indexes = {
        @Index(name = "idx_ad_likes_created_at", columnList = "created_at"),
        @Index(name = "idx_ad_likes_consumer_id", columnList = "consumer_user_id"),
        @Index(name = "idx_ad_likes_ad_id", columnList = "ad_id")
    }
)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class AdLike {

    @EmbeddedId
    private AdLikeId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("consumerId")
    @JoinColumn(nullable = false)
    private ConsumerDetails consumer;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("adId")
    @JoinColumn(nullable = false)
    private Ad ad;

    /**
     * Lo que el anunciante FINANCIÓ por este like (ad.rewardPerLike), en centavos.
     * No es lo que recibió el consumidor: el multiplicador de nivel los separa.
     * Para "cuánto ganó el usuario" usar {@link #creditedAmountCents}.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private Long rewardAmount;

    /**
     * Lo que realmente se ACREDITÓ en la billetera del consumidor, en centavos
     * (rewardAmount × multiplicador de nivel, redondeado).
     *
     * Nullable: las filas anteriores a esta columna no tienen el dato y no se
     * puede reconstruir desde aquí — habría que derivarlo de KeyTransaction.
     */
    @Column(name = "credited_amount")
    private Long creditedAmountCents;

    /**
     * Si el diferencial (financiado − acreditado) de esta fila ya se liquidó en
     * tesorería. Lo pone el job por lotes, no el like: liquidar inline tomaba
     * lock pesimista sobre dos cuentas globales en cada like.
     */
    @Column(name = "issuance_settled", nullable = false)
    @Builder.Default
    private boolean issuanceSettled = false;

    @Column(nullable = false)
    private ZonedDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (id == null) {
            id = new AdLikeId();
        }
        if (consumer != null) {
            id.setConsumerId(consumer.getId());
        }
        if (ad != null) {
            id.setAdId(ad.getId());
        }
    }

    // Constructor de conveniencia
    public AdLike(ConsumerDetails consumer, Ad ad, Long rewardAmount) {
        this.consumer = consumer;
        this.ad = ad;
        this.rewardAmount = rewardAmount;
        this.id = new AdLikeId(consumer.getId(), ad.getId());
        this.createdAt = ZonedDateTime.now();
    }
}
