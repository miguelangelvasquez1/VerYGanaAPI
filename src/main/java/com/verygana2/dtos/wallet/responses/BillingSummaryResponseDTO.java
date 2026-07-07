package com.verygana2.dtos.wallet.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.finance.WalletStatus;
import com.verygana2.models.finance.plans.Plan.PlanCode;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BillingSummaryResponseDTO {

    private Long balanceCents;
    private WalletStatus walletStatus;
    private Long spentThisMonthCents;
    private Long earnedThisMonthCents;
    private ActivePlan currentPlan;

    @Data
    @Builder
    public static class ActivePlan {
        private String planName;
        private PlanCode planCode;
        private ZonedDateTime endDate;
        private Long daysRemaining;
        private String status;
    }
}
