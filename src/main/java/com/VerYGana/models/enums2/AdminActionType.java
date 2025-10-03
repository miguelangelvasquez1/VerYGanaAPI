package com.VerYGana.models.enums2;

public enum AdminActionType {
    BALANCE_BLOCK("Balance Blocked"),
    BALANCE_UNBLOCK("Balance Unblocked"),
    BALANCE_ADJUSTMENT("Balance Adjustment"),
    ACCOUNT_FREEZE("Account Freeze"),
    ACCOUNT_UNFREEZE("Account Unfreeze"),
    PENALTY_APPLICATION("Penalty Applied"),
    BONUS_GRANT("Bonus Granted"),
    REFUND_PROCESSING("Refund Processed");

    private final String description;

    AdminActionType(String description) {
        this.description = description;
    }

    public String getDescription(){
        return description;
    }
}
