package com.VerYGana.models;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.VerYGana.models.Enums.TransactionState;
import com.VerYGana.models.Enums.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import lombok.Data;

@Entity
@Data
public class Transaction { // Future consideration: currency column, hacer esto con stripe
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String referenceId; // For external reference

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @ManyToOne
    private PayoutMethod payoutMethod;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    private TransactionState transactionState;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(name= "created_at", columnDefinition = "DATETIME")
    private ZonedDateTime createdAt;
    @Column(name= "completed_at", columnDefinition = "DATETIME")
    private ZonedDateTime completedAt;

    @PrePersist
    public void prePersist() {
        if (this.referenceId == null) {
            this.referenceId = UUID.randomUUID().toString();
        }

        if (this.createdAt == null) {
            this.createdAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        }
    }


    public static Transaction createDepositTransaction(Long walletId, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setTransactionType(TransactionType.DEPOSIT);
        tx.setPayoutMethod(null);
        tx.setTransactionState(TransactionState.COMPLETED);
        return tx;
    }

    public static Transaction createAdLikeRewardTransaction(Long walletId, BigDecimal amount,
            String mutualReferenceId) {
        Transaction tx = new Transaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setTransactionType(TransactionType.POINTS_AD_LIKE_REWARD);
        tx.setPayoutMethod(null);
        tx.setTransactionState(TransactionState.COMPLETED);
        tx.setReferenceId(mutualReferenceId);
        return tx;
    }

    public static Transaction createReferralRewardTransaction(Long walletId, BigDecimal amount,
            String mutualReferenceId) {
        Transaction tx = new Transaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setTransactionType(TransactionType.POINTS_REFERRAL_BONUS);
        tx.setPayoutMethod(null);
        tx.setTransactionState(TransactionState.COMPLETED);
        tx.setReferenceId(mutualReferenceId);
        return tx;
    }

    public static Transaction createRafflePrizeTransaction(Long walletId, Long ownerId, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setTransactionType(TransactionType.RAFFLE_PRIZE);
        tx.setPayoutMethod(null);
        tx.setTransactionState(TransactionState.COMPLETED);
        return tx;
    }

    public static Transaction createWithdrawalTransaction(Long walletId, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setTransactionType(TransactionType.WITHDRAWAL);
        tx.setPayoutMethod(null);
        tx.setTransactionState(TransactionState.COMPLETED);
        return tx;
    }

    public static Transaction createProductPurchaseTransaction(Long walletId, BigDecimal amount,
            String mutualReferenceId) {
        Transaction tx = new Transaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setTransactionType(TransactionType.PRODUCT_PURCHASE);
        tx.setPayoutMethod(null);
        tx.setTransactionState(TransactionState.COMPLETED);
        tx.setReferenceId(mutualReferenceId);
        return tx;
    }

    public static Transaction createProductSaleTransaction(Long walletId, BigDecimal amount,
            String referenceId) {
        Transaction tx = new Transaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setTransactionType(TransactionType.PRODUCT_SALE);
        tx.setPayoutMethod(null);
        tx.setTransactionState(TransactionState.COMPLETED);
        tx.setReferenceId(referenceId);
        return tx;
    }

    public static Transaction createRaffleParticipationTransaction(Long walletId, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setTransactionType(TransactionType.RAFFLE_PARTICIPATION);
        tx.setPayoutMethod(null);
        tx.setTransactionState(TransactionState.COMPLETED);
        return tx;
    }

    public static Transaction createDataRechargeTransaction(Long walletId, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setTransactionType(TransactionType.DATA_RECHARGE);
        tx.setPayoutMethod(null);
        tx.setTransactionState(TransactionState.COMPLETED);
        return tx;
    }

    public static Transaction createGiftSentTransaction(Long walletId, BigDecimal amount,
            String mutualReferenceId) {
        Transaction tx = new Transaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setTransactionType(TransactionType.GIFT_TRANSFER_SENT);
        tx.setPayoutMethod(null);
        tx.setTransactionState(TransactionState.COMPLETED);
        tx.setReferenceId(mutualReferenceId);
        return tx;
    }

    public static Transaction createGiftReceivedTransaction(Long walletId, BigDecimal amount,
            String mutualReferenceId) {
        Transaction tx = new Transaction();
        tx.setWalletId(walletId);
        tx.setAmount(amount);
        tx.setTransactionType(TransactionType.GIFT_TRANSFER_RECEIVED);
        tx.setPayoutMethod(null);
        tx.setTransactionState(TransactionState.COMPLETED);
        tx.setReferenceId(mutualReferenceId);
        return tx;
    }

}

