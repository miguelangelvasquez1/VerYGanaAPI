package com.verygana2.services.finance;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.treasury.TreasuryBalanceResponseDTO;
import com.verygana2.dtos.treasury.TreasuryMovementResponseDTO;
import com.verygana2.exceptions.InvalidAmountException;
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
         * es exactamente igual a baseAmountCents.
         *
         * El IVA (si lo hay) NO participa de esta distribución — va completo y
         * aparte a TAX_RESERVE (ver MP-02 Frente 2: el empresario paga
         * baseAmountCents + IVA, pero solo baseAmountCents es la inversión real).
         *
         * @param baseAmountCents monto de inversión sin IVA, en centavos de COP
         * @param vatAmountCents  IVA cobrado sobre el depósito, en centavos (puede ser 0)
         * @param commercial      empresario que realizó el depósito
         * @param referenceId     ID del Investment o WompiTransaction que originó este
         *                        depósito
         */
        @Transactional
        @Override
        public void distributeDeposit(Long baseAmountCents, Long vatAmountCents, CommercialDetails commercial,
                        UUID referenceId) {
                log.info("[TREASURY] Distribuyendo depósito: base={}, vat={}, commercial={}, reference={}",
                                baseAmountCents, vatAmountCents, commercial.getId(), referenceId);

                validateAmount(baseAmountCents);

                // 1. Calcular montos de cada parte
                long keysAmount = baseAmountCents * treasuryConfig.getKeysReservePct() / 100;
                long fortificationAmount = baseAmountCents * treasuryConfig.getFortificationPct() / 100;
                // OPERATIONS absorbe el residuo del redondeo para que los 3 sumen exactamente
                // baseAmountCents
                long operationsAmount = baseAmountCents - keysAmount - fortificationAmount;

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

                // 5. IVA del depósito, aparte, completo a TAX_RESERVE
                if (vatAmountCents != null && vatAmountCents > 0) {
                        TreasuryAccount taxReserve = getAccountForUpdate(TreasuryAccountCode.TAX_RESERVE);
                        taxReserve.setBalanceCents(taxReserve.getBalanceCents() + vatAmountCents);
                        treasuryAccountRepository.save(taxReserve);

                        recordMovement(external, taxReserve, vatAmountCents,
                                        MovementConcept.BUSINESS_DEPOSIT_VAT, referenceId, "INVESTMENT");
                }

                log.info("[TREASURY] Depósito distribuido exitosamente: reference={}", referenceId);
        }

        /**
         * Registra el ingreso de un pago de plan BÁSICO mensual.
         *
         * El plan básico NO distribuye llaves porque es una suscripción de servicio,
         * no un depósito publicitario. La base va a OPERATIONS como ingreso directo;
         * el IVA (si lo hay) va aparte a TAX_RESERVE.
         *
         * @param baseAmountCents monto de la suscripción sin IVA, en centavos
         * @param vatAmountCents  IVA cobrado sobre la suscripción, en centavos (puede ser 0)
         * @param commercial      empresario que pagó
         * @param referenceId     ID de la WompiTransaction que confirmó el pago
         */
        @Transactional
        @Override
        public void distributeSubscription(Long baseAmountCents, Long vatAmountCents, CommercialDetails commercial,
                        UUID referenceId) {
                log.info("[TREASURY] Registrando suscripción plan básico: base={}, vat={}, commercial={}, reference={}",
                                baseAmountCents, vatAmountCents, commercial.getId(), referenceId);

                validateAmount(baseAmountCents);

                TreasuryAccount external = getAccountForUpdate(TreasuryAccountCode.EXTERNAL_INCOME);

                TreasuryAccount operations = getAccountForUpdate(TreasuryAccountCode.OPERATIONS);
                operations.setBalanceCents(operations.getBalanceCents() + baseAmountCents);
                treasuryAccountRepository.save(operations);

                recordMovement(external, operations, baseAmountCents,
                                MovementConcept.BASIC_PLAN_SUBSCRIPTION, referenceId, "WOMPI_TRANSACTION");

                if (vatAmountCents != null && vatAmountCents > 0) {
                        TreasuryAccount taxReserve = getAccountForUpdate(TreasuryAccountCode.TAX_RESERVE);
                        taxReserve.setBalanceCents(taxReserve.getBalanceCents() + vatAmountCents);
                        treasuryAccountRepository.save(taxReserve);

                        recordMovement(external, taxReserve, vatAmountCents,
                                        MovementConcept.BASIC_PLAN_SUBSCRIPTION_VAT, referenceId, "WOMPI_TRANSACTION");
                }

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
         * Retiene la comisión de una venta: mueve de PAYOUTS_PENDING a OPERATIONS,
         * salvo la porción de IVA (la comisión ya la incluye) que se extrae y va
         * a TAX_RESERVE en vez de a OPERATIONS. Llamado desde CopaymentServiceImpl
         * cuando la venta se confirma.
         *
         * @param amountCents comisión total en centavos (incluye IVA)
         * @param vatCents    porción de esa comisión correspondiente a IVA (puede ser 0)
         * @param referenceId ID del Copayment
         */
        @Transactional
        @Override
        public void retainCommission(Long amountCents, Long vatCents, UUID referenceId, String referenceType) {
                log.info("[TREASURY] Reteniendo comisión: amount={}, vat={}, reference={}",
                                amountCents, vatCents, referenceId);

                if (amountCents <= 0)
                        return;

                long vat = vatCents == null ? 0L : vatCents;
                long netAmount = amountCents - vat;

                TreasuryAccount payoutsPending = getAccountForUpdate(TreasuryAccountCode.PAYOUTS_PENDING);

                if (payoutsPending.getBalanceCents() < amountCents) {
                        throw new IllegalStateException(
                                        "[TREASURY] Saldo insuficiente en PAYOUTS_PENDING para retener comisión.");
                }

                payoutsPending.setBalanceCents(payoutsPending.getBalanceCents() - amountCents);
                treasuryAccountRepository.save(payoutsPending);

                if (netAmount > 0) {
                        TreasuryAccount operations = getAccountForUpdate(TreasuryAccountCode.OPERATIONS);
                        operations.setBalanceCents(operations.getBalanceCents() + netAmount);
                        treasuryAccountRepository.save(operations);

                        recordMovement(payoutsPending, operations, netAmount,
                                        MovementConcept.COMMISSION_RETENTION, referenceId, referenceType);
                }

                if (vat > 0) {
                        TreasuryAccount taxReserve = getAccountForUpdate(TreasuryAccountCode.TAX_RESERVE);
                        taxReserve.setBalanceCents(taxReserve.getBalanceCents() + vat);
                        treasuryAccountRepository.save(taxReserve);

                        recordMovement(payoutsPending, taxReserve, vat,
                                        MovementConcept.COMMISSION_VAT_RETENTION, referenceId, referenceType);
                }

                log.info("[TREASURY] Comisión retenida: PAYOUTS_PENDING → OPERATIONS/TAX_RESERVE, reference={}",
                                referenceId);
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
         * Reversa internamente un PurchaseItem reembolsado. Ver Javadoc de la
         * interfaz para el alcance exacto (no reversa el cobro en Wompi).
         */
        @Transactional
        @Override
        public void reversePurchaseItemForRefund(Long commissionCents, Long commissionVatCents,
                        Long keysPortionCents, Long cashPortionCents, UUID referenceId) {
                log.info("[TREASURY] Reversando ítem reembolsado: commission={}, vat={}, keys={}, cash={}, reference={}",
                                commissionCents, commissionVatCents, keysPortionCents, cashPortionCents, referenceId);

                long commission = commissionCents == null ? 0 : commissionCents;
                long commissionVat = commissionVatCents == null ? 0 : commissionVatCents;
                long keysPortion = keysPortionCents == null ? 0 : keysPortionCents;
                long cashPortion = cashPortionCents == null ? 0 : cashPortionCents;

                if (commission < 0 || commissionVat < 0 || keysPortion < 0 || cashPortion < 0) {
                        throw new InvalidAmountException("Los montos a reversar no pueden ser negativos");
                }

                // La comisión retenida originalmente se dividió en (commission - vat) →
                // OPERATIONS y vat → TAX_RESERVE (ver retainCommission). Cada porción se
                // reversa desde la cuenta a la que realmente fue a parar.
                long commissionNet = commission - commissionVat;

                TreasuryAccount payoutsPending = getAccountForUpdate(TreasuryAccountCode.PAYOUTS_PENDING);

                if (commissionNet > 0) {
                        TreasuryAccount operations = getAccountForUpdate(TreasuryAccountCode.OPERATIONS);

                        if (operations.getBalanceCents() < commissionNet) {
                                throw new IllegalStateException(
                                                "[TREASURY] Saldo insuficiente en OPERATIONS para revertir la comisión.");
                        }

                        operations.setBalanceCents(operations.getBalanceCents() - commissionNet);
                        payoutsPending.setBalanceCents(payoutsPending.getBalanceCents() + commissionNet);

                        treasuryAccountRepository.save(operations);
                        treasuryAccountRepository.save(payoutsPending);

                        recordMovement(operations, payoutsPending, commissionNet,
                                        MovementConcept.COMMISSION_REVERSAL, referenceId, "PURCHASE_ITEM_REFUND");
                }

                if (commissionVat > 0) {
                        TreasuryAccount taxReserve = getAccountForUpdate(TreasuryAccountCode.TAX_RESERVE);

                        if (taxReserve.getBalanceCents() < commissionVat) {
                                throw new IllegalStateException(
                                                "[TREASURY] Saldo insuficiente en TAX_RESERVE para revertir el IVA de la comisión.");
                        }

                        taxReserve.setBalanceCents(taxReserve.getBalanceCents() - commissionVat);
                        payoutsPending.setBalanceCents(payoutsPending.getBalanceCents() + commissionVat);

                        treasuryAccountRepository.save(taxReserve);
                        treasuryAccountRepository.save(payoutsPending);

                        recordMovement(taxReserve, payoutsPending, commissionVat,
                                        MovementConcept.COMMISSION_VAT_REVERSAL, referenceId, "PURCHASE_ITEM_REFUND");
                }

                if (keysPortion > 0) {
                        TreasuryAccount keysReserve = getAccountForUpdate(TreasuryAccountCode.KEYS_RESERVE);

                        if (payoutsPending.getBalanceCents() < keysPortion) {
                                throw new IllegalStateException(
                                                "[TREASURY] Saldo insuficiente en PAYOUTS_PENDING para reponer KEYS_RESERVE.");
                        }

                        payoutsPending.setBalanceCents(payoutsPending.getBalanceCents() - keysPortion);
                        keysReserve.setBalanceCents(keysReserve.getBalanceCents() + keysPortion);

                        treasuryAccountRepository.save(payoutsPending);
                        treasuryAccountRepository.save(keysReserve);

                        recordMovement(payoutsPending, keysReserve, keysPortion,
                                        MovementConcept.REFUND_KEYS_TO_RESERVE, referenceId, "PURCHASE_ITEM_REFUND");
                }

                if (cashPortion > 0) {
                        TreasuryAccount operations = getAccountForUpdate(TreasuryAccountCode.OPERATIONS);

                        if (payoutsPending.getBalanceCents() < cashPortion) {
                                throw new IllegalStateException(
                                                "[TREASURY] Saldo insuficiente en PAYOUTS_PENDING para el reembolso en efectivo.");
                        }

                        payoutsPending.setBalanceCents(payoutsPending.getBalanceCents() - cashPortion);
                        operations.setBalanceCents(operations.getBalanceCents() + cashPortion);

                        treasuryAccountRepository.save(payoutsPending);
                        treasuryAccountRepository.save(operations);

                        recordMovement(payoutsPending, operations, cashPortion,
                                        MovementConcept.REFUND_CASH_TO_OPERATIONS, referenceId, "PURCHASE_ITEM_REFUND");
                }

                log.info("[TREASURY] Reversión completada: reference={}", referenceId);
        }

        /**
         * Registra el pago manual de un reembolso en efectivo: sale de
         * OPERATIONS hacia afuera del sistema (mismo patrón que registerPayoutSent).
         */
        @Transactional
        @Override
        public void registerManualCashRefundPaid(Long amountCents, UUID referenceId) {
                log.info("[TREASURY] Registrando reembolso en efectivo pagado manualmente: amount={}, reference={}",
                                amountCents, referenceId);

                validateAmount(amountCents);

                TreasuryAccount operations = getAccountForUpdate(TreasuryAccountCode.OPERATIONS);

                if (operations.getBalanceCents() < amountCents) {
                        throw new IllegalStateException(
                                        "[TREASURY] Saldo insuficiente en OPERATIONS para el reembolso manual.");
                }

                operations.setBalanceCents(operations.getBalanceCents() - amountCents);
                treasuryAccountRepository.save(operations);

                TreasuryAccount external = getAccountForUpdate(TreasuryAccountCode.EXTERNAL_INCOME);
                recordMovement(operations, external, amountCents,
                                MovementConcept.REFUND_TO_BUYER, referenceId, "CASH_REFUND");

                log.info("[TREASURY] Reembolso manual registrado: OPERATIONS → [externo], reference={}", referenceId);
        }

        /**
         * Retorna los saldos actuales de las cuentas de tesorería.
         * Usado por el endpoint de auditoría del administrador.
         */
        @Transactional(readOnly = true)
        @Override
        public TreasurySnapshot getSnapshot() {
                long keysReserve = getBalance(TreasuryAccountCode.KEYS_RESERVE);
                long fortification = getBalance(TreasuryAccountCode.FORTIFICATION);
                long operations = getBalance(TreasuryAccountCode.OPERATIONS);
                long payouts = getBalance(TreasuryAccountCode.PAYOUTS_PENDING);
                long taxReserve = getBalance(TreasuryAccountCode.TAX_RESERVE);
                long total = keysReserve + fortification + operations + payouts + taxReserve;

                return new TreasurySnapshot(keysReserve, fortification, operations, payouts, taxReserve, total);
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

        @Transactional
        @Override
        public void moveExpiredKeysToFortification(Long amountCents, UUID batchId) {
                log.info("[TREASURY] Moviendo llaves vencidas a FORTIFICATION: amount={}, batch={}",
                                amountCents, batchId);

                validateAmount(amountCents);

                TreasuryAccount keysReserve = getAccountForUpdate(TreasuryAccountCode.KEYS_RESERVE);
                TreasuryAccount fortification = getAccountForUpdate(TreasuryAccountCode.FORTIFICATION);

                // Si el fondo tiene menos de lo esperado (inconsistencia contable),
                // mover lo que hay y registrarlo en log — nunca bloquear el vencimiento.
                if (keysReserve.getBalanceCents() < amountCents) {
                        log.error("[TREASURY] KEYS_RESERVE insuficiente al vencer llaves. " +
                                        "disponible={}, esperado={}. Moviendo el disponible.",
                                        keysReserve.getBalanceCents(), amountCents);
                        amountCents = keysReserve.getBalanceCents();
                }

                keysReserve.setBalanceCents(keysReserve.getBalanceCents() - amountCents);
                fortification.setBalanceCents(fortification.getBalanceCents() + amountCents);

                treasuryAccountRepository.save(keysReserve);
                treasuryAccountRepository.save(fortification);

                recordMovement(keysReserve, fortification, amountCents,
                                MovementConcept.EXPIRED_KEYS_TO_FORTIFICATION, batchId, "KEY_EXPIRY_BATCH");

                long balanceAfter = keysReserve.getBalanceCents();
                if (balanceAfter < treasuryConfig.getKeysReserveCriticalThresholdCents()) {
                        log.error("[TREASURY] KEYS_RESERVE CRÍTICO tras vencimiento de llaves: saldo={}. "
                                        + "Recargar el fondo urgente.", balanceAfter);
                } else if (balanceAfter < treasuryConfig.getKeysReserveWarnThresholdCents()) {
                        log.warn("[TREASURY] KEYS_RESERVE bajo tras vencimiento de llaves: saldo={}.", balanceAfter);
                }

                log.info("[TREASURY] Vencimiento completado: KEYS_RESERVE={}, FORTIFICATION+={}",
                                keysReserve.getBalanceCents(), amountCents);
        }

        @Transactional(readOnly = true)
        @Override
        public TreasuryBalanceResponseDTO getBalanceReport() {
                TreasurySnapshot snap = getSnapshot();

                long warn = treasuryConfig.getKeysReserveWarnThresholdCents();
                long critical = treasuryConfig.getKeysReserveCriticalThresholdCents();

                String status;
                if (snap.keysReserveCents() < critical) {
                        status = "CRITICAL";
                } else if (snap.keysReserveCents() < warn) {
                        status = "WARNING";
                } else {
                        status = "OK";
                }

                List<TreasuryAccount> all = treasuryAccountRepository.findAll();
                boolean hasNegative = all.stream().anyMatch(a -> a.getBalanceCents() < 0);

                return new TreasuryBalanceResponseDTO(
                                snap.keysReserveCents(),
                                snap.fortificationCents(),
                                snap.operationsCents(),
                                snap.payoutsPendingCents(),
                                snap.taxReserveCents(),
                                snap.totalCents(),
                                snap.keysReserveHealthPct(),
                                status,
                                hasNegative);
        }

        @Transactional(readOnly = true)
        @Override
        public Page<TreasuryMovementResponseDTO> getMovements(TreasuryAccountCode code, Pageable pageable) {
                return treasuryMovementRepository.findByAccountCode(code, pageable)
                                .map(m -> new TreasuryMovementResponseDTO(
                                                m.getId(),
                                                m.getFromAccount().getCode().name(),
                                                m.getToAccount().getCode().name(),
                                                m.getAmountCents(),
                                                m.getConcept().name(),
                                                m.getReferenceId(),
                                                m.getReferenceType(),
                                                m.getCreatedAt()));
        }

        @Transactional(readOnly = true)
        @Override
        public void runReconciliation() {
                log.info("[RECONCILIATION] Iniciando reconciliación semanal de tesorería...");

                TreasurySnapshot snap = getSnapshot();
                long negativesCount = treasuryAccountRepository.countNegativeBalances();

                log.info("[RECONCILIATION] KEYS_RESERVE    → {} centavos", snap.keysReserveCents());
                log.info("[RECONCILIATION] FORTIFICATION   → {} centavos", snap.fortificationCents());
                log.info("[RECONCILIATION] OPERATIONS      → {} centavos", snap.operationsCents());
                log.info("[RECONCILIATION] PAYOUTS_PENDING → {} centavos", snap.payoutsPendingCents());
                log.info("[RECONCILIATION] TAX_RESERVE     → {} centavos", snap.taxReserveCents());
                log.info("[RECONCILIATION] TOTAL           → {} centavos", snap.totalCents());
                log.info("[RECONCILIATION] KEYS_RESERVE salud: {}% — estado: {}",
                                String.format("%.2f", snap.keysReserveHealthPct()),
                                snap.keysReserveCents() < treasuryConfig.getKeysReserveCriticalThresholdCents()
                                                ? "CRITICAL"
                                                : snap.keysReserveCents() < treasuryConfig.getKeysReserveWarnThresholdCents()
                                                                ? "WARNING"
                                                                : "OK");

                if (negativesCount > 0) {
                        log.error("[RECONCILIATION] ANOMALIA CRITICA: {} cuenta(s) con saldo negativo. " +
                                        "ACCION REQUERIDA INMEDIATA.", negativesCount);
                } else {
                        log.info("[RECONCILIATION] Reconciliacion completada sin anomalias.");
                }
        }

        private void validateAmount(Long amountCents) {
                if (amountCents == null || amountCents <= 0) {
                        throw new InvalidAmountException(
                                        "el monto debe ser positivo. Recibido: " + amountCents);
                }
        }
}

