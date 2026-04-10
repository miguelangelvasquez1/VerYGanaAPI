package com.verygana2.models.enums.finance;

/**
 * Los 4 bolsillos virtuales de tesorería de VeryGana.
 * Cada uno corresponde a un destino específico del dinero que entra a la app.
 * La suma de los saldos de estas 4 cuentas siempre debe igualar el saldo
 * real de la cuenta bancaria de Bancolombia.
 */
public enum TreasuryAccountCode {

    /**
     * Reserva que respalda todas las llaves en circulación.
     * Recibe el 60% de cada depósito empresarial.
     * Se debita cuando la app convierte llaves a dinero en un copago.
     */
    KEYS_RESERVE,

    /**
     * Fondo de fortalecimiento empresarial.
     * Recibe el 10% de cada depósito y se alimenta de las llaves vencidas.
     * Se usa para comprar productos a empresarios con bajo rendimiento
     * y financiar rifas y premios del administrador.
     */
    FORTIFICATION,

    /**
     * Operación y utilidades de VeryGana.
     * Recibe el 30% de cada depósito empresarial.
     * Cubre infraestructura, salarios y ganancia de la app.
     */
    OPERATIONS,

    /**
     * Dinero acumulado listo para el payout diario a empresarios.
     * Actúa como sala de espera: el dinero se mueve aquí desde KEYS_RESERVE
     * y descontando comisiones, espera hasta el job de las 24h para salir
     * vía Wompi hacia la cuenta del empresario.
     */
    PAYOUTS_PENDING
}
