package com.verygana2.dtos.ad.requests;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdLikeDTO {
    
    private Long userId;
    private String userName;
    private Long adId;
    private String adTitle;
    private BigDecimal rewardAmount;
    private LocalDateTime createdAt;
    private String ipAddress;
}