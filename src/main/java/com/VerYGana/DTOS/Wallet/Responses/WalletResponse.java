package com.VerYGana.DTOS.Wallet.Responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.VerYGana.models.Enums.WalletOwnerType;


public record WalletResponse (
    Long id,
    Long ownerId,
    WalletOwnerType ownerType,
    BigDecimal balance,
    BigDecimal blockedBalance,
    ZonedDateTime lastUpdated
){
    
}
