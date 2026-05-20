package com.verygana2.services.finance;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.models.enums.finance.MovementConcept;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.finance.TreasuryAccount;
import com.verygana2.models.finance.TreasuryMovement;
import com.verygana2.models.records.TreasurySnapshot;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.TreasuryAccountRepository;
import com.verygana2.repositories.finance.TreasuryMovementRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gestiona todos los movimientos entre las cuentas virtuales de tesorería.
 *
 * PRINCIPIO FUNDAMENTAL:
 * Cada peso que entra a VeryGana debe quedar reflejado en exactamente
 * una cuenta de tesorería. La suma de los 4 saldos siempre debe coincidir
 * con el saldo real de la cuenta bancaria de Bancolombia.
 *
 * Toda operación que modifique saldos:
 * 1. Adquiere lock pesimista sobre las cuentas afectadas (evita race
 * conditions)
 * 2. Modifica los saldos
 * 3. Registra TreasuryMovement por cada transferencia (libro contable)
 * 4. Todo en una sola transacción de BD — si algo falla, todo revierte
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TreasuryServiceImpl implements TreasuryService {

        private final TreasuryAccountRepository treasuryAccountRepository;
        private final TreasuryMovementRepository treasuryMovementRepository;
        private final TreasuryConfig treasuryConfig;

        /**
         * Distribuye un depósito de plan ESTÁNDAR o PREMIUM entre los 3 fondos.
         *
         * Distribución configurable desde application.yml (por defecto 60/10/30):
         * - KEYS_RESERVE: keysReservePct% → respaldo de llaves para usuarios
         * - FORTIFICATION: fortificationPct% → fondo de fortalecimiento empresarial
         * - OPERATIONS: operationsPct% → operación y utilidades de VeryGana
         *
         * El redondeo siempre favorece a OPERATIONS para que los centavos perdidos
         * por división entera no desaparezcan — la suma de los 3 montos siempre
         * es exactamente igual a amountCents.
         *
         * @param amountCents monto total del depósito en centavos de COP
         * @param commercial  empresario que realizó el depósito
         * @param referenceId ID del Investment o WompiTransaction que originó este
         *                    depósito
         */
        @Transactional
        @Override
        public void distributeDeposit(Long amountCents, CommercialDetails commercial, UUID referenceId) {
                log.info("[TREASURY] Distribuyendo depósito: amount={}, commercial={}, reference={}",
                                amountCents, commercial.getId(), referenceId);

                validateAmount(amountCents);

                // 1. Calcular montos de cada parte
                long keysAmount = amountCents * treasuryConfig.getKeysReservePct() / 100;
                long fortificationAmount = amountCents * treasuryConfig.getFortificationPct() / 100;
                // OPERATIONS absorbe el residuo del redondeo para que los 3 sumen exactamente
                // amountCents
                long operationsAmount = amountCents - keysAmount - fortificationAmount;

                log.debug("[TREASURY] Distribución: KEYS_RESERVE={}, FORTIFICATION={}, OPERATIONS={}",
                                keysAmount, fortificationAmount, operationsAmount);

                // 2. Obtener cuentas con lock pesimista — previene que dos depósitos
                // simultáneos modifiquen el mismo saldo con valores desactualizados
                TreasuryAccount external = getAccountForUpdate(TreasuryAccountCode.EXTERNAL_INCOME);
                TreasuryAccount keysReserve = getAccountForUpdate(TreasuryAccountCode.KEYS_RESERVE);
                TreasuryAccount fortification = getAccountForUpdate(TreasuryAccountCode.FORTIFICATION);
                TreasuryAccount operations = getAccountForUpdate(TreasuryAccountCode.OPERATIONS);

                // 3. Acreditar saldos
                keysReserve.setBalanceCents(keysReserve.getBalanceCents() + keysAmount);
                fortification.setBalanceCents(fortification.getBalanceCents() + fortificationAmount);
                operations.setBalanceCents(operations.getBalanceCents() + operationsAmount);

                treasuryAccountRepository.save(keysReserve);
                treasuryAccountRepository.save(fortification);
                treasuryAccountRepository.save(operations);

                // 4. Registrar los 3 movimientos en el libro contable
                // Usamos una cuenta "EXTERNAL" virtual como origen para representar
                // que el dinero viene de afuera del sistema (Wompi → Bancolombia)
                recordMovement(external, keysReserve, keysAmount,
                                MovementConcept.BUSINESS_DEPOSIT_KEYS, referenceId, "INVESTMENT");

                recordMovement(external, fortification, fortificationAmount,
                                MovementConcept.BUSINESS_DEPOSIT_FORTIFICATION, referenceId, "INVESTMENT");

                recordMovement(external, operations, operationsAmount,
                                MovementConcept.BUSINESS_DEPOSIT_OPERATIONS, referenceId, "INVESTMENT");

                log.info("[TREASURY] Depósito distribuido exitosamente: reference={}", referenceId);
        }

        /**
         * Registra el ingreso de un pago de plan BÁSICO mensual.
         *
         * El plan básico NO distribuye llaves porque es una suscripción de servicio,
         * no un depósito publicitario. Todo va a OPERATIONS como ingreso directo.
         *
         * @param amountCents monto de la suscripción en centavos
         * @param commercial  empresario que pagó
         * @param referenceId ID de la WompiTransaction que confirmó el pago
         */
        @Transactional
        @Override
        public void distributeSubscription(Long amountCents, CommercialDetails commercial, UUID referenceId) {
                log.info("[TREASURY] Registrando suscripción plan básico: amount={}, commercial={}, reference={}",
                                amountCents, commercial.getId(), referenceId);

                validateAmount(amountCents);

                TreasuryAccount external = getAccountForUpdate(TreasuryAccountCode.EXTERNAL_INCOME);

                TreasuryAccount operations = getAccountForUpdate(TreasuryAccountCode.OPERATIONS);
                operations.setBalanceCents(operations.getBalanceCents() + amountCents);
                treasuryAccountRepository.save(operations);

                recordMovement(external, operations, amountCents,
                                MovementConcept.BASIC_PLAN_SUBSCRIPTION, referenceId, "WOMPI_TRANSACTION");

                log.info("[TREASURY] Suscripción registrada en OPERATIONS: reference={}", referenceId);
        }

        /**
         * Mueve dinero de KEYS_RESERVE a PAYOUTS_PENDING cuando se procesa
         * la parte de llaves de un copago.
         * Llamado por CopaymentService cuando Wompi aprueba el pago del usuario.
         *
         * @param amountCents valor en COP de las llaves usadas en el copago
         * @param referenceId ID del Copayment
         */
        @Transactional
        @Override
        public void convertKeysToPayoutPending(Long amountCents, UUID referenceId) {
                log.info("[TREASURY] Convirtiendo llaves a PAYOUTS_PENDING: amount={}, reference={}",
                                amountCents, referenceId);

                validateAmount(amountCents);

                TreasuryAccount keysReserve = getAccountForUpdate(TreasuryAccountCode.KEYS_RESERVE);
                TreasuryAccount payoutsPending = getAccountForUpdate(TreasuryAccountCode.PAYOUTS_PENDING);

                long available = keysReserve.getBalanceCents();

                if (available < amountCents) {
                        throw new IllegalStateException(
                                        "[TREASURY] Saldo insuficiente en KEYS_RESERVE. " +
                                                        "disponible=" + available +
                                                        " requerido=" + amountCents);
                }

                long balanceAfter = available - amountCents;
                long criticalThreshold = treasuryConfig.getKeysReserveCriticalThresholdCents();
                long warnThreshold = treasuryConfig.getKeysReserveWarnThresholdCents();

                // Bloquear si el saldo post-transacción caería por debajo del umbral crítico
                if (balanceAfter < criticalThreshold) {
                        log.error("[TREASURY] KEYS_RESERVE CRÍTICO: saldo tras transacción={} < umbral={}. " +
                                        "Copago con llaves bloqueado. referenceId={}",
                                        balanceAfter, criticalThreshold, referenceId);
                        throw new IllegalStateException(
                                        "[TREASURY] KEYS_RESERVE por debajo del umbral crítico. " +
                                                        "El pago con llaves no está disponible temporalmente.");
                }

                // Alerta temprana (no bloquea)
                if (balanceAfter < warnThreshold) {
                        log.warn("[TREASURY] KEYS_RESERVE bajo: saldo tras transacción={} < umbral_warn={}. " +
                                        "Reponer fondo pronto. referenceId={}",
                                        balanceAfter, warnThreshold, referenceId);
                }

                keysReserve.setBalanceCents(balanceAfter);
                payoutsPending.setBalanceCents(payoutsPending.getBalanceCents() + amountCents);

                treasuryAccountRepository.save(keysReserve);
                treasuryAccountRepository.save(payoutsPending);

                recordMovement(keysReserve, payoutsPending, amountCents,
                                MovementConcept.COPAYMENT_KEYS_CONVERSION, referenceId, "COPAYMENT");

                log.info("[TREASURY] Conversión completada: KEYS_RESERVE={} → PAYOUTS_PENDING, reference={}",
                                balanceAfter, referenceId);
        }

        /**
         * Mueve el dinero en efectivo de un copago aprobado a PAYOUTS_PENDING.
         * El dinero en efectivo ya está en la cuenta bancaria (Wompi lo depositó),
         * este movimiento solo lo refleja en la tesorería virtual.
         *
         * @param amountCents parte en efectivo del copago (cashAmountCents)
         * @param referenceId ID del Copayment
         */
        @Transactional
        @Override
        public void moveCashToPayoutPending(Long amountCents, UUID referenceId) {
                log.info("[TREASURY] Moviendo efectivo a PAYOUTS_PENDING: amount={}, reference={}",
                                amountCents, referenceId);

                validateAmount(amountCents);

                // El efectivo viene de afuera (Wompi → Bancolombia). EXTERNAL_INCOME actúa
                // como cuenta de origen virtual para mantener la partida doble sin violar
                // el constraint nullable=false de from_account_id en TreasuryMovement.
                TreasuryAccount external = getAccountForUpdate(TreasuryAccountCode.EXTERNAL_INCOME);
                TreasuryAccount payoutsPending = getAccountForUpdate(TreasuryAccountCode.PAYOUTS_PENDING);
                payoutsPending.setBalanceCents(payoutsPending.getBalanceCents() + amountCents);
                treasuryAccountRepository.save(payoutsPending);

                recordMovement(external, payoutsPending, amountCents,
                                MovementConcept.SALE_TO_PAYOUT_PENDING, referenceId, "COPAYMENT");

                log.info("[TREASURY] Efectivo registrado en PAYOUTS_PENDING: reference={}", referenceId);
        }

        /**
         * Retiene la comisión de una venta: mueve de PAYOUTS_PENDING a OPERATIONS.
         * Llamado por el PayoutScheduler antes de transferirle al empresario.
         *
         * @param amountCents comisión en centavos
         * @param referenceId ID del Payout
         */
        @Transactional
        @Override
        public void retainCommission(Long amountCents, UUID referenceId, String referenceType) {
                log.info("[TREASURY] Reteniendo comisión: amount={}, reference={}", amountCents, referenceId);

                if (amountCents <= 0)
                        return;

                TreasuryAccount payoutsPending = getAccountForUpdate(TreasuryAccountCode.PAYOUTS_PENDING);
                TreasuryAccount operations = getAccountForUpdate(TreasuryAccountCode.OPERATIONS);

                if (payoutsPending.getBalanceCents() < amountCents) {
                        throw new IllegalStateException(
                                        "[TREASURY] Saldo insuficiente en PAYOUTS_PENDING para retener comisión.");
                }

                payoutsPending.setBalanceCents(payoutsPending.getBalanceCents() - amountCents);
                operations.setBalanceCents(operations.getBalanceCents() + amountCents);

                treasuryAccountRepository.save(payoutsPending);
                treasuryAccountRepository.save(operations);

                recordMovement(payoutsPending, operations, amountCents,
                                MovementConcept.COMMISSION_RETENTION, referenceId, referenceType);

                log.info("[TREASURY] Comisión retenida: PAYOUTS_PENDING → OPERATIONS, reference={}", referenceId);
        }

        /**
         * Registra la salida del dinero cuando se ejecuta un payout al empresario.
         * El dinero sale físicamente vía Wompi — este movimiento lo refleja en
         * la tesorería virtual debitando PAYOUTS_PENDING.
         *
         * @param amountCents monto neto transferido al empresario
         * @param referenceId ID del Payout
         */
        @Transactional
        @Override
        public void registerPayoutSent(Long amountCents, UUID referenceId) {
                log.info("[TREASURY] Registrando payout enviado: amount={}, reference={}",
                                amountCents, referenceId);

                validateAmount(amountCents);

                TreasuryAccount payoutsPending = getAccountForUpdate(TreasuryAccountCode.PAYOUTS_PENDING);

                if (payoutsPending.getBalanceCents() < amountCents) {
                        throw new IllegalStateException(
                                        "[TREASURY] Saldo insuficiente en PAYOUTS_PENDING para el payout.");
                }

                payoutsPending.setBalanceCents(payoutsPending.getBalanceCents() - amountCents);
                treasuryAccountRepository.save(payoutsPending);

                // El dinero sale hacia el banco del empresario. EXTERNAL_INCOME actúa
                // como cuenta de destino virtual para satisfacer el constraint not-null.
                TreasuryAccount external = getAccountForUpdate(TreasuryAccountCode.EXTERNAL_INCOME);
                recordMovement(payoutsPending, external, amountCents,
                                MovementConcept.PAYOUT_TO_BUSINESS, referenceId, "PAYOUT");

                log.info("[TREASURY] Payout registrado: PAYOUTS_PENDING → [externo], reference={}", referenceId);
        }

        /**
         * Retorna los saldos actuales de las 4 cuentas de tesorería.
         * Usado por el endpoint de auditoría del administrador.
         */
        @Transactional(readOnly = true)
        @Override
        public TreasurySnapshot getSnapshot() {
                long keysReserve = getBalance(TreasuryAccountCode.KEYS_RESERVE);
                long fortification = getBalance(TreasuryAccountCode.FORTIFICATION);
                long operations = getBalance(TreasuryAccountCode.OPERATIONS);
                long payouts = getBalance(TreasuryAccountCode.PAYOUTS_PENDING);
                long total = keysReserve + fortification + operations + payouts;

                return new TreasurySnapshot(keysReserve, fortification, operations, payouts, total);
        }

        // ─── Privados ─────────────────────────────────────────────────────────────

        private TreasuryAccount getAccountForUpdate(TreasuryAccountCode code) {
                return treasuryAccountRepository.findByCodeForUpdate(code)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Cuenta de tesorería no encontrada: " + code +
                                                                ". Verifica que TreasuryDataInitializer corrió correctamente."));
        }

        private long getBalance(TreasuryAccountCode code) {
                return treasuryAccountRepository.findByCode(code)
                                .map(TreasuryAccount::getBalanceCents)
                                .orElse(0L);
        }

        private void recordMovement(
                        TreasuryAccount from,
                        TreasuryAccount to,
                        long amountCents,
                        MovementConcept concept,
                        UUID referenceId,
                        String referenceType) {

                TreasuryMovement movement = TreasuryMovement.builder()
                                .fromAccount(from)
                                .toAccount(to)
                                .amountCents(amountCents)
                                .concept(concept)
                                .referenceId(referenceId)
                                .referenceType(referenceType)
                                .build();

                treasuryMovementRepository.save(Objects.requireNonNull(movement));
        }

        private void validateAmount(Long amountCents) {
                if (amountCents == null || amountCents <= 0) {
                        throw new IllegalArgumentException(
                                        "El monto debe ser positivo. Recibido: " + amountCents);
                }
        }
}

