package com.verygana2.services.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.treasury.TreasuryBalanceResponseDTO;
import com.verygana2.models.enums.finance.MovementConcept;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.finance.TreasuryAccount;
import com.verygana2.models.finance.TreasuryMovement;
import com.verygana2.models.records.TreasurySnapshot;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.TreasuryAccountRepository;
import com.verygana2.repositories.finance.TreasuryMovementRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("TreasuryServiceImpl")
class TreasuryServiceImplTest {

    @Mock TreasuryAccountRepository accountRepository;
    @Mock TreasuryMovementRepository movementRepository;
    @Mock TreasuryConfig treasuryConfig;

    @InjectMocks TreasuryServiceImpl service;

    @Mock CommercialDetails commercial;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private TreasuryAccount account(TreasuryAccountCode code, long balance) {
        return TreasuryAccount.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(code.name())
                .balanceCents(balance)
                .build();
    }

    private void stubForUpdate(TreasuryAccountCode code, TreasuryAccount acc) {
        when(accountRepository.findByCodeForUpdate(code)).thenReturn(Optional.of(acc));
    }

    private void stubFindByCode(TreasuryAccountCode code, TreasuryAccount acc) {
        when(accountRepository.findByCode(code)).thenReturn(Optional.of(acc));
    }

    private void stubDistributionPct(int keysPct, int fortPct) {
        // getOperationsPct() is never called — ops = deposit - keys - fort (remainder)
        when(treasuryConfig.getKeysReservePct()).thenReturn(keysPct);
        when(treasuryConfig.getFortificationPct()).thenReturn(fortPct);
    }

    // ─── distributeDeposit ────────────────────────────────────────────────────

    @Nested
    @DisplayName("distributeDeposit")
    class DistributeDeposit {

        @Test
        @DisplayName("splits amount with 60/10/30 and operations absorbs rounding residue")
        void splitsAmountCorrectly() {
            // 100_001 centavos: 60% = 60000, 10% = 10000, 30% = 30000 — exact sum
            // Use 100_003 to verify residue: 60% = 60001 (truncated), 10% = 10000, ops = 30002
            long deposit = 100_003L;
            TreasuryAccount external = account(TreasuryAccountCode.EXTERNAL_INCOME, 0);
            TreasuryAccount keys    = account(TreasuryAccountCode.KEYS_RESERVE, 0);
            TreasuryAccount fort    = account(TreasuryAccountCode.FORTIFICATION, 0);
            TreasuryAccount ops     = account(TreasuryAccountCode.OPERATIONS, 0);

            stubForUpdate(TreasuryAccountCode.EXTERNAL_INCOME, external);
            stubForUpdate(TreasuryAccountCode.KEYS_RESERVE, keys);
            stubForUpdate(TreasuryAccountCode.FORTIFICATION, fort);
            stubForUpdate(TreasuryAccountCode.OPERATIONS, ops);
            stubDistributionPct(60, 10);

            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(commercial.getId()).thenReturn(1L);

            service.distributeDeposit(deposit, commercial, UUID.randomUUID());

            long expectedKeys = deposit * 60 / 100;        // 60001
            long expectedFort = deposit * 10 / 100;        // 10000
            long expectedOps  = deposit - expectedKeys - expectedFort; // 30002

            assertThat(keys.getBalanceCents()).isEqualTo(expectedKeys);
            assertThat(fort.getBalanceCents()).isEqualTo(expectedFort);
            assertThat(ops.getBalanceCents()).isEqualTo(expectedOps);

            // All three parts must sum exactly to the original deposit
            assertThat(keys.getBalanceCents() + fort.getBalanceCents() + ops.getBalanceCents())
                    .isEqualTo(deposit);
        }

