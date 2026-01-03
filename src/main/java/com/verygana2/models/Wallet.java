package com.verygana2.models;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.Version;

import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.models.enums.WalletStatus;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Data;

@Entity
@Data
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  

    @Version
    private Long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) 
    private User user; 

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Column(precision = 15, scale = 2, nullable = false)  
    private BigDecimal blockedBalance = BigDecimal.ZERO;
    
    @Column(name = "pending_balance", precision = 15, scale = 2, nullable = false)
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    @Column(name = "total_deposited", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalDeposited = BigDecimal.ZERO;

    @Column(name = "total_withdrawn", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalWithdrawn = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status = WalletStatus.ACTIVE;

    @Column(nullable = false)
    private ZonedDateTime lastUpdated;
    
    @CreationTimestamp
    private ZonedDateTime createdAt;

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

        if (this.pendingBalance == null) {
            this.pendingBalance = BigDecimal.ZERO;
        }
        if (this.totalDeposited == null) {
            this.totalDeposited = BigDecimal.ZERO;
        }
        if (this.totalWithdrawn == null) {
            this.totalWithdrawn = BigDecimal.ZERO;
        }
        if (this.status == null) {
            this.status = WalletStatus.ACTIVE;
        }
    }
    
    @PreUpdate  
    public void preUpdate() {
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }


    public static Wallet createWallet(User user){
        Wallet wallet = new Wallet();
        wallet.setUser(user);
        return wallet;
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

    // MÉTODOS NUEVOS para Wompi:
    
    /**
     * Cuando usuario inicia un pago PSE/Nequi que queda PENDING
     */
    public void addPendingBalance(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.pendingBalance = this.pendingBalance.add(amount);
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
    
    /**
     * Cuando Wompi confirma el pago (webhook APPROVED)
     * Mueve de pending a balance disponible
     */
    public void confirmPendingDeposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (this.pendingBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Cannot confirm more than pending amount");
        }
        
        // Restar de pending
        this.pendingBalance = this.pendingBalance.subtract(amount);
        
        // Agregar a balance disponible
        this.balance = this.balance.add(amount);
        this.totalDeposited = this.totalDeposited.add(amount);
        
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
    
    /**
     * Cuando pago es rechazado (webhook DECLINED)
     */
    public void cancelPendingDeposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (this.pendingBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Cannot cancel more than pending amount");
        }
        
        this.pendingBalance = this.pendingBalance.subtract(amount);
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
    
    /**
     * Para depósitos instantáneos (tarjetas que se aprueban de inmediato)
     */
    public void addInstantDeposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.balance = this.balance.add(amount);
        this.totalDeposited = this.totalDeposited.add(amount);
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }

    /**
     * Para retiros instantáneos (tarjetas que se aprueban de inmediato)
     */
    public void addInstantWithdrawal(BigDecimal amount){
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (!hasSufficientBalance(amount)) {
            throw new InsufficientFundsException();
        }
        this.balance = this.balance.subtract(amount);
        this.totalWithdrawn = this.totalWithdrawn.add(amount); // AGREGAR ESTA LÍNEA
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }

    /**
     * Congelar wallet (sospecha de fraude)
     */
    public void freeze() {
        this.status = WalletStatus.FROZEN;
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
    
    /**
     * Descongelar wallet
     */
    public void unfreeze() {
        this.status = WalletStatus.ACTIVE;
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
    
    /**
     * Verificar si wallet está activa
     */
    public boolean isActive() {
        return this.status == WalletStatus.ACTIVE;
    }

}

