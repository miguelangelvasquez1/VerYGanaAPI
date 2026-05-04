package com.verygana2.mappers.finance;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component
public class MoneyMapper {
    
    public Long toCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).longValue();
    }
    public BigDecimal fromCents(Long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100));
    }
}
