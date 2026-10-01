package com.verygana2.services.plans;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.BudgetTransaction;
import com.verygana2.models.finance.plans.BudgetTransaction.TransactionType;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.finance.plans.BudgetTransactionRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link BudgetService}: el consumo del presupuesto publicitario vía
 * lock pesimista sobre el Wallet, la trazabilidad en BudgetTransaction por
 * cada método público, y la propagación de fondos insuficientes / wallet
 * agotado hacia InvestmentService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetService")
class BudgetServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private BudgetTransactionRepository transactionRepository;
    @Mock private InvestmentService investmentService;

    private BudgetService service;

    private static final Long COMMERCIAL_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new BudgetService(walletRepository, transactionRepository, investmentService);
    }

    private Wallet walletWithBalance(long balanceCents) {
        Wallet wallet = new Wallet();
        wallet.setBalanceCents(balanceCents);
        return wallet;
    }

    // ─── Métodos públicos → tipo/referenceId/description correctos ────────────

    @Nested
    @DisplayName("consumeForAdView")
    class ConsumeForAdView {

        @Test
        @DisplayName("genera un BudgetTransaction de tipo AD_VIEW con el referenceId dado")
        void generatesAdViewTransaction() {
            Wallet wallet = walletWithBalance(1000L);
            when(walletRepository.findByCommercialIdForUpdate(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            service.consumeForAdView(COMMERCIAL_ID, 100L, "ad-view-1");

            ArgumentCaptor<BudgetTransaction> captor = ArgumentCaptor.forClass(BudgetTransaction.class);
            verify(transactionRepository).save(captor.capture());
            BudgetTransaction tx = captor.getValue();
            assertThat(tx.getType()).isEqualTo(TransactionType.AD_VIEW);
            assertThat(tx.getReferenceId()).isEqualTo("ad-view-1");
            assertThat(tx.getDescription()).isEqualTo("Costo por visualización de anuncio");
            assertThat(tx.getAmountCents()).isEqualTo(100L);
            assertThat(tx.getWallet()).isSameAs(wallet);
        }
    }

    @Nested
    @DisplayName("consumeForGameReward")
    class ConsumeForGameReward {

        @Test
        @DisplayName("genera un BudgetTransaction de tipo GAME_REWARD con el referenceId dado")
        void generatesGameRewardTransaction() {
            Wallet wallet = walletWithBalance(1000L);
            when(walletRepository.findByCommercialIdForUpdate(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            service.consumeForGameReward(COMMERCIAL_ID, 200L, "game-session-1");

            ArgumentCaptor<BudgetTransaction> captor = ArgumentCaptor.forClass(BudgetTransaction.class);
            verify(transactionRepository).save(captor.capture());
            BudgetTransaction tx = captor.getValue();
            assertThat(tx.getType()).isEqualTo(TransactionType.GAME_REWARD);
            assertThat(tx.getReferenceId()).isEqualTo("game-session-1");
            assertThat(tx.getDescription()).isEqualTo("Recompensa por sesión de juego branded");
        }
    }

    @Nested
    @DisplayName("applyManualAdjustment")
    class ApplyManualAdjustment {

        @Test
        @DisplayName("genera un BudgetTransaction de tipo MANUAL_ADJUSTMENT sin referenceId, con la descripción dada")
        void generatesManualAdjustmentTransaction() {
            Wallet wallet = walletWithBalance(1000L);
            when(walletRepository.findByCommercialIdForUpdate(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            service.applyManualAdjustment(COMMERCIAL_ID, 50L, "Ajuste por error de facturación");

            ArgumentCaptor<BudgetTransaction> captor = ArgumentCaptor.forClass(BudgetTransaction.class);
            verify(transactionRepository).save(captor.capture());
            BudgetTransaction tx = captor.getValue();
            assertThat(tx.getType()).isEqualTo(TransactionType.MANUAL_ADJUSTMENT);
            assertThat(tx.getReferenceId()).isNull();
            assertThat(tx.getDescription()).isEqualTo("Ajuste por error de facturación");
        }
    }

    @Nested
    @DisplayName("consumeForBrandingRequest")
    class ConsumeForBrandingRequest {

        @Test
        @DisplayName("genera un BudgetTransaction de tipo BRANDING_REQUEST con el referenceId dado")
        void generatesBrandingRequestTransaction() {
            Wallet wallet = walletWithBalance(1000L);
            when(walletRepository.findByCommercialIdForUpdate(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            service.consumeForBrandingRequest(COMMERCIAL_ID, 300L, "branding-req-1");

            ArgumentCaptor<BudgetTransaction> captor = ArgumentCaptor.forClass(BudgetTransaction.class);
            verify(transactionRepository).save(captor.capture());
            BudgetTransaction tx = captor.getValue();
            assertThat(tx.getType()).isEqualTo(TransactionType.BRANDING_REQUEST);
            assertThat(tx.getReferenceId()).isEqualTo("branding-req-1");
            assertThat(tx.getDescription()).isEqualTo("Presupuesto reservado para solicitud de juego branded");
        }
    }

    // ─── Lock pesimista / wallet inexistente ───────────────────────────────────

    @Nested
    @DisplayName("consume (vía cualquier método público) — wallet inexistente")
    class WalletNotFound {

        @Test
        @DisplayName("sin wallet: lanza IllegalArgumentException sin consumir ni persistir nada")
        void noWallet_throwsIllegalArgumentException() {
            when(walletRepository.findByCommercialIdForUpdate(COMMERCIAL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.consumeForAdView(COMMERCIAL_ID, 100L, "ref"))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(walletRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
            verifyNoInteractions(investmentService);
        }

        @Test
        @DisplayName("usa el lock pesimista findByCommercialIdForUpdate, no findByCommercialId")
        void usesLockedLookup() {
            Wallet wallet = walletWithBalance(1000L);
            when(walletRepository.findByCommercialIdForUpdate(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            service.consumeForAdView(COMMERCIAL_ID, 100L, "ref");

            verify(walletRepository).findByCommercialIdForUpdate(COMMERCIAL_ID);
            verify(walletRepository, never()).findByCommercialId(any());
        }
    }

    // ─── Fondos insuficientes ───────────────────────────────────────────────────

    @Nested
    @DisplayName("consume — fondos insuficientes")
    class InsufficientFunds {

        @Test
        @DisplayName("saldo menor al monto: propaga InsufficientFundsException sin persistir transacción")
        void notEnoughBalance_propagatesExceptionWithoutPersisting() {
            Wallet wallet = walletWithBalance(50L);
            when(walletRepository.findByCommercialIdForUpdate(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() -> service.consumeForAdView(COMMERCIAL_ID, 100L, "ref"))
                    .isInstanceOf(InsufficientFundsException.class);

            assertThat(wallet.getBalanceCents()).isEqualTo(50L); // el saldo no cambió
            verify(walletRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
            verifyNoInteractions(investmentService);
        }
    }

    // ─── Consumo exitoso — agotamiento del wallet ──────────────────────────────

    @Nested
    @DisplayName("consume — consumo exitoso")
    class SuccessfulConsume {

        @Test
        @DisplayName("el consumo agota el wallet: notifica a InvestmentService.handleWalletExhausted")
        void exhaustsWallet_notifiesInvestmentService() {
            Wallet wallet = walletWithBalance(100L);
            when(walletRepository.findByCommercialIdForUpdate(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            service.consumeForAdView(COMMERCIAL_ID, 100L, "ref");

            assertThat(wallet.getBalanceCents()).isEqualTo(0L);
            verify(walletRepository).save(wallet);
            verify(investmentService).handleWalletExhausted(COMMERCIAL_ID);
        }

        @Test
        @DisplayName("el consumo NO agota el wallet: nunca notifica a InvestmentService")
        void doesNotExhaustWallet_neverNotifiesInvestmentService() {
            Wallet wallet = walletWithBalance(200L);
            when(walletRepository.findByCommercialIdForUpdate(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            service.consumeForAdView(COMMERCIAL_ID, 100L, "ref");

            assertThat(wallet.getBalanceCents()).isEqualTo(100L);
            verify(walletRepository).save(wallet);
            verify(investmentService, never()).handleWalletExhausted(any());
        }
    }
}
