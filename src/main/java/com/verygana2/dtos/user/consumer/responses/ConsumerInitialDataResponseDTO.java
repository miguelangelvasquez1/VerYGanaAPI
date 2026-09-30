package com.verygana2.dtos.user.consumer.responses;

import java.util.UUID;

import lombok.Data;

@Data
public class ConsumerInitialDataResponseDTO {
    private UUID publicId;
    private String name;
    private Long totalAvailableKeys;
    private Long purchaseKeys;
    private Long connectivityKeys;
    private Long blockedPurchaseKeys;
    private Long blockedConnectivityKeys;
    private String avatarUrl;
}
