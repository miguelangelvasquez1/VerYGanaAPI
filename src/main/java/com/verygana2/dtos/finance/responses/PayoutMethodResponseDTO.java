package com.verygana2.dtos.finance.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.finance.PayoutMethod.BankAccountType;
import com.verygana2.models.finance.PayoutMethod.DocType;
import com.verygana2.models.finance.PayoutMethod.PayoutMethodType;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;

import lombok.Data;

@Data
public class PayoutMethodResponseDTO {
    private Long id;
    private PayoutMethodType type;
    private String alias;

    // Campos BANK_TRANSFER
    private String bankCode;
    private String accountNumber;
    private BankAccountType bankAccountType;

    // Campos NEQUI / DAVIPLATA
    private String phoneNumber;

    // Campos comunes
    private String accountHolderName;
    private String accountHolderDoc;
    private DocType accountHolderDocType;

    // Estado de verificación
    private VerificationStatus verificationStatus;
    private String rejectionReason;

    private boolean active;
    private boolean firstPayoutCompleted;
    private boolean defaultMethod;
    private ZonedDateTime createdAt;
    private ZonedDateTime verifiedAt;

    /**
     * URL del proxy privado para ver la certificación bancaria (PDF/foto), solo
     * cuando ya fue subida y validada. Null si el método no tiene certificación
     * (ej. NEQUI/DAVIPLATA, o BANK_ACCOUNT que aún no la sube).
     */
    private String certificateUrl;
}
