package com.VerYGana.models.Interfaces;

import com.VerYGana.models.Enums.WalletOwnerType;

public interface WalletOwner {
    Long getId();
    String getEmail();
    WalletOwnerType getOwnerType();
}
