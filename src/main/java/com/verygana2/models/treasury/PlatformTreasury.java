package com.verygana2.models.treasury;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.verygana2.exceptions.InsufficientFundsException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "platform_treasury")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformTreasury {
    
    @Id
    private Long id = 1L;  // ✅ Siempre será 1 (singleton)
    
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal reservedForWithdrawals = BigDecimal.ZERO;
    
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal availableBalance = BigDecimal.ZERO;
    
    @Version
    private Long version;  // Para optimistic locking
    
    @Column(nullable = false)
    private ZonedDateTime lastUpdated;
    
    @CreationTimestamp
    private ZonedDateTime createdAt;
    
    // ===== MÉTODOS DE NEGOCIO =====
    
    public void addCommission(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.balance = this.balance.add(amount);
        updateAvailableBalance();
    }

    public void substractBalance(BigDecimal amount){
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (amount.compareTo(availableBalance) > 0) {
            throw new IllegalArgumentException("Insufficient available balance in treasury");
        }

        this.balance = this.balance.subtract(amount);
        updateAvailableBalance();
    }
    
    public void addRealMoneyDeposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.balance = this.balance.add(amount);
        updateAvailableBalance();
    }
    
    public void reserveForWithdrawal(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.compareTo(this.availableBalance) > 0) {
            throw new InsufficientFundsException("Insufficient available balance in treasury");
        }
        this.reservedForWithdrawals = this.reservedForWithdrawals.add(amount);
        updateAvailableBalance();
    }
    
    public void completeWithdrawal(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.compareTo(this.reservedForWithdrawals) > 0) {
            throw new IllegalArgumentException("Cannot complete more than reserved amount");
        }
        this.balance = this.balance.subtract(amount);
        this.reservedForWithdrawals = this.reservedForWithdrawals.subtract(amount);
        updateAvailableBalance();
    }
    
    public void cancelWithdrawalReservation(BigDecimal amount) {
        if (amount.compareTo(this.reservedForWithdrawals) > 0) {
            throw new IllegalArgumentException("Cannot cancel more than reserved amount");
        }
        this.reservedForWithdrawals = this.reservedForWithdrawals.subtract(amount);
        updateAvailableBalance();
    }
    
    private void updateAvailableBalance() {
        this.availableBalance = this.balance.subtract(this.reservedForWithdrawals);
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
    
    @PrePersist
    protected void onCreate() {
        if (this.lastUpdated == null) {
            this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
}
