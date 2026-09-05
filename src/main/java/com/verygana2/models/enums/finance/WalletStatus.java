package com.verygana2.models.enums.finance;

/**
 * Estado del presupuesto publicitario del empresario.
 *
 * El estado es INDEPENDIENTE del plan:
 * Un PREMIUM puede estar EXHAUSTED (sin saldo) y sigue siendo PREMIUM.
 * El plan nunca baja — solo el estado operativo cambia.
 */
public enum WalletStatus {

    /**
     * Wallet recién creado. El empresario aún no ha hecho ningún depósito.
     * No puede activar ninguna interacción.
     */
    INACTIVE,

    /**
     * Saldo disponible ( > 0 ). Las interacciones activas corren normalmente.
     * El aviso de "saldo bajo" (WARNING / CRITICAL) NO es un estado: se deriva en
     * tiempo real de los umbrales por plan en {@code BudgetAlertScheduler} y en el
     * dashboard del comercial.
     */
    ACTIVE,

    /**
     * Saldo agotado (balance = 0).
     * Todas las interacciones fueron pausadas automáticamente.
     * Se reactiva automáticamente cuando el empresario recarga.
     */
    EXHAUSTED
}
