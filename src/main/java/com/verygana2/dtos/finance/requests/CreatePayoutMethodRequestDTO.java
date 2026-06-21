package com.verygana2.dtos.finance.requests;

import com.verygana2.models.finance.PayoutMethod.BankAccountType;
import com.verygana2.models.finance.PayoutMethod.DocType;
import com.verygana2.models.finance.PayoutMethod.PayoutMethodType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePayoutMethodRequestDTO {

    @NotNull (message = "Payout method type is required")
    private PayoutMethodType type;

    @NotBlank (message = "alias is required")
    private String alias;

    private String bankCode;
    private String accountNumber;
    private BankAccountType bankAccountType;
    private String phoneNumber;

    @NotBlank(message = "Account holder name is required")
    private String accountHolderName;

    @NotNull(message = "Account holder doc type is required")
    private DocType accountHolderDocType;

    @NotBlank(message = "Account holder doc is required")
    private String accountHolderDoc;
}
