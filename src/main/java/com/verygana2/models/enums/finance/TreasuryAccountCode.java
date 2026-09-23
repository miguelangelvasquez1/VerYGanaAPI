package com.verygana2.models.enums.finance;

/**
 * Los bolsillos virtuales de tesorería de VeryGana.
 * Cada uno corresponde a un destino específico del dinero que entra a la app.
 * La suma de los saldos de estas cuentas siempre debe igualar el saldo
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
    PAYOUTS_PENDING,

    /**
     * Dinero que ingresan los empresarios al contratar planes (fuente externa a verygana)
     */
    EXTERNAL_INCOME,

    /**
     * IVA recaudado por VerYGana (19%), pendiente de declarar/pagar a la DIAN
     * en el ciclo de declaración de IVA (bimestral/cuatrimestral) — no se
     * remite venta a venta. Recibe: el 19% adicional que paga el empresario
     * sobre un depósito de inversión o una suscripción BASIC, y el 19% de
     * cada comisión de venta retenida por VerYGana (la comisión ya incluye IVA).
     */
    TAX_RESERVE,

    /**
     * Programa de conectividad (MP-04). Subsidia el costo de datos
     * móviles/internet de los usuarios. NO se alimenta del reparto de
     * depósitos empresariales (ver TreasuryServiceImpl#distributeDeposit) —
     * se nutre de otras operaciones, aún no implementadas.
     */
    CONNECTIVITY,

    /**
     * Infraestructura tecnológica de la plataforma (MP-04). Cubre hosting,
     * CDN y servicios en la nube. NO se alimenta del reparto de depósitos
     * empresariales — se nutre de otras operaciones, aún no implementadas.
     */
    INFRASTRUCTURE,

    /**
     * Nómina / remuneración del equipo de VerYGana (MP-04). NO se alimenta
     * del reparto de depósitos empresariales — se nutre de otras
     * operaciones, aún no implementadas.
     */
    PAYROLL
}
