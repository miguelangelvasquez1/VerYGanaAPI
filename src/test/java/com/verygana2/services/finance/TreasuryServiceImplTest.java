package com.verygana2.services.finance;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.models.enums.finance.MovementConcept;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.finance.TreasuryAccount;
import com.verygana2.models.finance.TreasuryMovement;
import com.verygana2.models.records.KeyBacking;
import com.verygana2.models.records.TreasurySnapshot;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.finance.TreasuryAccountRepository;
import com.verygana2.repositories.finance.TreasuryMovementRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link TreasuryServiceImpl}: el libro contable de los 4 bolsillos
 * virtuales. Cada método mueve dinero entre cuentas y debe dejar registrado
 * el movimiento — aquí se verifica tanto la aritmética (los 3 montos de un
 * depósito siempre suman el total, sin perder centavos por redondeo) como
 * los invariantes de negocio (nunca dejar una cuenta en negativo).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TreasuryServiceImpl")
class TreasuryServiceImplTest {

    @Mock private TreasuryAccountRepository treasuryAccountRepository;
    @Mock private TreasuryMovementRepository treasuryMovementRepository;
    @Mock private KeyWalletRepository keyWalletRepository;
    @Mock private KeyBackingCalculator keyBackingCalculator;
    @Mock private TreasuryConfig treasuryConfig;

