package com.verygana2.dtos.user.consumer.responses;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ConsumerInitialDataResponseDTO {
    private Long id;
    private String name;
    // private String petName;
    private BigDecimal walletAvailableBalance;
    // private List<Notification> notificationNotRead;
}
