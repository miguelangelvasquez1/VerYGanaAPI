package com.verygana2.dtos.purchase.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.enums.marketplace.PurchaseBuyerType;

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
    /**
     * VERYGANA cuando el ítem es un reconocimiento: buyerName y el documento son entonces
     * los del beneficiario, que es quien se presenta a recoger.
     */
    private PurchaseBuyerType buyerType;
}
