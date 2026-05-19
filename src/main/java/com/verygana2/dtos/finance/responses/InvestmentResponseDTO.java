package com.verygana2.dtos.finance.responses;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentResponseDTO {
    
    private BigDecimal depositAmountCOP;
}
