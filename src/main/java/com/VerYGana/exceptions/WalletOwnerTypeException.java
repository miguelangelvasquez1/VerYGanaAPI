package com.VerYGana.exceptions;

import com.VerYGana.models.Enums.WalletOwnerType;

public class WalletOwnerTypeException extends RuntimeException{
    
    public WalletOwnerTypeException(WalletOwnerType walletOwnerType){
        super("The wallet owner type mismatch, the owner of this wallet must be an " + walletOwnerType);
    }
}
