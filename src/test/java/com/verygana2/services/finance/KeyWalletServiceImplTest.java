package com.verygana2.services.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeyWalletServiceImpl")
class KeyWalletServiceImplTest {

    @Mock KeyWalletRepository keyWalletRepository;
    @Mock ConsumerDetailsService consumerDetailsService;

    @InjectMocks KeyWalletServiceImpl service;

    // ─── createFor ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createFor")
    class CreateFor {

        @Test
        @DisplayName("saves new wallet when consumer does not have one")
        void savesWalletWhenNotExists() {
            ConsumerDetails consumer = new ConsumerDetails();
            consumer.setId(1L);

            when(keyWalletRepository.existsByConsumerId(1L)).thenReturn(false);
            when(consumerDetailsService.getConsumerById(1L)).thenReturn(consumer);
            when(keyWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.createFor(1L);

            verify(keyWalletRepository).save(any(KeyWallet.class));
        }

        @Test
        @DisplayName("does not create a second wallet when one already exists")
        void doesNotDuplicateWallet() {
            when(keyWalletRepository.existsByConsumerId(5L)).thenReturn(true);

            service.createFor(5L);

            verify(keyWalletRepository, never()).save(any());
            verify(consumerDetailsService, never()).getConsumerById(any());
        }

        @Test
        @DisplayName("created wallet is linked to the correct consumer")
        void walletLinkedToConsumer() {
            ConsumerDetails consumer = new ConsumerDetails();
            consumer.setId(7L);

            when(keyWalletRepository.existsByConsumerId(7L)).thenReturn(false);
            when(consumerDetailsService.getConsumerById(7L)).thenReturn(consumer);

            KeyWallet saved = KeyWallet.builder()
                    .id(UUID.randomUUID())
                    .consumer(consumer)
                    .purchaseKeys(0L)
                    .connectivityKeys(0L)
                    .build();
            when(keyWalletRepository.save(any())).thenReturn(saved);

            service.createFor(7L);

            verify(keyWalletRepository).save(any(KeyWallet.class));
            // Consumer was fetched with the correct ID
            verify(consumerDetailsService).getConsumerById(7L);
        }
    }

    // ─── getByConsumerId ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getByConsumerId")
    class GetByConsumerId {

        @Test
        @DisplayName("returns wallet when consumer exists")
        void returnsWallet() {
            ConsumerDetails consumer = new ConsumerDetails();
            consumer.setId(3L);
            KeyWallet wallet = KeyWallet.builder()
                    .id(UUID.randomUUID())
                    .consumer(consumer)
                    .purchaseKeys(10L)
                    .connectivityKeys(5L)
                    .build();

            when(keyWalletRepository.findByConsumerId(3L)).thenReturn(Optional.of(wallet));

            KeyWallet result = service.getByConsumerId(3L);

            assertThat(result).isSameAs(wallet);
            assertThat(result.getPurchaseKeys()).isEqualTo(10L);
        }

        @Test
        @DisplayName("throws EntityNotFoundException when wallet not found")
        void throwsEntityNotFoundWhenMissing() {
            when(keyWalletRepository.findByConsumerId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByConsumerId(99L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null consumer ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getByConsumerId(null))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(keyWalletRepository, never()).findByConsumerId(any());
        }

        @Test
        @DisplayName("throws IllegalArgumentException for zero consumer ID")
        void throwsOnZeroId() {
            assertThatThrownBy(() -> service.getByConsumerId(0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for negative consumer ID")
        void throwsOnNegativeId() {
            assertThatThrownBy(() -> service.getByConsumerId(-1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
