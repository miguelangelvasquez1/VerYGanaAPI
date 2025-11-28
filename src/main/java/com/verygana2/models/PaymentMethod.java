package com.verygana2.models;

public enum PaymentMethod {
    WALLET,              // Pago con billetera interna (Tpoints) - PRINCIPAL
    CREDIT_CARD,         // Para recargas: Tarjeta de crédito
    DEBIT_CARD,          // Para recargas: Tarjeta débito
    PSE,                 // Para recargas: PSE Colombia
    BANK_TRANSFER,       // Para retiros: Transferencia bancaria
    NEQUI,               // Para recargas/retiros: Nequi
    DAVIPLATA,           // Para recargas/retiros: Daviplata
    BANCOLOMBIA,         // Para retiros: Cuenta Bancolombia
    CASH                 // Para recargas: Efectivo (puntos físicos)
}