// Posible cambio con mejores practicas
/*
 * @Entity
 * 
 * @Table(name = "transactions", indexes = {
 * 
 * @Index(name = "idx_transaction_wallet", columnList = "wallet_id"),
 * 
 * @Index(name = "idx_transaction_reference", columnList = "reference_id"),
 * 
 * @Index(name = "idx_transaction_created_at", columnList = "created_at"),
 * 
 * @Index(name = "idx_transaction_type_state", columnList =
 * "transaction_type, transaction_state")
 * })
 * 
 * @Data
 * 
 * @NoArgsConstructor
 * 
 * @AllArgsConstructor
 * 
 * @Builder
 * 
 * @EntityListeners(AuditingEntityListener.class)
 * public class Transaction {
 * 
 * @Id
 * 
 * @GeneratedValue(strategy = GenerationType.IDENTITY)
 * private Long id;
 * 
 * @Column(name = "reference_id", unique = true, nullable = false,
 * updatable = false, length = 36)
 * private String referenceId;
 * 
 * @ManyToOne(fetch = FetchType.LAZY, optional = false)
 * 
 * @JoinColumn(name = "wallet_id", nullable = false)
 * private Wallet wallet;
 * 
 * @ManyToOne(fetch = FetchType.LAZY)
 * 
 * @JoinColumn(name = "payout_method_id")
 * private PayoutMethod payoutMethod;
 * 
 * @Enumerated(EnumType.STRING)
 * 
 * @Column(name = "transaction_type", nullable = false, length = 50)
 * private TransactionType transactionType;
 * 
 * @Enumerated(EnumType.STRING)
 * 
 * @Column(name = "transaction_state", nullable = false, length = 50)
 * 
 * @Builder.Default
 * private TransactionState transactionState = TransactionState.PENDING;
 * 
 * @Column(name = "amount", precision = 15, scale = 2, nullable = false)
 * private BigDecimal amount;
 * 
 * @Column(name = "description", length = 500)
 * private String description;
 * 
 * // Auditoría automática con Spring Data JPA
 * 
 * @CreationTimestamp
 * 
 * @Column(name = "created_at", nullable = false, updatable = false)
 * private LocalDateTime createdAt;
 * 
 * @Column(name = "completed_at")
 * private LocalDateTime completedAt;
 * 
 * @UpdateTimestamp
 * 
 * @Column(name = "updated_at")
 * private LocalDateTime updatedAt;
 * 
 * // Campos adicionales para auditoría (opcional)
 * 
 * @CreatedBy
 * 
 * @Column(name = "created_by", length = 100, updatable = false)
 * private String createdBy;
 * 
 * @LastModifiedBy
 * 
 * @Column(name = "modified_by", length = 100)
 * private String modifiedBy;
 * 
 * // Optimistic locking
 * 
 * @Version
 * private Long version;
 * 
 * // Soft delete (opcional pero recomendado para transacciones)
 * 
 * @Column(name = "deleted", nullable = false)
 * 
 * @Builder.Default
 * private Boolean deleted = false;
 * 
 * @Column(name = "deleted_at")
 * private LocalDateTime deletedAt;
 * 
 * // Métodos de negocio
 * public boolean isPending() {
 * return TransactionState.PENDING.equals(this.transactionState);
 * }
 * 
 * public boolean isCompleted() {
 * return TransactionState.COMPLETED.equals(this.transactionState);
 * }
 * 
 * public boolean isFailed() {
 * return TransactionState.FAILED.equals(this.transactionState);
 * }
 * 
 * public boolean isCancelled() {
 * return TransactionState.CANCELLED.equals(this.transactionState);
 * }
 * 
 * public void markAsCompleted() {
 * this.transactionState = TransactionState.COMPLETED;
 * this.completedAt = LocalDateTime.now();
 * }
 * 
 * public void markAsFailed() {
 * this.transactionState = TransactionState.FAILED;
 * }
 * 
 * public void markAsCancelled() {
 * this.transactionState = TransactionState.CANCELLED;
 * }
 * 
 * public boolean isDebit() {
 * return this.amount.compareTo(BigDecimal.ZERO) < 0;
 * }
 * 
 * public boolean isCredit() {
 * return this.amount.compareTo(BigDecimal.ZERO) > 0;
 * }
 * 
 * // Validaciones
 * public void validateAmount() {
 * if (amount == null) {
 * throw new IllegalArgumentException("Transaction amount cannot be null");
 * }
 * if (amount.compareTo(BigDecimal.ZERO) == 0) {
 * throw new IllegalArgumentException("Transaction amount cannot be zero");
 * }
 * }
 * 
 * @PrePersist
 * public void prePersist() {
 * if (this.referenceId == null) {
 * this.referenceId = UUID.randomUUID().toString();
 * }
 * validateAmount();
 * 
 * // Inicializar valores por defecto
 * if (this.transactionState == null) {
 * this.transactionState = TransactionState.PENDING;
 * }
 * if (this.deleted == null) {
 * this.deleted = false;
 * }
 * }
 * 
 * @PreUpdate
 * public void preUpdate() {
 * validateAmount();
 * }
 * 
 * // Soft delete
 * public void softDelete() {
 * this.deleted = true;
 * this.deletedAt = LocalDateTime.now();
 * }
 * 
 * public boolean isDeleted() {
 * return Boolean.TRUE.equals(this.deleted);
 * }
 * 
 * // Para debugging y logging
 * 
 * @Override
 * public String toString() {
 * return "Transaction{" +
 * "id=" + id +
 * ", referenceId='" + referenceId + '\'' +
 * ", transactionType=" + transactionType +
 * ", transactionState=" + transactionState +
 * ", amount=" + amount +
 * ", createdAt=" + createdAt +
 * '}';
 * }
 * }
 */
