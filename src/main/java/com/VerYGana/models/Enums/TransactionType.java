package com.VerYGana.models.enums;

public enum TransactionType {
    // Review
    // Ingresos
     DEPOSIT,           // Depósito de dinero
     POINTS_AD_LIKE_REWARD,       // Puntos por ver anuncios y dar like
     POINTS_REFERRAL_BONUS,  // Puntos por referidos
     RAFFLE_PRIZE,          // Premio de rifa
    
    // Egresos  
    WITHDRAWAL,       // Retiro de dinero
    PRODUCT_PURCHASE,       // Compra de producto
    PRODUCT_SALE,           // Venta de producto
    RAFFLE_PARTICIPATION,  // Participar en rifa
    DATA_RECHARGE,         // Recarga de datos
    
    // Transferencias
    GIFT_TRANSFER_SENT,     // Envío entre usuarios
    GIFT_TRANSFER_RECEIVED,  // Recepción entre usuarios
    
}
