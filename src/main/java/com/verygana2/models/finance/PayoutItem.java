package com.verygana2.models.finance;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "payout_items")
public class PayoutItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id", nullable = false)
    private Payout payout;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "copayment_id", nullable = false)
    private Copayment copayment;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents; // snapshot del monto en el momento del payout
}
