package com.verygana2.dtos.finance.requests;

import com.verygana2.models.finance.PurchaseItemCashRefund.BankAccountType;
import com.verygana2.models.finance.PurchaseItemCashRefund.DocType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitCashRefundBankDetailsRequestDTO {

    @NotBlank(message = "Account holder name is required")
    private String accountHolderName;

    @NotBlank(message = "Account holder doc is required")
    private String accountHolderDoc;

    @NotNull(message = "Account holder doc type is required")
    private DocType accountHolderDocType;

    @NotBlank(message = "Bank name is required")
    private String bankName;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotNull(message = "Account type is required")
    private BankAccountType accountType;
}
