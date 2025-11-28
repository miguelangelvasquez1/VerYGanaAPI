package com.verygana2.models;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

        @Column(name = "reference_id", nullable = false, updatable = false)
        private String referenceId; // For external reference

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "wallet_id", nullable = false)
        private Wallet wallet;

        @Enumerated(EnumType.STRING)
        private PaymentMethod paymentMethod;

        @ManyToOne(fetch = FetchType.LAZY)
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

        public static Transaction createDepositTransaction(Wallet wallet, BigDecimal amount,
                        PaymentMethod paymentMethod) {
                Transaction tx = Transaction.builder().wallet(wallet).amount(amount)
                                .transactionType(TransactionType.DEPOSIT).paymentMethod(paymentMethod).paymentInfo(null)
                                .transactionState(TransactionState.COMPLETED).build();
                return tx;
        }

        public static Transaction createAdLikeRewardSentTransaction(Wallet wallet, BigDecimal amount,
                        String mutualReferenceId) {
                Transaction tx = Transaction.builder().wallet(wallet).amount(amount)
                                .transactionType(TransactionType.POINTS_AD_LIKE_PAID)
                                .paymentMethod(PaymentMethod.WALLET)
                                .paymentInfo(null)
                                .transactionState(TransactionState.COMPLETED).referenceId(mutualReferenceId).build();
                return tx;
        }

        public static Transaction createAdLikeRewardReceivedTransaction(Wallet wallet, BigDecimal amount,
                        String mutualReferenceId) {
                Transaction tx = Transaction.builder().wallet(wallet).amount(amount)
                                .transactionType(TransactionType.POINTS_AD_LIKE_REWARD)
                                .transactionState(TransactionState.COMPLETED).referenceId(mutualReferenceId).build();
                return tx;
        }

        public static Transaction createReferralRewardTransaction(Wallet wallet, BigDecimal amount,
                        String mutualReferenceId) {
                Transaction tx = Transaction.builder().wallet(wallet).amount(amount)
                                .transactionType(TransactionType.POINTS_REFERRAL_BONUS)
                                .transactionState(TransactionState.COMPLETED).referenceId(mutualReferenceId).build();
                return tx;
        }

        public static Transaction createRafflePrizeTransaction(Wallet wallet, Long ownerId, BigDecimal amount) {
                Transaction tx = Transaction.builder().wallet(wallet).amount(amount)
                                .transactionType(TransactionType.RAFFLE_PRIZE)
                                .transactionState(TransactionState.COMPLETED).build();
                return tx;
        }

        public static Transaction createWithdrawalTransaction(Wallet wallet, BigDecimal amount,
                        PaymentMethod paymentMethod) {
                Transaction tx = Transaction.builder().wallet(wallet).amount(amount)
                                .transactionType(TransactionType.WITHDRAWAL).paymentMethod(paymentMethod)
                                .paymentInfo(null)
                                .transactionState(TransactionState.COMPLETED).build();
                return tx;
        }

        public static Transaction createWholePurchaseTransaction(Wallet wallet, BigDecimal amount,
                        String mutualReferenceId) {
                Transaction tx = Transaction.builder().wallet(wallet).amount(amount)
                                .transactionType(TransactionType.WHOLE_PURCHASE).paymentMethod(PaymentMethod.WALLET)
                                .paymentInfo(null)
                                .transactionState(TransactionState.COMPLETED).referenceId(mutualReferenceId).build();
                return tx;
        }

        public static Transaction createProductSaleTransaction(Wallet wallet, BigDecimal amount,
                        String referenceId) {
                Transaction tx = Transaction.builder().wallet(wallet).amount(amount)
                                .transactionType(TransactionType.PRODUCT_SALE)
                                .transactionState(TransactionState.COMPLETED).referenceId(referenceId).build();
                return tx;
        }

        public static Transaction createRaffleParticipationTransaction(Wallet wallet, BigDecimal amount) {
                Transaction tx = Transaction.builder().wallet(wallet).amount(amount)
                                .transactionType(TransactionType.RAFFLE_PARTICIPATION)
                                .paymentMethod(PaymentMethod.WALLET)
                                .paymentInfo(null).transactionState(TransactionState.COMPLETED).build();
                return tx;
        }

        public static Transaction createDataRechargeTransaction(Wallet wallet, BigDecimal amount) {
                Transaction tx = Transaction.builder().wallet(wallet).amount(amount)
                                .transactionType(TransactionType.DATA_RECHARGE).paymentMethod(PaymentMethod.WALLET)
                                .paymentInfo(null)
                                .transactionState(TransactionState.COMPLETED).build();
                return tx;
        }

        public static Transaction createGiftSentTransaction(Wallet wallet, BigDecimal amount,
                        String mutualReferenceId) {
                Transaction tx = Transaction.builder().wallet(wallet).amount(amount)
                                .transactionType(TransactionType.GIFT_TRANSFER_SENT).paymentMethod(PaymentMethod.WALLET)
                                .paymentInfo(null).transactionState(TransactionState.COMPLETED)
                                .referenceId(mutualReferenceId).build();
                return tx;
        }

        public static Transaction createGiftReceivedTransaction(Wallet wallet, BigDecimal amount,
                        String mutualReferenceId) {
                Transaction tx = Transaction.builder().wallet(wallet).amount(amount)
                                .transactionType(TransactionType.GIFT_TRANSFER_RECEIVED)
                                .transactionState(TransactionState.COMPLETED)
                                .referenceId(mutualReferenceId).build();
                return tx;
        }

}
