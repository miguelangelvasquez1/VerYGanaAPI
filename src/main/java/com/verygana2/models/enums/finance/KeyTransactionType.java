package com.verygana2.models.enums.finance;

/**
 * Todos los tipos de movimiento posibles en el historial de llaves de un usuario.
 * Reemplaza completamente el enum TransactionType de la entidad Transaction eliminada.
 *
 * Convención de prefijos:
 *   CREDIT_  → el usuario GANA llaves (saldo sube)
 *   DEBIT_   → el usuario PIERDE llaves (saldo baja)
 *   RESERVE_ → llaves bloqueadas temporalmente (saldo disponible baja, bloqueado sube)
 *   RELEASE_ → llaves desbloqueadas (revierte un RESERVE_)
 */
public enum KeyTransactionType {

    // ─── CRÉDITOS (el usuario gana llaves) ───────────────────────────────────

    /**
     * Llaves ganadas por interactuar con contenido patrocinado:
     * ver un anuncio, jugar un juego brandeado, completar una encuesta, etc.
     * Origen: distribución del depósito del empresario (60% → llaves a usuarios).
     */
    CREDIT_INTERACTION,

    /**
     * Llaves ganadas por referir a un nuevo usuario que se registró y activó.
     * Reemplaza: Transaction.POINTS_REFERRAL_BONUS
     */
    CREDIT_REFERRAL_BONUS,

    /**
     * Llaves acreditadas manualmente por el administrador.
     * Uso: correcciones, compensaciones, promociones especiales.
     */
    CREDIT_ADMIN_ADJUSTMENT,

    // ─── DÉBITOS (el usuario usa o pierde llaves) ─────────────────────────────

    /**
     * Llaves usadas como parte del pago en un copago de producto.
     * Se registra cuando el Copayment pasa a COMPLETED.
     * Reemplaza: Transaction.WHOLE_PURCHASE / PRODUCT_SALE
     */
    DEBIT_COPAYMENT,

    /**
     * Llaves de conectividad usadas para canjear una recarga de datos o minutos
     * a través de Puntored.
     * Reemplaza: Transaction.DATA_RECHARGE
     * Solo puede debitarse de connectivityKeys, nunca de purchaseKeys.
     */
    DEBIT_CONNECTIVITY_RECHARGE,

    /**
     * Llaves vencidas al cambio de período (mensual para purchase, diario para connectivity).
     * Generado por el job nocturno de vencimientos.
     * El valor equivalente en COP se transfiere a TreasuryAccount(FORTIFICATION).
     */
    DEBIT_EXPIRY,

    /**
     * Débito manual por el administrador.
     * Uso: correcciones, penalizaciones por fraude.
     */
    DEBIT_ADMIN_ADJUSTMENT,

    // ─── RESERVAS (bloqueo temporal durante un copago en curso) ──────────────

    /**
     * Llaves reservadas cuando el usuario inicia un copago (Copayment.status = PENDING).
     * El saldo disponible baja pero el saldo bloqueado sube en la misma cantidad.
     * Si Wompi aprueba → se convierte en DEBIT_COPAYMENT.
     * Si Wompi rechaza → se convierte en RELEASE_COPAYMENT_CANCELLED.
     */
    RESERVE_COPAYMENT_PENDING,

    /**
     * Llaves devueltas al saldo disponible porque el copago fue rechazado o cancelado.
     * Revierte un RESERVE_COPAYMENT_PENDING.
     */
    RELEASE_COPAYMENT_CANCELLED
}
