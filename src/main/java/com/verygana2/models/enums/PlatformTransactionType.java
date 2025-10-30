package com.verygana2.models.enums;

public enum PlatformTransactionType {
    // Comisiones (entradas)
    COMMISSION_PRODUCTS_SALE("Commission by products sale"),
    COMMISSION_RAFFLE("Commission by raffle ticket purchase"),
    COMMISSION_AD("Commision by ad published"),
    COMMISSION_PREMIUM_SUBSCRIPTION("Commission premium suscription"),
    
    // Movimientos de dinero real
    REAL_MONEY_DEPOSIT("Real money deposit"),
    REAL_MONEY_WITHDRAWAL("Real money withdrawal"),
    
    // Reservas y liberaciones
    WITHDRAWAL_RESERVED("Withdrawal reserved"),
    WITHDRAWAL_COMPLETED("Withdrawal completed"),
    WITHDRAWAL_CANCELLED("Withdrawal canceled"),
    
    // Ajustes manuales
    MANUAL_ADJUSTMENT("Manual adjustment by admin"),

    // Reembolsos
    PRODUCT_SALE_CANCELED("Product sale canceled");

    private final String description;
    
    PlatformTransactionType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
