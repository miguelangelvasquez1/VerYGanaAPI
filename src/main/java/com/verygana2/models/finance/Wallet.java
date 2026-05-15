package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

@Entity
@Table(name = "wallets")
@Data
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false, unique = true)
    private CommercialDetails commercial;

    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Investment> investments = new ArrayList<>();

    @Column(name = "balance_cents", nullable = false)
    private Long balanceCents = 0L;

    @Column(name = "last_updated", nullable = false)
    private ZonedDateTime lastUpdated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        if (this.createdAt == null) this.createdAt = now;
        if (this.lastUpdated == null) this.lastUpdated = now;
        if (this.balanceCents == null) this.balanceCents = 0L;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastUpdated = ZonedDateTime.now(ZoneOffset.UTC);
    }

    public static Wallet createFor(CommercialDetails commercial) {
        Wallet wallet = new Wallet();
        wallet.setCommercial(commercial);
        return wallet;
    }

    public void deposit(Long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        this.balanceCents += amount;
    }

    public void consume(Long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (!hasFundsFor(amount)) throw new InsufficientFundsException();
        this.balanceCents -= amount;
    }

    public boolean hasFundsFor(Long amount) {
        return this.balanceCents >= amount;
    }

    public boolean isExhausted() {
        return this.balanceCents == 0L;
    }
}
