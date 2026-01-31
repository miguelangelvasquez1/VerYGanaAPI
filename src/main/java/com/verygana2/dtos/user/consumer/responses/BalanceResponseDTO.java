package com.verygana2.dtos.user.consumer.responses;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BalanceResponseDTO {
    
    private BigDecimal availableBalance;
}
