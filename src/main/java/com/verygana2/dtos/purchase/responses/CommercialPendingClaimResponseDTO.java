package com.verygana2.dtos.purchase.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.DocumentType;

import lombok.Data;

@Data
public class CommercialPendingClaimResponseDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String imageUrl;
    private Long unitPriceCents;
    private String buyerName;
    private DocumentType documentType;
    private String documentNumber;
    private ZonedDateTime purchaseAt;
}
