package com.verygana2.models.enums.finance;

/** Última etapa de aviso de saldo bajo notificada al comercial — evita reenviar el mismo aviso. */
public enum WalletBudgetAlertStage {
    NONE,
    WARNING,
    CRITICAL,
    EXHAUSTED,
    /** Saldo en 0 durante más del periodo de gracia del plan sin recargar — cuenta en pausa. */
    DORMANT
}
