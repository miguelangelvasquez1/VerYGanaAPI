package com.verygana2.models.enums;

public enum PlatformTransactionType {
    // Comisiones (entradas)
    COMMISSION_PRODUCT_SALE("Comisión por venta de producto"),
    COMMISSION_RAFFLE("Comisión por creación de rifa"),
    COMMISSION_AD("Comisión por publicación de anuncio"),
    COMMISSION_PREMIUM_SUBSCRIPTION("Comisión por suscripción premium"),
    
    // Movimientos de dinero real
    REAL_MONEY_DEPOSIT("Entrada de dinero real"),
    REAL_MONEY_WITHDRAWAL("Salida de dinero real"),
    
    // Reservas y liberaciones
    WITHDRAWAL_RESERVED("Reserva para retiro"),
    WITHDRAWAL_COMPLETED("Retiro completado"),
    WITHDRAWAL_CANCELLED("Retiro cancelado"),
    
    // Ajustes manuales
    MANUAL_ADJUSTMENT("Ajuste manual por admin");
    
    private final String description;
    
    PlatformTransactionType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
