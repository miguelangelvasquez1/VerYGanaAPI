package com.verygana2.services.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.finance.responses.KeyTransactionResponseDTO;
import com.verygana2.mappers.finance.KeyTransactionMapper;
import com.verygana2.models.enums.finance.KeyTransactionType;
import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.repositories.finance.KeyTransactionRepository;

import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeyTransactionServiceImpl")
class KeyTransactionServiceImplTest {

    @Mock KeyTransactionRepository keyTransactionRepository;
    @Mock KeyTransactionMapper keyTransactionMapper;

    @InjectMocks KeyTransactionServiceImpl service;

    private KeyTransaction dummyTx() {
        return KeyTransaction.builder()
                .id(UUID.randomUUID())
                .purchaseKeysDelta(5L)
                .connectivityKeysDelta(0L)
                .build();
    }

    private KeyTransactionResponseDTO dummyDTO(UUID id) {
        return new KeyTransactionResponseDTO(id, null, 5L, 0L, null, null, null, null);
    }

    @Test
    @DisplayName("getByConsumerId delegates to repository and maps results")
    void getByConsumerId_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        KeyTransaction tx = dummyTx();
        KeyTransactionResponseDTO dto = dummyDTO(tx.getId());

        when(keyTransactionRepository.findByConsumerId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(tx)));
        when(keyTransactionMapper.toKeyTransactionResponseDTO(tx)).thenReturn(dto);

        Page<KeyTransactionResponseDTO> result = service.getByConsumerId(1L, pageable);

        assertThat(result.getContent()).containsExactly(dto);
        verify(keyTransactionRepository).findByConsumerId(1L, pageable);
        verify(keyTransactionMapper).toKeyTransactionResponseDTO(tx);
    }

    @Test
    @DisplayName("getByConsumerId returns empty page when no transactions")
    void getByConsumerId_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(keyTransactionRepository.findByConsumerId(99L, pageable))
                .thenReturn(Page.empty());

        Page<KeyTransactionResponseDTO> result = service.getByConsumerId(99L, pageable);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("getByConsumerIdAndType delegates with type filter to repository")
    void getByConsumerIdAndType_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 5);
        KeyTransaction tx = dummyTx();
        KeyTransactionResponseDTO dto = dummyDTO(tx.getId());

        when(keyTransactionRepository.findByConsumerIdAndType(2L, KeyTransactionType.CREDIT_INTERACTION, pageable))
                .thenReturn(new PageImpl<>(List.of(tx)));
        when(keyTransactionMapper.toKeyTransactionResponseDTO(tx)).thenReturn(dto);

        Page<KeyTransactionResponseDTO> result =
                service.getByConsumerIdAndType(2L, KeyTransactionType.CREDIT_INTERACTION, pageable);

        assertThat(result.getContent()).containsExactly(dto);
        verify(keyTransactionRepository)
                .findByConsumerIdAndType(2L, KeyTransactionType.CREDIT_INTERACTION, pageable);
    }

    @Test
    @DisplayName("getByConsumerIdAndType returns empty page when no matching transactions")
    void getByConsumerIdAndType_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(keyTransactionRepository.findByConsumerIdAndType(3L, KeyTransactionType.DEBIT_EXPIRY, pageable))
                .thenReturn(Page.empty());

        Page<KeyTransactionResponseDTO> result =
                service.getByConsumerIdAndType(3L, KeyTransactionType.DEBIT_EXPIRY, pageable);

        assertThat(result.isEmpty()).isTrue();
    }
}
