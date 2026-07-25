package com.verygana2.models.marketplace;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Producto de un comercial aliado (Básico/Estándar) que un comercial Premium eligió
 * promocionar en el popup final de sus juegos brandeados, ya que Premium no vende
 * productos propios (ver {@code CAN_SELL_DIRECTLY} y {@code CAN_PROMOTE_ALLY_PRODUCTS}).
 */
@Entity
@Table(name = "ally_product_promotions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"premium_commercial_id", "product_id"}),
    indexes = {
        @Index(name = "idx_ally_promo_premium_commercial", columnList = "premium_commercial_id"),
        @Index(name = "idx_ally_promo_product", columnList = "product_id")
    })
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AllyProductPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Comercial Premium que promociona el producto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "premium_commercial_id", nullable = false)
    private CommercialDetails premiumCommercial;

    /** Producto del comercial aliado (Básico/Estándar) siendo promocionado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
