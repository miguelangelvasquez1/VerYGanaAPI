package com.verygana2.models.enums;

public enum PlatformTransactionType {
    // Comisiones (entradas)
    COMMISSION_PRODUCTS_SALE("Commission by products sale"),
    COMMISSION_RAFFLE("Commission by raffle ticket purchase"),
    COMMISSION_AD("Commission by ad published"),
    COMMISION_GAME("Commission by game builded"),
    COMMISSION_PREMIUM_SUBSCRIPTION("Commission premium suscription"),
    
    // Movimientos de dinero real
    REAL_MONEY_DEPOSIT("Real money deposit"),
    REAL_MONEY_WITHDRAWAL("Real money withdrawal"),
    
    // Reservas y liberaciones
    WITHDRAWAL_REQUESTED("Withdrawal requested"),
    WITHDRAWAL_COMPLETED("Withdrawal completed"),
    WITHDRAWAL_CANCELLED("Withdrawal cancelled"),
    WITHDRAWAL_RESERVED("Withdrawal reserved"),
    
    // Ajustes manuales
    MANUAL_ADJUSTMENT("Manual adjustment by admin"),

    // Referidos
    REFERRAL_PROMOTION("Referral promotion");

    private final String description;
    
    PlatformTransactionType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
