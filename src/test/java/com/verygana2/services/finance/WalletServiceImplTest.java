package com.verygana2.services.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.verygana2.exceptions.financeExceptions.WalletAlreadyExistsException;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.services.interfaces.details.CommercialDetailsService;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletServiceImpl")
class WalletServiceImplTest {

    @Mock WalletRepository walletRepository;
    @Mock CommercialDetailsService commercialDetailsService;

    @InjectMocks WalletServiceImpl service;

    // ─── createFor ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createFor")
    class CreateFor {

        @Test
        @DisplayName("saves and returns new wallet for a commercial without one")
        void savesWalletWhenNotExists() {
            CommercialDetails commercial = new CommercialDetails();
            commercial.setId(1L);

            when(walletRepository.existsByCommercialId(1L)).thenReturn(false);
            when(commercialDetailsService.getCommercialById(1L)).thenReturn(commercial);
            when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Wallet result = service.createFor(1L);

            assertThat(result).isNotNull();
            verify(walletRepository).save(any(Wallet.class));
        }

        @Test
        @DisplayName("throws WalletAlreadyExistsException when commercial already has a wallet")
        void throwsWhenWalletAlreadyExists() {
            when(walletRepository.existsByCommercialId(5L)).thenReturn(true);

            assertThatThrownBy(() -> service.createFor(5L))
                    .isInstanceOf(WalletAlreadyExistsException.class)
                    .hasMessageContaining("5");
        }
    }

    // ─── getByCommercialId ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getByCommercialId")
    class GetByCommercialId {

        @Test
        @DisplayName("returns wallet when commercial has one")
        void returnsWallet() {
            CommercialDetails commercial = new CommercialDetails();
            commercial.setId(3L);
            Wallet wallet = Wallet.createFor(commercial);

            when(walletRepository.findByCommercialId(3L)).thenReturn(Optional.of(wallet));

            Wallet result = service.getByCommercialId(3L);

            assertThat(result).isSameAs(wallet);
        }

        @Test
        @DisplayName("throws EntityNotFoundException when no wallet found")
        void throwsEntityNotFoundWhenMissing() {
            when(walletRepository.findByCommercialId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByCommercialId(99L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null commercial ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getByCommercialId(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for zero commercial ID")
        void throwsOnZeroId() {
            assertThatThrownBy(() -> service.getByCommercialId(0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for negative commercial ID")
        void throwsOnNegativeId() {
            assertThatThrownBy(() -> service.getByCommercialId(-10L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── Unimplemented methods ────────────────────────────────────────────────

    @Nested
    @DisplayName("unimplemented methods throw UnsupportedOperationException")
    class UnimplementedMethods {

        @Test
        @DisplayName("getMyWallet throws UnsupportedOperationException")
        void getMyWalletThrows() {
            assertThatThrownBy(() -> service.getMyWallet(1L))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("initiateDeposit throws UnsupportedOperationException")
        void initiateDepositThrows() {
            assertThatThrownBy(() -> service.initiateDeposit(1L, null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("requestWithdrawal throws UnsupportedOperationException")
        void requestWithdrawalThrows() {
            assertThatThrownBy(() -> service.requestWithdrawal(1L, null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("getTransactions throws UnsupportedOperationException")
        void getTransactionsThrows() {
            assertThatThrownBy(() -> service.getTransactions(1L, PageRequest.of(0, 10)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("getPayouts throws UnsupportedOperationException")
        void getPayoutsThrows() {
            assertThatThrownBy(() -> service.getPayouts(1L, PageRequest.of(0, 10)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
