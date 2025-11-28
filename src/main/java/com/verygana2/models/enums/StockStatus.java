package com.verygana2.models.enums;

public enum StockStatus {
    AVAILABLE,      // Disponible para vender
    RESERVED,       // Reservado (en proceso de compra)
    SOLD,           // Ya vendido
    EXPIRED,        // Código expirado
    INVALID         // Código inválido/reportado
}