        @Test
        @DisplayName("records 3 treasury movements with correct concepts")
        void recordsThreeMovements() {
            TreasuryAccount external = account(TreasuryAccountCode.EXTERNAL_INCOME, 0);
            TreasuryAccount keys    = account(TreasuryAccountCode.KEYS_RESERVE, 0);
            TreasuryAccount fort    = account(TreasuryAccountCode.FORTIFICATION, 0);
            TreasuryAccount ops     = account(TreasuryAccountCode.OPERATIONS, 0);

            stubForUpdate(TreasuryAccountCode.EXTERNAL_INCOME, external);
            stubForUpdate(TreasuryAccountCode.KEYS_RESERVE, keys);
            stubForUpdate(TreasuryAccountCode.FORTIFICATION, fort);
            stubForUpdate(TreasuryAccountCode.OPERATIONS, ops);
            stubDistributionPct(60, 10);

            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(commercial.getId()).thenReturn(1L);

            service.distributeDeposit(100_000L, commercial, UUID.randomUUID());

            ArgumentCaptor<TreasuryMovement> captor = ArgumentCaptor.forClass(TreasuryMovement.class);
            verify(movementRepository, times(3)).save(captor.capture());

            List<MovementConcept> concepts = captor.getAllValues().stream()
                    .map(TreasuryMovement::getConcept).toList();

            assertThat(concepts).containsExactlyInAnyOrder(
                    MovementConcept.BUSINESS_DEPOSIT_KEYS,
                    MovementConcept.BUSINESS_DEPOSIT_FORTIFICATION,
                    MovementConcept.BUSINESS_DEPOSIT_OPERATIONS);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null amount")
        void throwsOnNullAmount() {
            when(commercial.getId()).thenReturn(1L);
            assertThatThrownBy(() -> service.distributeDeposit(null, commercial, UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for zero amount")
        void throwsOnZeroAmount() {
            when(commercial.getId()).thenReturn(1L);
            assertThatThrownBy(() -> service.distributeDeposit(0L, commercial, UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalStateException when account not found")
        void throwsWhenAccountNotFound() {
            when(treasuryConfig.getKeysReservePct()).thenReturn(60);
            when(treasuryConfig.getFortificationPct()).thenReturn(10);
            when(accountRepository.findByCodeForUpdate(any())).thenReturn(Optional.empty());
            when(commercial.getId()).thenReturn(1L);

            assertThatThrownBy(() -> service.distributeDeposit(1000L, commercial, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cuenta de tesorería no encontrada");
        }
    }

    // ─── distributeSubscription ───────────────────────────────────────────────

    @Nested
    @DisplayName("distributeSubscription")
    class DistributeSubscription {

        @Test
        @DisplayName("entire amount goes to OPERATIONS")
        void allGoesToOperations() {
            TreasuryAccount external = account(TreasuryAccountCode.EXTERNAL_INCOME, 0);
            TreasuryAccount ops      = account(TreasuryAccountCode.OPERATIONS, 5_000L);

            stubForUpdate(TreasuryAccountCode.EXTERNAL_INCOME, external);
            stubForUpdate(TreasuryAccountCode.OPERATIONS, ops);

            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(commercial.getId()).thenReturn(2L);

            service.distributeSubscription(20_000L, commercial, UUID.randomUUID());

            assertThat(ops.getBalanceCents()).isEqualTo(25_000L);
            verify(movementRepository, times(1)).save(
                    argThat(m -> m.getConcept() == MovementConcept.BASIC_PLAN_SUBSCRIPTION));
        }
    }

    // ─── convertKeysToPayoutPending ───────────────────────────────────────────

    @Nested
    @DisplayName("convertKeysToPayoutPending")
    class ConvertKeysToPayout {

        // No @BeforeEach — threshold getters are called unconditionally in the service only
        // after the initial balance check; throwsWhenInsufficientBalance throws before reaching them.

        @Test
        @DisplayName("moves balance from KEYS_RESERVE to PAYOUTS_PENDING")
        void movesBalance() {
            TreasuryAccount keys    = account(TreasuryAccountCode.KEYS_RESERVE, 20_000_000L);
            TreasuryAccount payouts = account(TreasuryAccountCode.PAYOUTS_PENDING, 0L);

            stubForUpdate(TreasuryAccountCode.KEYS_RESERVE, keys);
            stubForUpdate(TreasuryAccountCode.PAYOUTS_PENDING, payouts);
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(2_000_000L);
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(10_000_000L);
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.convertKeysToPayoutPending(1_000L, UUID.randomUUID());

            assertThat(keys.getBalanceCents()).isEqualTo(19_999_000L);
            assertThat(payouts.getBalanceCents()).isEqualTo(1_000L);
        }

        @Test
        @DisplayName("throws IllegalStateException when KEYS_RESERVE has insufficient balance")
        void throwsWhenInsufficientBalance() {
            // Throws at the initial balance check — threshold getters never reached, so not stubbed.
            TreasuryAccount keys    = account(TreasuryAccountCode.KEYS_RESERVE, 500L);
            TreasuryAccount payouts = account(TreasuryAccountCode.PAYOUTS_PENDING, 0L);
            stubForUpdate(TreasuryAccountCode.KEYS_RESERVE, keys);
            stubForUpdate(TreasuryAccountCode.PAYOUTS_PENDING, payouts);

            assertThatThrownBy(() -> service.convertKeysToPayoutPending(1_000L, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Saldo insuficiente en KEYS_RESERVE");
        }

        @Test
        @DisplayName("throws IllegalStateException when post-tx balance would be below critical threshold")
        void throwsWhenBelowCriticalThreshold() {
            // balance = 2_500_000 (above critical), but after deducting 1_000_000 → 1_500_000 < critical (2_000_000)
            TreasuryAccount keys    = account(TreasuryAccountCode.KEYS_RESERVE, 2_500_000L);
            TreasuryAccount payouts = account(TreasuryAccountCode.PAYOUTS_PENDING, 0L);
            stubForUpdate(TreasuryAccountCode.KEYS_RESERVE, keys);
            stubForUpdate(TreasuryAccountCode.PAYOUTS_PENDING, payouts);
            // Both getters are called unconditionally before the if-check
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(2_000_000L);
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(10_000_000L);

            assertThatThrownBy(() -> service.convertKeysToPayoutPending(1_000_000L, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("umbral crítico");
        }

        @Test
        @DisplayName("records movement with COPAYMENT_KEYS_CONVERSION concept")
        void recordsCorrectMovementConcept() {
            TreasuryAccount keys    = account(TreasuryAccountCode.KEYS_RESERVE, 20_000_000L);
            TreasuryAccount payouts = account(TreasuryAccountCode.PAYOUTS_PENDING, 0L);

            stubForUpdate(TreasuryAccountCode.KEYS_RESERVE, keys);
            stubForUpdate(TreasuryAccountCode.PAYOUTS_PENDING, payouts);
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(2_000_000L);
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(10_000_000L);
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.convertKeysToPayoutPending(1_000L, UUID.randomUUID());

            verify(movementRepository).save(
                    argThat(m -> m.getConcept() == MovementConcept.COPAYMENT_KEYS_CONVERSION));
        }
    }

    // ─── registerPayoutSent ───────────────────────────────────────────────────

    @Nested
    @DisplayName("registerPayoutSent")
    class RegisterPayoutSent {

        @Test
        @DisplayName("decreases PAYOUTS_PENDING balance")
        void decreasesPayoutsPending() {
            TreasuryAccount payouts  = account(TreasuryAccountCode.PAYOUTS_PENDING, 50_000L);
            TreasuryAccount external = account(TreasuryAccountCode.EXTERNAL_INCOME, 0L);

            stubForUpdate(TreasuryAccountCode.PAYOUTS_PENDING, payouts);
            stubForUpdate(TreasuryAccountCode.EXTERNAL_INCOME, external);
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.registerPayoutSent(20_000L, UUID.randomUUID());

            assertThat(payouts.getBalanceCents()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("throws when PAYOUTS_PENDING has insufficient balance")
        void throwsWhenInsufficientBalance() {
            TreasuryAccount payouts = account(TreasuryAccountCode.PAYOUTS_PENDING, 100L);
            stubForUpdate(TreasuryAccountCode.PAYOUTS_PENDING, payouts);

            assertThatThrownBy(() -> service.registerPayoutSent(50_000L, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PAYOUTS_PENDING");
        }
    }

    // ─── retainCommission ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("retainCommission")
    class RetainCommission {

        @Test
        @DisplayName("moves amount from PAYOUTS_PENDING to OPERATIONS")
        void movesFromPayoutsPendingToOperations() {
            TreasuryAccount payouts = account(TreasuryAccountCode.PAYOUTS_PENDING, 10_000L);
            TreasuryAccount ops     = account(TreasuryAccountCode.OPERATIONS, 0L);

            stubForUpdate(TreasuryAccountCode.PAYOUTS_PENDING, payouts);
            stubForUpdate(TreasuryAccountCode.OPERATIONS, ops);
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.retainCommission(2_000L, UUID.randomUUID(), "COPAYMENT");

            assertThat(payouts.getBalanceCents()).isEqualTo(8_000L);
            assertThat(ops.getBalanceCents()).isEqualTo(2_000L);
        }

        @Test
        @DisplayName("is a no-op when amount is zero")
        void noOpWhenZero() {
            service.retainCommission(0L, UUID.randomUUID(), "COPAYMENT");
            verify(accountRepository, never()).findByCodeForUpdate(any());
        }

        @Test
        @DisplayName("throws when PAYOUTS_PENDING is insufficient")
        void throwsWhenInsufficientBalance() {
            // Both accounts fetched before balance check — must stub both
            TreasuryAccount payouts = account(TreasuryAccountCode.PAYOUTS_PENDING, 100L);
            TreasuryAccount ops     = account(TreasuryAccountCode.OPERATIONS, 0L);
            stubForUpdate(TreasuryAccountCode.PAYOUTS_PENDING, payouts);
            stubForUpdate(TreasuryAccountCode.OPERATIONS, ops);

            assertThatThrownBy(() -> service.retainCommission(50_000L, UUID.randomUUID(), "COPAYMENT"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PAYOUTS_PENDING");
        }
    }

    // ─── moveExpiredKeysToFortification ───────────────────────────────────────

    @Nested
    @DisplayName("moveExpiredKeysToFortification")
    class MoveExpiredKeys {

        @BeforeEach
        void stubThresholds() {
            // Only critical is stubbed — test balances (0–50_000) are always below critical (2M),
            // so the warn branch (else-if) is never reached and would cause UnnecessaryStubbing.
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(2_000_000L);
        }

        @Test
        @DisplayName("moves amount from KEYS_RESERVE to FORTIFICATION")
        void movesCorrectly() {
            TreasuryAccount keys = account(TreasuryAccountCode.KEYS_RESERVE, 50_000L);
            TreasuryAccount fort = account(TreasuryAccountCode.FORTIFICATION, 10_000L);

            stubForUpdate(TreasuryAccountCode.KEYS_RESERVE, keys);
            stubForUpdate(TreasuryAccountCode.FORTIFICATION, fort);
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.moveExpiredKeysToFortification(20_000L, UUID.randomUUID());

            assertThat(keys.getBalanceCents()).isEqualTo(30_000L);
            assertThat(fort.getBalanceCents()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("moves only available amount when KEYS_RESERVE has less than requested")
        void movesAvailableWhenInsufficient() {
            TreasuryAccount keys = account(TreasuryAccountCode.KEYS_RESERVE, 5_000L);
            TreasuryAccount fort = account(TreasuryAccountCode.FORTIFICATION, 0L);

            stubForUpdate(TreasuryAccountCode.KEYS_RESERVE, keys);
            stubForUpdate(TreasuryAccountCode.FORTIFICATION, fort);
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Request 20_000 but only 5_000 available — should move 5_000 gracefully
            service.moveExpiredKeysToFortification(20_000L, UUID.randomUUID());

            assertThat(keys.getBalanceCents()).isEqualTo(0L);
            assertThat(fort.getBalanceCents()).isEqualTo(5_000L);
        }

        @Test
        @DisplayName("records movement with EXPIRED_KEYS_TO_FORTIFICATION concept")
        void recordsCorrectConcept() {
            TreasuryAccount keys = account(TreasuryAccountCode.KEYS_RESERVE, 50_000L);
            TreasuryAccount fort = account(TreasuryAccountCode.FORTIFICATION, 0L);

            stubForUpdate(TreasuryAccountCode.KEYS_RESERVE, keys);
            stubForUpdate(TreasuryAccountCode.FORTIFICATION, fort);
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.moveExpiredKeysToFortification(10_000L, UUID.randomUUID());

            verify(movementRepository).save(
                    argThat(m -> m.getConcept() == MovementConcept.EXPIRED_KEYS_TO_FORTIFICATION));
        }
    }

    // ─── getSnapshot ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSnapshot")
    class GetSnapshot {

        @Test
        @DisplayName("sums all four account balances into total")
        void sumsAllFourAccounts() {
            stubFindByCode(TreasuryAccountCode.KEYS_RESERVE,    account(TreasuryAccountCode.KEYS_RESERVE,    60_000L));
            stubFindByCode(TreasuryAccountCode.FORTIFICATION,   account(TreasuryAccountCode.FORTIFICATION,   10_000L));
            stubFindByCode(TreasuryAccountCode.OPERATIONS,      account(TreasuryAccountCode.OPERATIONS,      30_000L));
            stubFindByCode(TreasuryAccountCode.PAYOUTS_PENDING, account(TreasuryAccountCode.PAYOUTS_PENDING, 5_000L));

            TreasurySnapshot snap = service.getSnapshot();

            assertThat(snap.keysReserveCents()).isEqualTo(60_000L);
            assertThat(snap.fortificationCents()).isEqualTo(10_000L);
            assertThat(snap.operationsCents()).isEqualTo(30_000L);
            assertThat(snap.payoutsPendingCents()).isEqualTo(5_000L);
            assertThat(snap.totalCents()).isEqualTo(105_000L);
        }
    }

    // ─── getBalanceReport ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getBalanceReport")
    class GetBalanceReport {

        @BeforeEach
        void stubThresholds() {
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(2_000_000L);
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(10_000_000L);
        }

        private void stubBalances(long keysBalance) {
            stubFindByCode(TreasuryAccountCode.KEYS_RESERVE,    account(TreasuryAccountCode.KEYS_RESERVE,    keysBalance));
            stubFindByCode(TreasuryAccountCode.FORTIFICATION,   account(TreasuryAccountCode.FORTIFICATION,   0L));
            stubFindByCode(TreasuryAccountCode.OPERATIONS,      account(TreasuryAccountCode.OPERATIONS,      0L));
            stubFindByCode(TreasuryAccountCode.PAYOUTS_PENDING, account(TreasuryAccountCode.PAYOUTS_PENDING, 0L));
            when(accountRepository.findAll()).thenReturn(List.of(
                    account(TreasuryAccountCode.KEYS_RESERVE, keysBalance)));
        }

        @Test
        @DisplayName("returns OK status when KEYS_RESERVE above warn threshold")
        void returnsOkStatus() {
            stubBalances(50_000_000L);

            TreasuryBalanceResponseDTO report = service.getBalanceReport();

            assertThat(report.keysReserveStatus()).isEqualTo("OK");
            assertThat(report.hasNegativeBalance()).isFalse();
        }

        @Test
        @DisplayName("returns WARNING status when KEYS_RESERVE is between warn and critical thresholds")
        void returnsWarningStatus() {
            stubBalances(5_000_000L); // below warn (10M) but above critical (2M)

            TreasuryBalanceResponseDTO report = service.getBalanceReport();

            assertThat(report.keysReserveStatus()).isEqualTo("WARNING");
        }

        @Test
        @DisplayName("returns CRITICAL status when KEYS_RESERVE is below critical threshold")
        void returnsCriticalStatus() {
            stubBalances(1_000_000L); // below critical (2M)

            TreasuryBalanceResponseDTO report = service.getBalanceReport();

            assertThat(report.keysReserveStatus()).isEqualTo("CRITICAL");
        }

        @Test
        @DisplayName("detects negative balance when any account is negative")
        void detectsNegativeBalance() {
            stubFindByCode(TreasuryAccountCode.KEYS_RESERVE,    account(TreasuryAccountCode.KEYS_RESERVE,    50_000_000L));
            stubFindByCode(TreasuryAccountCode.FORTIFICATION,   account(TreasuryAccountCode.FORTIFICATION,   0L));
            stubFindByCode(TreasuryAccountCode.OPERATIONS,      account(TreasuryAccountCode.OPERATIONS,      0L));
            stubFindByCode(TreasuryAccountCode.PAYOUTS_PENDING, account(TreasuryAccountCode.PAYOUTS_PENDING, 0L));

            TreasuryAccount negativeAccount = account(TreasuryAccountCode.OPERATIONS, -100L);
            when(accountRepository.findAll()).thenReturn(List.of(negativeAccount));

            TreasuryBalanceResponseDTO report = service.getBalanceReport();

            assertThat(report.hasNegativeBalance()).isTrue();
        }
    }

    // ─── runReconciliation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("runReconciliation")
    class RunReconciliation {

        @Test
        @DisplayName("logs balances without throwing")
        void logsWithoutThrowing() {
            stubFindByCode(TreasuryAccountCode.KEYS_RESERVE,    account(TreasuryAccountCode.KEYS_RESERVE,    50_000_000L));
            stubFindByCode(TreasuryAccountCode.FORTIFICATION,   account(TreasuryAccountCode.FORTIFICATION,   10_000_000L));
            stubFindByCode(TreasuryAccountCode.OPERATIONS,      account(TreasuryAccountCode.OPERATIONS,      30_000_000L));
            stubFindByCode(TreasuryAccountCode.PAYOUTS_PENDING, account(TreasuryAccountCode.PAYOUTS_PENDING, 5_000_000L));
            when(accountRepository.countNegativeBalances()).thenReturn(0L);
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(2_000_000L);
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(10_000_000L);

            // Should not throw
            service.runReconciliation();

            verify(accountRepository, atLeastOnce()).findByCode(any());
            verify(accountRepository).countNegativeBalances();
        }

        @Test
        @DisplayName("queries for negative balances as anomaly detection")
        void checksForNegativeBalances() {
            stubFindByCode(TreasuryAccountCode.KEYS_RESERVE,    account(TreasuryAccountCode.KEYS_RESERVE,    50_000_000L));
            stubFindByCode(TreasuryAccountCode.FORTIFICATION,   account(TreasuryAccountCode.FORTIFICATION,   0L));
            stubFindByCode(TreasuryAccountCode.OPERATIONS,      account(TreasuryAccountCode.OPERATIONS,      0L));
            stubFindByCode(TreasuryAccountCode.PAYOUTS_PENDING, account(TreasuryAccountCode.PAYOUTS_PENDING, 0L));
            when(accountRepository.countNegativeBalances()).thenReturn(2L);
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(2_000_000L);
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(10_000_000L);

            service.runReconciliation(); // Should log error but not throw

            verify(accountRepository).countNegativeBalances();
        }
    }
}
