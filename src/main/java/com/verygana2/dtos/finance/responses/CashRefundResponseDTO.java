package com.verygana2.dtos.finance.responses;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.finance.CashRefundStatus;
import com.verygana2.models.finance.PurchaseItemCashRefund.BankAccountType;
import com.verygana2.models.finance.PurchaseItemCashRefund.DocType;

import lombok.Data;

@Data
public class CashRefundResponseDTO {
    private UUID id;
    private Long purchaseItemId;
    private Long amountCents;
    private CashRefundStatus status;

    private String accountHolderName;
    private String accountHolderDoc;
    private DocType accountHolderDocType;
    private String bankName;
    private String accountNumber;
    private BankAccountType accountType;

    private ZonedDateTime createdAt;
    private ZonedDateTime bankDetailsSubmittedAt;
    private ZonedDateTime paidAt;
}
