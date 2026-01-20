package com.verygana2.models;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.models.enums.PaymentMethod;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transactions", indexes = {
                @Index(name = "idx_wallet_id", columnList = "wallet_id"),
                @Index(name = "idx_wompi_transaction_id", columnList = "wompi_transaction_id"),
                @Index(name = "idx_reference_id", columnList = "reference_id"),
                @Index(name = "idx_transaction_state", columnList = "transaction_state"),
                @Index(name = "idx_created_at", columnList = "created_at")
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class Transaction {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "reference_id", nullable = false, updatable = false)
        private String referenceId; // For external reference

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "wallet_id", nullable = false)
        private Wallet wallet;

        @Enumerated(EnumType.STRING)
        @Column(name = "payment_method")
        private PaymentMethod paymentMethod;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "payment_info_id")
        private PaymentInfo paymentInfo;

        @Enumerated(EnumType.STRING)
        @Column(name = "transaction_type", nullable = false)
        private TransactionType transactionType;

        @Enumerated(EnumType.STRING)
        @Column(name = "transaction_state", nullable = false)
        private TransactionState transactionState;

        @Column(precision = 15, scale = 2)
        private BigDecimal amount;

        @Column(name = "created_at")
        private ZonedDateTime createdAt;

        @Column(name = "completed_at")
        private ZonedDateTime completedAt;

        @Column(name = "updated_at")
        private ZonedDateTime updatedAt;

        // Wompi retorna algo como: "1234-1668097749-23456"
        @Column(name = "wompi_transaction_id", unique = true, length = 100)
        private String wompiTransactionId;

        @Column(name = "wompi_response", columnDefinition = "TEXT")
        private String wompiResponse;

        @Column(name = "payment_method_info", columnDefinition = "TEXT")
        private String paymentMethodInfo; // JSON string

        @Column(name = "error_message", columnDefinition = "TEXT")
        private String errorMessage;

        @Column(name = "ip_address", length = 50)
        private String ipAddress;

        @Column(name = "metadata", columnDefinition = "TEXT")
        private String metadata; // JSON string con device, userAgent, etc.

        @Column(name = "customer_email", length = 255)
        private String customerEmail;

        @Column(name = "customer_name", length = 255)
        private String customerName;

        @Column(name = "payment_url", length = 500)
        private String paymentUrl;

        @PrePersist
        public void prePersist() {
                if (this.referenceId == null) {
                        this.referenceId = UUID.randomUUID().toString();
                }

                if (this.createdAt == null) {
                        this.createdAt = ZonedDateTime.now();
                }

                if (this.updatedAt == null) {
                        this.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
                }
        }

        @PreUpdate
        public void preUpdate() {
                this.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        }

        /**
         * Crear transacción de DEPÓSITO con Wompi
         * Estado inicial SIEMPRE es PENDING
         */
        public static Transaction createWompiDepositTransaction(
                        Wallet wallet,
                        BigDecimal amount,
                        PaymentMethod paymentMethod,
                        String email,
                        String customerName,
                        String ipAddress) {
                String referenceId = "VERYGANA-DEP-" + wallet.getId() + "-" + System.currentTimeMillis();

                return Transaction.builder()
                                .wallet(wallet)
                                .amount(amount)
                                .referenceId(referenceId)
                                .transactionType(TransactionType.DEPOSIT)
                                .paymentMethod(paymentMethod)
                                .transactionState(TransactionState.PENDING) // IMPORTANTE: inicia PENDING
                                .customerEmail(email)
                                .customerName(customerName)
                                .ipAddress(ipAddress)
                                .build();
        }

        /**
         * Actualizar con respuesta de Wompi
         */
        public void updateWithWompiResponse(String wompiTxId, String response, String status) {
                this.wompiTransactionId = wompiTxId;
                this.wompiResponse = response;
                this.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));

                // Mapear status de Wompi a tu enum
                switch (status) {
                        case "APPROVED":
                                this.transactionState = TransactionState.COMPLETED;
                                this.completedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
                                break;
                        case "DECLINED":
                                this.transactionState = TransactionState.FAILED;
                                break;
                        case "PENDING":
                                this.transactionState = TransactionState.PENDING;
                                break;
                        case "VOIDED":
                                this.transactionState = TransactionState.CANCELLED;
                                break;
                        default:
                                this.transactionState = TransactionState.FAILED;
                }
        }

        /**
         * Mapear status de Wompi a tu enum
         */
        // private TransactionState mapWompiStatusToState(String wompiStatus) {
        //         switch (wompiStatus.toUpperCase()) {
        //                 case "APPROVED":
        //                         return TransactionState.COMPLETED;
        //                 case "DECLINED":
        //                         return TransactionState.FAILED;
        //                 case "PENDING":
        //                         return TransactionState.PENDING;
        //                 case "VOIDED":
        //                         return TransactionState.CANCELLED;
        //                 case "ERROR":
        //                         return TransactionState.FAILED;
        //                 default:
        //                         return TransactionState.FAILED;
        //         }
        // }

        /**
         * Marcar como aprobada (desde webhook)
         */
        public void markAsApproved() {
                this.transactionState = TransactionState.COMPLETED;
                this.completedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
                this.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        }

        /**
         * Marcar como rechazada
         */
        public void markAsDeclined(String errorMessage) {
                this.transactionState = TransactionState.FAILED;
                this.errorMessage = errorMessage;
                this.completedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
                this.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        }

        /**
         * Marcar como procesando (PSE/Nequi mientras usuario aprueba)
         */
        public void markAsProcessing() {
                this.transactionState = TransactionState.PROCESSING;
                this.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        }

        /**
         * Guardar información del método de pago (como JSON)
         */
        public void setPaymentMethodInfoFromMap(Map<String, String> info) {
                try {
                        ObjectMapper mapper = new ObjectMapper();
                        this.paymentMethodInfo = mapper.writeValueAsString(info);
                } catch (Exception e) {
                        this.paymentMethodInfo = info.toString();
                }
        }

        /**
         * Convertir monto de pesos a centavos para Wompi
         */
        public Long getAmountInCents() {
                return this.amount.multiply(new BigDecimal("100")).longValue();
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
