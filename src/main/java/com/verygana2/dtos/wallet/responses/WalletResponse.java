package com.verygana2.dtos.wallet.responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.Transaction;


public record WalletResponse (
    BigDecimal balance,
    BigDecimal blockedBalance,
    ZonedDateTime lastUpDateTime,
    List<Transaction> walleTransactions
){
    
}