    private TreasuryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TreasuryServiceImpl(
                treasuryAccountRepository, treasuryMovementRepository, keyWalletRepository,
                keyBackingCalculator, treasuryConfig);
    }

    private TreasuryAccount account(TreasuryAccountCode code, long balance) {
        return TreasuryAccount.builder().code(code).balanceCents(balance).build();
    }

    private void stubAccount(TreasuryAccountCode code, long balance) {
        when(treasuryAccountRepository.findByCodeForUpdate(code)).thenReturn(Optional.of(account(code, balance)));
    }

    private CommercialDetails commercial(Long id) {
        CommercialDetails c = new CommercialDetails();
        c.setId(id);
        return c;
    }

    @Nested
    @DisplayName("distributeDeposit")
    class DistributeDeposit {

        @Test
        @DisplayName("distribuye 60/10/30 y los 3 montos suman exactamente el total (sin perder centavos por redondeo)")
        void splitsIntoThreeAccountsSummingExactTotal() {
            when(treasuryConfig.getKeysReservePct()).thenReturn(60);
            when(treasuryConfig.getFortificationPct()).thenReturn(10);
            stubAccount(TreasuryAccountCode.EXTERNAL_INCOME, 0);
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 0);
            stubAccount(TreasuryAccountCode.FORTIFICATION, 0);
            stubAccount(TreasuryAccountCode.OPERATIONS, 0);

            service.distributeDeposit(1_000_001L, commercial(1L), UUID.randomUUID()); // monto impar a propósito

            var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
            verify(treasuryAccountRepository, org.mockito.Mockito.times(3)).save(captor.capture());
            long sum = captor.getAllValues().stream().mapToLong(TreasuryAccount::getBalanceCents).sum();
            assertThat(sum).isEqualTo(1_000_001L); // OPERATIONS absorbió el residuo del redondeo
            verify(treasuryMovementRepository, org.mockito.Mockito.times(3)).save(any(TreasuryMovement.class));
        }

        @Test
        @DisplayName("monto no positivo: lanza IllegalArgumentException sin tocar ninguna cuenta")
        void nonPositiveAmount_throwsWithoutTouchingAccounts() {
            assertThatThrownBy(() -> service.distributeDeposit(0L, commercial(1L), UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(treasuryAccountRepository, org.mockito.Mockito.never()).findByCodeForUpdate(any());
        }
    }

    @Test
    @DisplayName("distributeSubscription: todo el monto va a OPERATIONS, ninguna otra cuenta se toca")
    void distributeSubscription_allGoesToOperations() {
        stubAccount(TreasuryAccountCode.EXTERNAL_INCOME, 0);
        stubAccount(TreasuryAccountCode.OPERATIONS, 500_000L);

        service.distributeSubscription(200_000L, commercial(1L), UUID.randomUUID());

        var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
        verify(treasuryAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getBalanceCents()).isEqualTo(700_000L);
    }

    @Nested
    @DisplayName("convertKeysToPayoutPending")
    class ConvertKeysToPayoutPending {

        @Test
        @DisplayName("saldo suficiente y por encima del umbral: mueve KEYS_RESERVE → PAYOUTS_PENDING")
        void sufficientBalance_movesFunds() {
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(2_000_000L);
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(10_000_000L);
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 50_000_000L);
            stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 0L);

            service.convertKeysToPayoutPending(1_000_000L, UUID.randomUUID());

            verify(treasuryAccountRepository, org.mockito.Mockito.times(2)).save(any());
        }

        @Test
        @DisplayName("saldo insuficiente en KEYS_RESERVE: lanza IllegalStateException")
        void insufficientBalance_throws() {
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 100L);
            stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 0L);

            assertThatThrownBy(() -> service.convertKeysToPayoutPending(1_000L, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("el saldo post-transacción caería bajo el umbral crítico: bloquea la operación")
        void wouldFallBelowCriticalThreshold_blocks() {
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(2_000_000L);
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 2_500_000L);
            stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 0L);

            // 2.500.000 - 1.000.000 = 1.500.000 < umbral crítico (2.000.000)
            assertThatThrownBy(() -> service.convertKeysToPayoutPending(1_000_000L, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("moveCashToPayoutPending: acredita PAYOUTS_PENDING desde la cuenta externa virtual")
    void moveCashToPayoutPending_creditsPayoutsPending() {
        stubAccount(TreasuryAccountCode.EXTERNAL_INCOME, 0L);
        stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 100_000L);

        service.moveCashToPayoutPending(50_000L, UUID.randomUUID());

        var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
        verify(treasuryAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getBalanceCents()).isEqualTo(150_000L);
    }

    @Nested
    @DisplayName("retainCommission")
    class RetainCommission {

        @Test
        @DisplayName("comisión positiva: mueve PAYOUTS_PENDING → OPERATIONS")
        void positiveCommission_movesFunds() {
            stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 100_000L);
            stubAccount(TreasuryAccountCode.OPERATIONS, 0L);

            service.retainCommission(10_000L, UUID.randomUUID(), "COPAYMENT");

            verify(treasuryAccountRepository, org.mockito.Mockito.times(2)).save(any());
        }

        @Test
        @DisplayName("comisión en cero: no hace nada, ni siquiera consulta las cuentas")
        void zeroCommission_doesNothing() {
            service.retainCommission(0L, UUID.randomUUID(), "COPAYMENT");

            verify(treasuryAccountRepository, org.mockito.Mockito.never()).findByCodeForUpdate(any());
        }

        @Test
        @DisplayName("PAYOUTS_PENDING sin saldo suficiente: lanza IllegalStateException")
        void insufficientPayoutsPending_throws() {
            stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 100L);
            stubAccount(TreasuryAccountCode.OPERATIONS, 0L);

            assertThatThrownBy(() -> service.retainCommission(1_000L, UUID.randomUUID(), "COPAYMENT"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("registerPayoutSent: debita PAYOUTS_PENDING; sin saldo lanza IllegalStateException")
    void registerPayoutSent_debitsPayoutsPending() {
        stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 100_000L);
        stubAccount(TreasuryAccountCode.EXTERNAL_INCOME, 0L);

        service.registerPayoutSent(60_000L, UUID.randomUUID());

        var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
        verify(treasuryAccountRepository, org.mockito.Mockito.times(1)).save(captor.capture());
        assertThat(captor.getValue().getBalanceCents()).isEqualTo(40_000L);
    }

    @Test
    @DisplayName("getSnapshot: suma los 4 saldos y calcula el total")
    void getSnapshot_sumsAllFourBalances() {
        when(treasuryAccountRepository.findByCode(TreasuryAccountCode.KEYS_RESERVE))
                .thenReturn(Optional.of(account(TreasuryAccountCode.KEYS_RESERVE, 100L)));
        when(treasuryAccountRepository.findByCode(TreasuryAccountCode.FORTIFICATION))
                .thenReturn(Optional.of(account(TreasuryAccountCode.FORTIFICATION, 50L)));
        when(treasuryAccountRepository.findByCode(TreasuryAccountCode.OPERATIONS))
                .thenReturn(Optional.of(account(TreasuryAccountCode.OPERATIONS, 30L)));
        when(treasuryAccountRepository.findByCode(TreasuryAccountCode.PAYOUTS_PENDING))
                .thenReturn(Optional.of(account(TreasuryAccountCode.PAYOUTS_PENDING, 20L)));

        TreasurySnapshot snapshot = service.getSnapshot();

        assertThat(snapshot.totalCents()).isEqualTo(200L);
    }

    @Test
    @DisplayName("moveExpiredKeysToFortification: si KEYS_RESERVE tiene menos de lo esperado, mueve solo lo disponible (nunca bloquea)")
    void moveExpiredKeysToFortification_movesOnlyAvailableIfInsufficient() {
        stubAccount(TreasuryAccountCode.KEYS_RESERVE, 500L); // menos de lo pedido
        stubAccount(TreasuryAccountCode.FORTIFICATION, 0L);

        service.moveExpiredKeysToFortification(1_000L, UUID.randomUUID());

        var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
        verify(treasuryAccountRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        // KEYS_RESERVE queda en 0 (se movió lo disponible, no lo pedido)
        assertThat(captor.getAllValues().get(0).getBalanceCents()).isZero();
    }

    @Nested
    @DisplayName("runReconciliation — solvencia del fondo de llaves")
    class RunReconciliation {

        private ListAppender<ILoggingEvent> appender;
        private Logger logger;

        @BeforeEach
        void attachAppender() {
            logger = (Logger) LoggerFactory.getLogger(TreasuryServiceImpl.class);
            appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
        }

        @org.junit.jupiter.api.AfterEach
        void detachAppender() {
            logger.detachAppender(appender);
        }

        /** Deja los 4 saldos listos para getSnapshot() y sin cuentas en negativo. */
        private void stubSnapshot(long keysReserve) {
            when(treasuryAccountRepository.findByCode(TreasuryAccountCode.KEYS_RESERVE))
                    .thenReturn(Optional.of(account(TreasuryAccountCode.KEYS_RESERVE, keysReserve)));
            when(treasuryAccountRepository.findByCode(TreasuryAccountCode.FORTIFICATION))
                    .thenReturn(Optional.of(account(TreasuryAccountCode.FORTIFICATION, 0L)));
            when(treasuryAccountRepository.findByCode(TreasuryAccountCode.OPERATIONS))
                    .thenReturn(Optional.of(account(TreasuryAccountCode.OPERATIONS, 0L)));
            when(treasuryAccountRepository.findByCode(TreasuryAccountCode.PAYOUTS_PENDING))
                    .thenReturn(Optional.of(account(TreasuryAccountCode.PAYOUTS_PENDING, 0L)));
            when(treasuryAccountRepository.countNegativeBalances()).thenReturn(0L);
        }

        /** El lado contabilizado de la identidad: saldos y presupuesto comprometido. */
        private void stubAccountedSides(long advertiserBalance, long committedAds, long committedSurveys) {
            when(keyBackingCalculator.compute(org.mockito.ArgumentMatchers.anyLong()))
                    .thenAnswer(inv -> new KeyBacking(
                            inv.getArgument(0),
                            keyWalletRepository.sumLiveKeyLiabilityCents(),
                            advertiserBalance, committedAds, committedSurveys, 0L, 0L));
        }

        private boolean loggedErrorContaining(String fragment) {
            return appender.list.stream()
                    .filter(e -> e.getLevel() == Level.ERROR)
                    .anyMatch(e -> e.getFormattedMessage().contains(fragment));
        }

        @Test
        @DisplayName("KEYS_RESERVE por debajo del pasivo de llaves vivas: reporta anomalía crítica con el déficit")
        void reserveBelowLiability_reportsCriticalAnomaly() {
            stubSnapshot(1_000L);
            stubAccountedSides(0L, 0L, 0L);
            when(keyWalletRepository.sumLiveKeyLiabilityCents()).thenReturn(1_500L);

            service.runReconciliation();

            // El déficit exacto debe aparecer: son las llaves emitidas sin respaldo.
            assertThat(loggedErrorContaining("500")).isTrue();
            assertThat(loggedErrorContaining("llaves emitidas sin respaldo")).isTrue();
            assertThat(appender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("sin anomalias"))).isFalse();
        }

        @Test
        @DisplayName("identidad cuadrada (pasivo + saldos + comprometido = reserva): no reporta anomalía")
        void reserveCoversLiability_reportsNoAnomaly() {
            stubSnapshot(2_000L);
            // 1.500 en llaves + 300 de saldo + 200 comprometido = 2.000
            stubAccountedSides(300L, 150L, 50L);
            when(keyWalletRepository.sumLiveKeyLiabilityCents()).thenReturn(1_500L);

            service.runReconciliation();

            assertThat(loggedErrorContaining("sin respaldo")).isFalse();
            assertThat(appender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("sin anomalias"))).isTrue();
        }

        @Test
        @DisplayName("respaldo exacto (reserva == pasivo): no es anomalía")
        void exactBacking_isNotAnomaly() {
            stubSnapshot(1_500L);
            stubAccountedSides(0L, 0L, 0L);
            when(keyWalletRepository.sumLiveKeyLiabilityCents()).thenReturn(1_500L);

            service.runReconciliation();

            assertThat(loggedErrorContaining("sin respaldo")).isFalse();
        }


        @Test
        @DisplayName("sobra respaldo (llaves que salieron sin debitar el fondo): lo reporta con el monto")
        void surplusBacking_isReported() {
            stubSnapshot(5_000L);
            // 1.000 en llaves + 1.000 de saldo, pero el fondo tiene 5.000:
            // 3.000 salieron de circulación sin debitar KEYS_RESERVE (p.ej. pet games).
            stubAccountedSides(1_000L, 0L, 0L);
            when(keyWalletRepository.sumLiveKeyLiabilityCents()).thenReturn(1_000L);

            service.runReconciliation();

            assertThat(appender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("excede lo contabilizado en 3000")))
                    .isTrue();
        }

        @Test
        @DisplayName("el presupuesto comprometido cuenta: un anuncio vivo no es un descuadre")
        void committedBudgetCountsTowardsIdentity() {
            stubSnapshot(10_000L);
            // Anunciante depositó: 4.000 sin gastar + 6.000 comprometidos en anuncios vivos.
            stubAccountedSides(4_000L, 6_000L, 0L);
            when(keyWalletRepository.sumLiveKeyLiabilityCents()).thenReturn(0L);

            service.runReconciliation();

            assertThat(appender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("sin anomalias"))).isTrue();
        }

        @Test
        @DisplayName("sin llaves en circulación: no divide por cero al calcular el porcentaje de respaldo")
        void zeroLiability_doesNotDivideByZero() {
            stubSnapshot(1_000L);
            stubAccountedSides(1_000L, 0L, 0L);
            when(keyWalletRepository.sumLiveKeyLiabilityCents()).thenReturn(0L);

            service.runReconciliation();

            assertThat(appender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("sin llaves en circulacion"))).isTrue();
            assertThat(loggedErrorContaining("sin respaldo")).isFalse();
        }
    }

    @Nested
    @DisplayName("settleKeyIssuance")
    class SettleKeyIssuance {

        @Test
        @DisplayName("sobrante (multiplicador < 1): mueve solo la diferencia de KEYS_RESERVE a OPERATIONS")
        void surplus_movesOnlyTheDifferenceToOperations() {
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(0L);
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 100_000L);
            stubAccount(TreasuryAccountCode.OPERATIONS, 0L);

            // financiado 10.000, emitido 7.000 (ORO ×0.7) → sobran 3.000
            service.settleKeyIssuance(10_000L, 7_000L, UUID.randomUUID(), "AD_LIKE");

            var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
            verify(treasuryAccountRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            // La base (7.000) se queda en KEYS_RESERVE respaldando las llaves emitidas.
            assertThat(captor.getAllValues().get(0).getBalanceCents()).isEqualTo(97_000L);
            assertThat(captor.getAllValues().get(1).getBalanceCents()).isEqualTo(3_000L);

            var mov = org.mockito.ArgumentCaptor.forClass(TreasuryMovement.class);
            verify(treasuryMovementRepository).save(mov.capture());
            assertThat(mov.getValue().getAmountCents()).isEqualTo(3_000L);
            assertThat(mov.getValue().getConcept())
                    .isEqualTo(MovementConcept.KEYS_ISSUANCE_SURPLUS_TO_OPERATIONS);
        }

        @Test
        @DisplayName("multiplicador 1.0 (financiado == emitido): no toca cuentas ni ensucia el libro contable")
        void neutralMultiplier_isNoOp() {
            service.settleKeyIssuance(10_000L, 10_000L, UUID.randomUUID(), "AD_LIKE");

            org.mockito.Mockito.verifyNoInteractions(treasuryAccountRepository);
            org.mockito.Mockito.verifyNoInteractions(treasuryMovementRepository);
        }

        @Test
        @DisplayName("déficit (multiplicador > 1): OPERATIONS financia el exceso hacia KEYS_RESERVE")
        void deficit_operationsFundsTheExcess() {
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 100_000L);
            stubAccount(TreasuryAccountCode.OPERATIONS, 50_000L);

            // financiado 10.000, emitido 12.000 (boost ×1.2) → faltan 2.000
            service.settleKeyIssuance(10_000L, 12_000L, UUID.randomUUID(), "AD_LIKE");

            var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
            verify(treasuryAccountRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            assertThat(captor.getAllValues().get(0).getBalanceCents()).isEqualTo(48_000L);  // OPERATIONS
            assertThat(captor.getAllValues().get(1).getBalanceCents()).isEqualTo(102_000L); // KEYS_RESERVE

            var mov = org.mockito.ArgumentCaptor.forClass(TreasuryMovement.class);
            verify(treasuryMovementRepository).save(mov.capture());
            assertThat(mov.getValue().getAmountCents()).isEqualTo(2_000L);
            assertThat(mov.getValue().getConcept())
                    .isEqualTo(MovementConcept.KEYS_ISSUANCE_DEFICIT_FUNDING);
        }

        @Test
        @DisplayName("déficit sin fondos en OPERATIONS: falla cerrado en vez de emitir llaves sin respaldo")
        void deficit_withoutOperationsFunds_throws() {
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 100_000L);
            stubAccount(TreasuryAccountCode.OPERATIONS, 500L);

            assertThatThrownBy(() -> service.settleKeyIssuance(10_000L, 12_000L, UUID.randomUUID(), "AD_LIKE"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("OPERATIONS");

            verify(treasuryAccountRepository, org.mockito.Mockito.never()).save(any(TreasuryAccount.class));
            verify(treasuryMovementRepository, org.mockito.Mockito.never()).save(any(TreasuryMovement.class));
        }

        @Test
        @DisplayName("montos negativos: rechaza sin tocar ninguna cuenta")
        void negativeAmounts_throwWithoutTouchingAccounts() {
            assertThatThrownBy(() -> service.settleKeyIssuance(-1L, 100L, UUID.randomUUID(), "AD_LIKE"))
                    .isInstanceOf(IllegalArgumentException.class);

            org.mockito.Mockito.verifyNoInteractions(treasuryAccountRepository);
        }
    }

    @Nested
    @DisplayName("getBalanceReport — respaldo real vs. reparto")
    class BalanceReportBacking {

        private void stubAllAccounts(long keysReserve) {
            when(treasuryAccountRepository.findByCode(TreasuryAccountCode.KEYS_RESERVE))
                    .thenReturn(Optional.of(account(TreasuryAccountCode.KEYS_RESERVE, keysReserve)));
            when(treasuryAccountRepository.findByCode(TreasuryAccountCode.FORTIFICATION))
                    .thenReturn(Optional.of(account(TreasuryAccountCode.FORTIFICATION, 0L)));
            when(treasuryAccountRepository.findByCode(TreasuryAccountCode.OPERATIONS))
                    .thenReturn(Optional.of(account(TreasuryAccountCode.OPERATIONS, 0L)));
            when(treasuryAccountRepository.findByCode(TreasuryAccountCode.PAYOUTS_PENDING))
                    .thenReturn(Optional.of(account(TreasuryAccountCode.PAYOUTS_PENDING, 0L)));
            when(treasuryAccountRepository.findAll()).thenReturn(java.util.List.of());
        }

        @Test
        @DisplayName("saldo holgado pero por debajo del pasivo: CRITICAL, aunque supere los umbrales")
        void underBacked_isCriticalEvenAboveThresholds() {
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(1_000L);
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(500L);
            stubAllAccounts(50_000L);                       // muy por encima de los umbrales
            when(keyWalletRepository.sumLiveKeyLiabilityCents()).thenReturn(80_000L);

            var report = service.getBalanceReport();

            assertThat(report.keysReserveStatus()).isEqualTo("CRITICAL");
            assertThat(report.keyLiabilityCents()).isEqualTo(80_000L);
            assertThat(report.keysBackingPct()).isEqualTo(62.5); // 50.000 / 80.000
        }

        @Test
        @DisplayName("reserva cubre el pasivo y supera umbrales: OK con respaldo por encima de 100%")
        void fullyBacked_isOk() {
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(1_000L);
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(500L);
            stubAllAccounts(100_000L);
            when(keyWalletRepository.sumLiveKeyLiabilityCents()).thenReturn(80_000L);

            var report = service.getBalanceReport();

            assertThat(report.keysReserveStatus()).isEqualTo("OK");
            assertThat(report.keysBackingPct()).isEqualTo(125.0);
        }

        @Test
        @DisplayName("sin llaves en circulación: respaldo 100%, no división por cero")
        void zeroLiability_reportsFullBacking() {
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(1_000L);
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(500L);
            stubAllAccounts(10_000L);
            when(keyWalletRepository.sumLiveKeyLiabilityCents()).thenReturn(0L);

            var report = service.getBalanceReport();

            assertThat(report.keysBackingPct()).isEqualTo(100.0);
            assertThat(report.keysReserveStatus()).isEqualTo("OK");
        }
    }

    @Nested
    @DisplayName("registerPetGameSpend")
    class RegisterPetGameSpend {

        @Test
        @DisplayName("mueve el valor de las llaves consumidas de KEYS_RESERVE a OPERATIONS")
        void movesSpentKeysToOperations() {
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(0L);
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 100_000L);
            stubAccount(TreasuryAccountCode.OPERATIONS, 0L);

            service.registerPetGameSpend(30_000L, UUID.randomUUID());

            var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
            verify(treasuryAccountRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            assertThat(captor.getAllValues().get(0).getBalanceCents()).isEqualTo(70_000L);
            assertThat(captor.getAllValues().get(1).getBalanceCents()).isEqualTo(30_000L);

            var mov = org.mockito.ArgumentCaptor.forClass(TreasuryMovement.class);
            verify(treasuryMovementRepository).save(mov.capture());
            assertThat(mov.getValue().getConcept())
                    .isEqualTo(MovementConcept.PET_GAME_KEYS_TO_OPERATIONS);
            // No va a PAYOUTS_PENDING: esas ventas no generan payout al comercial.
            assertThat(mov.getValue().getToAccount().getCode()).isEqualTo(TreasuryAccountCode.OPERATIONS);
        }

        @Test
        @DisplayName("KEYS_RESERVE insuficiente: falla en vez de dejar el fondo en negativo")
        void insufficientReserve_throws() {
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 100L);
            stubAccount(TreasuryAccountCode.OPERATIONS, 0L);

            assertThatThrownBy(() -> service.registerPetGameSpend(30_000L, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class);

            verify(treasuryAccountRepository, org.mockito.Mockito.never()).save(any(TreasuryAccount.class));
        }

        @Test
        @DisplayName("monto no positivo: no-op")
        void nonPositive_isNoOp() {
            service.registerPetGameSpend(0L, UUID.randomUUID());
            org.mockito.Mockito.verifyNoInteractions(treasuryAccountRepository);
        }
    }
}
