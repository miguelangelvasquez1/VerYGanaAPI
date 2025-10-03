package com.VerYGana.dtos.Wallet.responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import com.VerYGana.models.Transaction;


public record WalletResponse (
    BigDecimal balance,
    BigDecimal blockedBalance,
    ZonedDateTime lastUpDateTime,
    List<Transaction> walleTransactions
){
    
}
