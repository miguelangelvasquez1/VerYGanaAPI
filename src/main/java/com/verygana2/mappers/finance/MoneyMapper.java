package com.verygana2.mappers.finance;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.verygana2.exceptions.InvalidAmountException;

@Component
public class MoneyMapper {
    
    public Long toCents(BigDecimal amount) {

        if (amount == null) {
            throw new InvalidAmountException("Amount cannot be null");
        }

        return amount.multiply(BigDecimal.valueOf(100)).longValue();
    }

    public BigDecimal fromCents(Long cents) {

        if (cents == null) {
            throw new InvalidAmountException("Amount cannot be null");
        }
        
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100));
    }
}
