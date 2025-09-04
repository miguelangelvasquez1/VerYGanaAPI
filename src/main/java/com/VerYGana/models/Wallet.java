package com.VerYGana.models;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.Version;

import com.VerYGana.exceptions.InsufficientFundsException;
import com.VerYGana.models.Enums.WalletOwnerType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Data;

@Entity
@Data
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletOwnerType ownerType;

    @Version
    private Long version;

    @Column(nullable = false)
    private Long ownerId;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Column(precision = 15, scale = 2, nullable = false)  
    private BigDecimal blockedBalance = BigDecimal.ZERO;
    
    @Column(nullable = false)
    private ZonedDateTime lastUpdated;
    
    @CreationTimestamp
    private ZonedDateTime createdAt;

    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Transaction> transactions;


    public static Wallet createWallet(Long OwnerId, WalletOwnerType walletOwnerType){
        Wallet wallet = new Wallet();
        wallet.setOwnerId(OwnerId);
        wallet.setOwnerType(walletOwnerType);
        return wallet;
    }

    public boolean isUserWallet() {
        return WalletOwnerType.USER.equals(this.ownerType);
    }
    
    public boolean isAdvertiserWallet() {
        return WalletOwnerType.ADVERTISER.equals(this.ownerType);
    }
    
    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.balance.compareTo(amount) >= 0;
    }
    
    public void addBalance(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.balance = this.balance.add(amount);
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
    
    public void subtractBalance(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (!hasSufficientBalance(amount)) {
            throw new InsufficientFundsException();
        }
        this.balance = this.balance.subtract(amount);
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
    
    public void blockBalance(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.compareTo(this.balance) > 0) {
            throw new InsufficientFundsException("Insufficient available funds to block");
        }
        this.blockedBalance = this.blockedBalance.add(amount);
        this.balance = this.balance.subtract(amount);
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
    
    public void unblockBalance(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (this.blockedBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Cannot unblock more than blocked amount");
        }
        this.blockedBalance = this.blockedBalance.subtract(amount);
        this.balance = this.balance.add(amount);
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }

    @PrePersist
    public void prePersist() {
        if (this.lastUpdated == null) {
            this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        }
        if (this.balance == null) {
            this.balance = BigDecimal.ZERO;
        }
        if (this.blockedBalance == null) {
            this.blockedBalance = BigDecimal.ZERO;
        }
    }
    
    @PreUpdate  
    public void preUpdate() {
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
}

