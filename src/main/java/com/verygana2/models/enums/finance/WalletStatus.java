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
     * Saldo disponible y suficiente para operar.
     * Las interacciones activas están corriendo normalmente.
     */
    ACTIVE,

    /**
     * Saldo por debajo del umbral de seguridad (10% del último depósito).
     * Las interacciones siguen activas pero se notifica al empresario
     * que debe recargar pronto para evitar una pausa.
     */
    LOW_BALANCE,

    /**
     * Saldo agotado (balance = 0).
     * Todas las interacciones fueron pausadas automáticamente.
     * Se reactiva automáticamente cuando el empresario recarga.
     */
    EXHAUSTED
}
