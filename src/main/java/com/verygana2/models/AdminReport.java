package com.verygana2.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.verygana2.models.enums.AdminActionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Data;

@Entity
@Data
@Builder
@Table(name = "admin_reports")
public class AdminReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_Id", nullable = false)
    private Long userId;

    @Column(name = "action_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AdminActionType actionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "previous_balance", precision = 19, scale = 2)
    private BigDecimal previousBalance;

    @Column(name = "new_balance", precision = 19, scale = 2)
    private BigDecimal newBalance;

    @Column(name = "previous_blocked_balance", precision = 19, scale = 2)
    private BigDecimal previousBlockedBalance;

    @Column(name = "new_blocked_balance", precision = 19, scale = 2)
    private BigDecimal newBlockedBalance;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static AdminReport createBlockBalanceReport(Long userId, BigDecimal amount, String reason,
            BigDecimal previousBalance, BigDecimal newBalance,
            BigDecimal previousBlockedBalance, BigDecimal newBlockedBalance) {

        AdminReport report = builder().userId(userId).amount(amount).reason(reason).previousBalance(previousBalance)
                .newBalance(newBalance).previousBlockedBalance(previousBlockedBalance)
                .newBlockedBalance(newBlockedBalance).actionType(AdminActionType.BALANCE_BLOCK)
                .createdAt(LocalDateTime.now()).build();

        return report;

    }

    public static AdminReport createUnblockBalanceReport(Long userId, BigDecimal amount, String reason,
            BigDecimal previousBalance, BigDecimal newBalance,
            BigDecimal previousBlockedBalance, BigDecimal newBlockedBalance) {

        AdminReport report = builder().userId(userId).amount(amount).reason(reason).previousBalance(previousBalance)
                .newBalance(newBalance).previousBlockedBalance(previousBlockedBalance)
                .newBlockedBalance(newBlockedBalance).actionType(AdminActionType.BALANCE_UNBLOCK)
                .createdAt(LocalDateTime.now()).build();

        return report;
    }

    public static AdminReport createBalanceAdjustmentReport(Long userId, BigDecimal amount, String reason,
            BigDecimal previousBalance, BigDecimal newBalance) {

        AdminReport report = builder().userId(userId).amount(amount).reason(reason).previousBalance(previousBalance)
                .newBalance(newBalance).actionType(AdminActionType.BALANCE_ADJUSTMENT).createdAt(LocalDateTime.now())
                .build();

        return report;

    }

}
