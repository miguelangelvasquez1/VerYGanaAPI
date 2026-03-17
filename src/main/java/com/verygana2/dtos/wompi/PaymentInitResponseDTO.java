package com.verygana2.dtos.wompi;

import lombok.Builder;
import lombok.Data;

@Data 
@Builder
public class PaymentInitResponseDTO {
    private String reference;
    private Long amountInCents;
    private String currency;
    private String publicKey;
    private String integrityHash;
    private String checkoutBaseUrl;
}
