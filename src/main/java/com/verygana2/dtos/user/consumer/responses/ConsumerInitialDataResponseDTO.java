package com.verygana2.dtos.user.consumer.responses;

import lombok.Data;

@Data
public class ConsumerInitialDataResponseDTO {
    private Long id;
    private String name;
    private Long totalAvailableKeys;
    private Long purchaseKeys;
    private Long connectivityKeys;
    private Long blockedPurchaseKeys;
    private Long blockedConnectivityKeys;
}
