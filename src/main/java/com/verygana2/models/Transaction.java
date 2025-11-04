package com.verygana2.models;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;
import com.verygana2.models.enums.products.PaymentMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class Transaction { // Future consideration: currency column, hacer esto con stripe
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false, updatable = false)
        private String referenceId; // For external reference

        @Column(name = "wallet_id", nullable = false)
        private Long walletId;

        @Enumerated(EnumType.STRING)
        private PaymentMethod paymentMethod;

        @ManyToOne
        private PaymentInfo paymentInfo;

        @Enumerated(EnumType.STRING)
        private TransactionType transactionType;

        @Enumerated(EnumType.STRING)
        private TransactionState transactionState;

        @Column(precision = 15, scale = 2)
        private BigDecimal amount;
        @Column(name = "created_at")
        private ZonedDateTime createdAt;
        @Column(name = "completed_at")
        private ZonedDateTime completedAt;

        @PrePersist
        public void prePersist() {
                if (this.referenceId == null) {
                        this.referenceId = UUID.randomUUID().toString();
                }

                if (this.createdAt == null) {
                        this.createdAt = ZonedDateTime.now();
                }
        }

        public static Transaction createDepositTransaction(Long walletId, BigDecimal amount,
                        PaymentMethod paymentMethod) {
                Transaction tx = Transaction.builder().walletId(walletId).amount(amount)
                                .transactionType(TransactionType.DEPOSIT).paymentMethod(paymentMethod).paymentInfo(null)
                                .transactionState(TransactionState.COMPLETED).build();
                return tx;
        }

        public static Transaction createAdLikeRewardSentTransaction(Long walletId, BigDecimal amount,
                        String mutualReferenceId) {
                Transaction tx = Transaction.builder().walletId(walletId).amount(amount)
                                .transactionType(TransactionType.POINTS_AD_LIKE_PAID)
                                .paymentMethod(PaymentMethod.WALLET)
                                .paymentInfo(null)
                                .transactionState(TransactionState.COMPLETED).referenceId(mutualReferenceId).build();
                return tx;
        }

        public static Transaction createAdLikeRewardReceivedTransaction(Long walletId, BigDecimal amount,
                        String mutualReferenceId) {
                Transaction tx = Transaction.builder().walletId(walletId).amount(amount)
                                .transactionType(TransactionType.POINTS_AD_LIKE_REWARD)
                                .transactionState(TransactionState.COMPLETED).referenceId(mutualReferenceId).build();
                return tx;
        }

        public static Transaction createReferralRewardTransaction(Long walletId, BigDecimal amount,
                        String mutualReferenceId) {
                Transaction tx = Transaction.builder().walletId(walletId).amount(amount)
                                .transactionType(TransactionType.POINTS_REFERRAL_BONUS)
                                .transactionState(TransactionState.COMPLETED).referenceId(mutualReferenceId).build();
                return tx;
        }

        public static Transaction createRafflePrizeTransaction(Long walletId, Long ownerId, BigDecimal amount) {
                Transaction tx = Transaction.builder().walletId(walletId).amount(amount)
                                .transactionType(TransactionType.RAFFLE_PRIZE)
                                .transactionState(TransactionState.COMPLETED).build();
                return tx;
        }

        public static Transaction createWithdrawalTransaction(Long walletId, BigDecimal amount,
                        PaymentMethod paymentMethod) {
                Transaction tx = Transaction.builder().walletId(walletId).amount(amount)
                                .transactionType(TransactionType.WITHDRAWAL).paymentMethod(paymentMethod)
                                .paymentInfo(null)
                                .transactionState(TransactionState.COMPLETED).build();
                return tx;
        }

        public static Transaction createWholePurchaseTransaction(Long walletId, BigDecimal amount,
                        String mutualReferenceId) {
                Transaction tx = Transaction.builder().walletId(walletId).amount(amount)
                                .transactionType(TransactionType.WHOLE_PURCHASE).paymentMethod(PaymentMethod.WALLET)
                                .paymentInfo(null)
                                .transactionState(TransactionState.COMPLETED).referenceId(mutualReferenceId).build();
                return tx;
        }

        public static Transaction createWholePurchaseRefundTransaction(Long walletId, BigDecimal amount,
                        String refundReferenceId) {
                Transaction tx = Transaction.builder().walletId(walletId)
                                .transactionType(TransactionType.WHOLE_PURCHASE_CANCELED)
                                .amount(amount)
                                .transactionState(TransactionState.COMPLETED).referenceId(refundReferenceId).build();
                return tx;
        }

        public static Transaction createProductSaleTransaction(Long walletId, BigDecimal amount,
                        String referenceId) {
                Transaction tx = Transaction.builder().walletId(walletId).amount(amount)
                                .transactionType(TransactionType.PRODUCT_SALE)
                                .transactionState(TransactionState.COMPLETED).referenceId(referenceId).build();
                return tx;
        }

        public static Transaction createProductSaleRefundTransaction(Long walletId, BigDecimal amount,
                        String refundReferenceId) {
                Transaction tx = Transaction.builder().walletId(walletId)
                                .transactionType(TransactionType.PRODUCT_SALE_CANCELED)
                                .amount(amount)
                                .transactionState(TransactionState.COMPLETED).referenceId(refundReferenceId).build();
                return tx;
        }

        public static Transaction createProductPurchaseRefundTransaction(Long walletId, BigDecimal amount,
                        String refundReferenceId) {
                Transaction tx = Transaction.builder().walletId(walletId)
                                .transactionType(TransactionType.PRODUCT_PURCHASE_CANCELED)
                                .amount(amount)
                                .transactionState(TransactionState.COMPLETED).referenceId(refundReferenceId).build();
                return tx;
        }

        public static Transaction createRaffleParticipationTransaction(Long walletId, BigDecimal amount) {
                Transaction tx = Transaction.builder().walletId(walletId).amount(amount)
                                .transactionType(TransactionType.RAFFLE_PARTICIPATION)
                                .paymentMethod(PaymentMethod.WALLET)
                                .paymentInfo(null).transactionState(TransactionState.COMPLETED).build();
                return tx;
        }

        public static Transaction createDataRechargeTransaction(Long walletId, BigDecimal amount) {
                Transaction tx = Transaction.builder().walletId(walletId).amount(amount)
                                .transactionType(TransactionType.DATA_RECHARGE).paymentMethod(PaymentMethod.WALLET)
                                .paymentInfo(null)
                                .transactionState(TransactionState.COMPLETED).build();
                return tx;
        }

        public static Transaction createGiftSentTransaction(Long walletId, BigDecimal amount,
                        String mutualReferenceId) {
                Transaction tx = Transaction.builder().walletId(walletId).amount(amount)
                                .transactionType(TransactionType.GIFT_TRANSFER_SENT).paymentMethod(PaymentMethod.WALLET)
                                .paymentInfo(null).transactionState(TransactionState.COMPLETED)
                                .referenceId(mutualReferenceId).build();
                return tx;
        }

        public static Transaction createGiftReceivedTransaction(Long walletId, BigDecimal amount,
                        String mutualReferenceId) {
                Transaction tx = Transaction.builder().walletId(walletId).amount(amount)
                                .transactionType(TransactionType.GIFT_TRANSFER_RECEIVED)
                                .transactionState(TransactionState.COMPLETED)
                                .referenceId(mutualReferenceId).build();
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
