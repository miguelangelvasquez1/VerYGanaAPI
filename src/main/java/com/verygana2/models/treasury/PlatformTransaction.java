package com.verygana2.models.treasury;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.PlatformTransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "platform_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformTransaction {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private PlatformTransactionType type;

        @Column(precision = 15, scale = 2, nullable = false)
        private BigDecimal amount;

        @Column
        private String referenceId;

        @Column(length = 500)
        private String description;

        @Column(name = "balance_after", precision = 15, scale = 2, nullable = false)
        private BigDecimal balanceAfter;

        @Column(name = "available_balance_after", precision = 15, scale = 2)
        private BigDecimal availableBalanceAfter;

        @Column(nullable = false)
        private ZonedDateTime createdAt;

        @PrePersist
        public void onCreate(){
                this.createdAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        }

        /**
         * Comisión por venta de producto
         */
        public static PlatformTransaction createProductsSaleCommission(
                        BigDecimal amount,
                        String referenceId,
                        String description,
                        BigDecimal updatedBalance,
                        BigDecimal updatedAvailableBalance) {
                return PlatformTransaction.builder()
                                .type(PlatformTransactionType.COMMISSION_PRODUCTS_SALE)
                                .amount(amount)
                                .referenceId(referenceId)
                                .description(description)
                                .balanceAfter(updatedBalance)
                                .availableBalanceAfter(updatedAvailableBalance)
                                .build();
        }

        public static PlatformTransaction createProductSaleRefund(
                        BigDecimal totalCommissionToRefund, String RefundReferenceId, String reason,
                        BigDecimal updatedBalance,
                        BigDecimal updatedAvailableBalance) {
                return PlatformTransaction.builder().type(PlatformTransactionType.PRODUCT_SALE_CANCELED)
                                .amount(totalCommissionToRefund).referenceId(RefundReferenceId).description(reason)
                                .balanceAfter(updatedBalance).availableBalanceAfter(updatedAvailableBalance).build();
        }

        /**
         * Comisión por creación de rifa
         */
        public static PlatformTransaction createRaffleCommission(
                        BigDecimal amount,
                        String referenceId,
                        String description,
                        BigDecimal updatedBalance,
                        BigDecimal updatedAvailableBalance) {
                return PlatformTransaction.builder()
                                .type(PlatformTransactionType.COMMISSION_RAFFLE)
                                .amount(amount)
                                .referenceId(referenceId)
                                .description(description)
                                .balanceAfter(updatedBalance)
                                .availableBalanceAfter(updatedAvailableBalance)
                                .build();
        }

        /**
         * Comisión por publicación de anuncio
         */
        public static PlatformTransaction createAdCommission(
                        BigDecimal amount,
                        String referenceId,
                        String description,
                        BigDecimal updatedBalance,
                        BigDecimal updatedAvailableBalance) {
                return PlatformTransaction.builder()
                                .type(PlatformTransactionType.COMMISSION_AD)
                                .amount(amount)
                                .referenceId(referenceId)
                                .description(description)
                                .balanceAfter(updatedBalance)
                                .availableBalanceAfter(updatedAvailableBalance)
                                .build();
        }

        /**
         * Entrada de dinero real (usuario recargó)
         */
        public static PlatformTransaction createRealMoneyDeposit(
                        BigDecimal amount,
                        String paymentReference,
                        String description,
                        BigDecimal updatedBalance,
                        BigDecimal updatedAvailableBalance) {
                return PlatformTransaction.builder()
                                .type(PlatformTransactionType.REAL_MONEY_DEPOSIT)
                                .amount(amount)
                                .referenceId(paymentReference)
                                .description(description)
                                .balanceAfter(updatedBalance)
                                .availableBalanceAfter(updatedAvailableBalance)
                                .build();
        }

        /**
         * Reserva para retiro pendiente
         */
        public static PlatformTransaction createWithdrawalReservation(
                        BigDecimal amount,
                        String withdrawalReference,
                        String description,
                        BigDecimal updatedBalance,
                        BigDecimal updatedAvailableBalance) {
                return PlatformTransaction.builder()
                                .type(PlatformTransactionType.WITHDRAWAL_RESERVED)
                                .amount(amount.negate()) // ⚠️ Negativo porque es reserva
                                .referenceId(withdrawalReference)
                                .description(description)
                                .balanceAfter(updatedBalance)
                                .availableBalanceAfter(updatedAvailableBalance)
                                .build();
        }

        /**
         * Retiro completado (dinero salió)
         */
        public static PlatformTransaction createWithdrawalCompleted(
                        BigDecimal amount,
                        String withdrawalReference,
                        String description,
                        BigDecimal updatedBalance,
                        BigDecimal updatedAvailableBalance) {
                return PlatformTransaction.builder()
                                .type(PlatformTransactionType.WITHDRAWAL_COMPLETED)
                                .amount(amount.negate()) // ⚠️ Negativo porque sale dinero
                                .referenceId(withdrawalReference)
                                .description(description)
                                .balanceAfter(updatedBalance)
                                .availableBalanceAfter(updatedAvailableBalance)
                                .build();
        }

        /**
         * Cancelación de retiro
         */
        public static PlatformTransaction createWithdrawalCancellation(
                        BigDecimal amount,
                        String withdrawalReference,
                        String description,
                        BigDecimal updatedBalance,
                        BigDecimal updatedAvailableBalance) {
                return PlatformTransaction.builder()
                                .type(PlatformTransactionType.WITHDRAWAL_CANCELLED)
                                .amount(amount) // ⚠️ Positivo porque se libera la reserva
                                .referenceId(withdrawalReference)
                                .description(description)
                                .balanceAfter(updatedBalance)
                                .availableBalanceAfter(updatedAvailableBalance)
                                .build();
        }

        /**
         * Transferencia manual (ajustes contables)
         */
        public static PlatformTransaction createManualAdjustment(
                        BigDecimal amount,
                        String reason,
                        BigDecimal updatedBalance,
                        BigDecimal updatedAvailableBalance) {
                return PlatformTransaction.builder()
                                .type(PlatformTransactionType.MANUAL_ADJUSTMENT)
                                .amount(amount)
                                .referenceId("MANUAL-" + UUID.randomUUID())
                                .description(reason)
                                .balanceAfter(updatedBalance)
                                .availableBalanceAfter(updatedAvailableBalance)
                                .build();
        }

        public static PlatformTransaction createRefferalPromotion(BigDecimal amount, String referenceId, String description,
                        BigDecimal updatedBalance, BigDecimal updatedAvailableBalance) {
                return PlatformTransaction.builder().type(PlatformTransactionType.REFERRAL_PROMOTION).amount(amount)
                                .referenceId(referenceId).description(description).balanceAfter(updatedBalance)
                                .availableBalanceAfter(updatedAvailableBalance).build();
        }
}
