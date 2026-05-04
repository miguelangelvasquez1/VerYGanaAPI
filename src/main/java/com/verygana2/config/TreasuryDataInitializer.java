package com.verygana2.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.finance.TreasuryAccount;
import com.verygana2.repositories.finance.TreasuryAccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * Inicializa los 4 registros fijos de tesorería al arrancar la app.
 *
 * Es IDEMPOTENTE: si los registros ya existen no hace nada.
 * Es seguro ejecutarlo en cada startup, en producción y en tests.
 *
 * Orden de ejecución: ApplicationRunner corre después de que el contexto
 * de Spring y Hibernate están completamente listos, por lo que las tablas
 * ya existen cuando este código se ejecuta.
 */
@Component
@RequiredArgsConstructor
public class TreasuryDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TreasuryDataInitializer.class);

    private final TreasuryAccountRepository treasuryAccountRepository;

    /**
     * Definición de los 4 bolsillos virtuales de tesorería.
     * El orden no importa porque la idempotencia se verifica por code,
     * no por posición.
     */
    private static final List<AccountDefinition> ACCOUNTS = List.of(
        new AccountDefinition(
            TreasuryAccountCode.KEYS_RESERVE,
            "Reserva de llaves",
            "Respalda el valor en COP de todas las llaves en circulación. " +
            "Recibe el 60% de cada depósito empresarial. " +
            "Se debita cuando la app convierte llaves a dinero en un copago."
        ),
        new AccountDefinition(
            TreasuryAccountCode.FORTIFICATION,
            "Fondo de fortalecimiento",
            "Recibe el 10% de cada depósito empresarial y el valor de las llaves vencidas. " +
            "Se usa para comprar productos a empresarios con bajo rendimiento " +
            "y financiar rifas y premios del administrador."
        ),
        new AccountDefinition(
            TreasuryAccountCode.OPERATIONS,
            "Operación VeryGana",
            "Recibe el 30% de cada depósito empresarial. " +
            "Cubre infraestructura, salarios, utilidades y gastos operativos de la app."
        ),
        new AccountDefinition(
            TreasuryAccountCode.PAYOUTS_PENDING,
            "Pagos pendientes a empresarios",
            "Sala de espera del dinero que le corresponde a los empresarios por sus ventas. " +
            "El dinero entra aquí después de cada copago completado y sale " +
            "vía Wompi en el job de payout diario a las 11 PM."
        )
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=== TreasuryDataInitializer: verificando cuentas de tesorería ===");

        int created = 0;
        int skipped = 0;

        for (AccountDefinition def : ACCOUNTS) {
            if (treasuryAccountRepository.existsByCode(def.code())) {
                log.debug("Cuenta [{}] ya existe — omitiendo", def.code());
                skipped++;
            } else {
                TreasuryAccount account = TreasuryAccount.builder()
                        .code(def.code())
                        .name(def.name())
                        .balanceCents(0L)
                        .build();

                treasuryAccountRepository.save(account);
                log.info("Cuenta de tesorería creada: [{}] - {}", def.code(), def.name());
                created++;
            }
        }

        log.info("=== TreasuryDataInitializer completado: {} creadas, {} ya existían ===",
                created, skipped);
    }

    /**
     * Record interno para definir cada cuenta.
     * Mantiene la definición junto al código de inicialización
     * sin necesidad de una clase separada.
     */
    private record AccountDefinition(
            TreasuryAccountCode code,
            String name,
            String description
    ) {}
}