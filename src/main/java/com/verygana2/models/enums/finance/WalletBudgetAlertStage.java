package com.verygana2.models.enums.finance;

/** Última etapa de aviso de saldo bajo notificada al comercial — evita reenviar el mismo aviso. */
public enum WalletBudgetAlertStage {
    NONE,
    WARNING,
    CRITICAL,
    EXHAUSTED
}
