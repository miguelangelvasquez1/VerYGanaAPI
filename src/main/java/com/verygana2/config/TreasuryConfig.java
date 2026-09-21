package com.verygana2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Porcentajes de distribución de los depósitos empresariales.
 * Cargados desde application.yml bajo el prefijo "treasury.distribution".
 *
 * Cambiar estos valores en el yml afecta TODOS los depósitos futuros.
 * Los depósitos pasados mantienen los movimientos registrados en TreasuryMovement.
 */
@Slf4j
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "treasury.distribution")
public class TreasuryConfig {

    @Min(1) @Max(99)
    private int keysReservePct;

    @Min(1) @Max(99)
    private int fortificationPct;

    @Min(1) @Max(99)
    private int operationsPct;

    /**
     * Porcentaje de IVA que VerYGana recauda (no declara/paga venta a venta,
     * se acumula en TAX_RESERVE hasta el ciclo de declaración DIAN):
     *   - Depósitos de inversión STANDARD/PREMIUM y suscripción BASIC: se cobra
     *     ADICIONAL al monto base (ej. depositar $1.000.000 implica pagar
     *     $1.190.000 con vatPct=19).
     *   - Comisión de venta: se EXTRAE del monto de la comisión, que ya lo
     *     incluye (ej. comisión de $100.000 con vatPct=19 → $19.000 a
     *     TAX_RESERVE, $81.000 a OPERATIONS).
     * Configurable (no hardcodeado) para poder ajustarse sin tocar código si
     * la tarifa de IVA cambia. Default 19 si no se configura explícitamente.
     */
    @Min(0) @Max(100)
    private int vatPct = 19;

    /**
     * Saldo mínimo en centavos para emitir un WARNING en los logs.
     * Default: 10.000.000 centavos = $100.000 COP.
     * No bloquea operaciones — es solo una alerta temprana.
     */
    private long keysReserveWarnThresholdCents = 10_000_000L;

    /**
     * Saldo mínimo en centavos por debajo del cual se bloquean los copagos con llaves.
     * Default: 2.000.000 centavos = $20.000 COP.
     * Previene que KEYS_RESERVE llegue a cero dejando compras en estado inválido.
     */
    private long keysReserveCriticalThresholdCents = 2_000_000L;

    /**
     * Valida al arrancar que los porcentajes sumen exactamente 100.
     * Si no suman 100 la app falla al iniciar con un mensaje claro.
     * Esto previene bugs silenciosos donde el dinero "desaparece" o se duplica.
     */
    @PostConstruct
    public void validate() {
        int total = keysReservePct + fortificationPct + operationsPct;
        if (total != 100) {
            throw new IllegalStateException(
                "Los porcentajes de distribución de tesorería deben sumar 100. " +
                "Actual: keysReserve=" + keysReservePct +
                " fortification=" + fortificationPct +
                " operations=" + operationsPct +
                " total=" + total
            );
        }
        log.info("[TREASURY] Distribución cargada: KEYS_RESERVE={}%, FORTIFICATION={}%, OPERATIONS={}%",
                keysReservePct, fortificationPct, operationsPct);
    }
}
