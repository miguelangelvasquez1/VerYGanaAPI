package com.verygana2.dtos.recognitions;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.enums.recognitions.RecognitionStatus;

/** Lo que ve el beneficiario de un reconocimiento recibido. Sin precios ni datos del fondo. */
public record ConsumerRecognitionDTO(
        UUID publicId,
        RecognitionStatus status,
        String reason,
        String productName,
        String imageUrl,
        String commercialName,
        ZonedDateTime createdAt,
        ZonedDateTime deliveredAt,
        List<Item> items) {

    /** @param code código del producto, solo cuando ya fue entregado. */
    public record Item(Long itemId, PurchaseItemStatus status, String code, ZonedDateTime claimExpiresAt) {
    }
}
