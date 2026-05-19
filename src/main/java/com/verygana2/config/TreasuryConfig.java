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
