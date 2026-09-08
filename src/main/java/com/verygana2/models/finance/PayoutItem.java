package com.verygana2.models.finance;

import java.util.UUID;

import com.verygana2.models.marketplace.PurchaseItem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "payout_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id", nullable = false)
    private Payout payout;

    /**
     * El ítem reclamado (PurchaseItemStatus.CLAIMED) que financia esta línea.
     * Único por ítem: un ítem solo puede entrar a un payout una vez — esto es
     * lo que hace idempotente al job diario (ver
     * PayoutServiceImpl.scheduleDailyPayouts / findClaimedWithoutPayout).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_item_id", nullable = false, unique = true)
    private PurchaseItem purchaseItem;

    /** = purchaseItem.netToCommercialCents en el momento de crear este payout. */
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;
}
