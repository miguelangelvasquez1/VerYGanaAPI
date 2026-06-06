package com.verygana2.services.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.raffle.responses.DrawProofResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleResultResponseDTO;
import com.verygana2.mappers.raffles.RaffleResultMapper;
import com.verygana2.exceptions.rafflesExceptions.InvalidOperationException;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.repositories.raffles.RaffleResultRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleResultServiceImpl")
class RaffleResultServiceImplTest {

    @Mock RaffleResultRepository raffleResultRepository;
    @Mock RaffleResultMapper raffleResultMapper;
    @Mock ObjectMapper objectMapper;

    @InjectMocks RaffleResultServiceImpl service;

    // ─── getByRaffleId ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getByRaffleId")
    class GetByRaffleId {

        @Test
        @DisplayName("throws IllegalArgumentException for null raffle ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getByRaffleId(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for zero raffle ID")
        void throwsOnZeroId() {
            assertThatThrownBy(() -> service.getByRaffleId(0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("returns RaffleResult when found")
        void returnsResult() {
            RaffleResult result = new RaffleResult();
            when(raffleResultRepository.findByRaffleId(1L)).thenReturn(Optional.of(result));

            RaffleResult found = service.getByRaffleId(1L);

            assertThat(found).isSameAs(result);
        }

        @Test
        @DisplayName("throws ObjectNotFoundException when not found")
        void throwsWhenNotFound() {
            when(raffleResultRepository.findByRaffleId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByRaffleId(99L))
                    .isInstanceOf(ObjectNotFoundException.class);
        }
    }

    // ─── getResultByRaffleId ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getResultByRaffleId")
    class GetResultByRaffleId {

        @Test
        @DisplayName("delegates to mapper and returns DTO")
        void returnsMappedDTO() {
            RaffleResult result = new RaffleResult();
            RaffleResultResponseDTO dto = new RaffleResultResponseDTO();

            when(raffleResultRepository.findByRaffleId(1L)).thenReturn(Optional.of(result));
            when(raffleResultMapper.toRaffleResultDTO(result)).thenReturn(dto);

            RaffleResultResponseDTO found = service.getResultByRaffleId(1L);

            assertThat(found).isSameAs(dto);
        }
    }

    // ─── getLastRaffleResults ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getLastRaffleResults")
    class GetLastRaffleResults {

        @Test
        @DisplayName("returns mapped list")
        void returnsMappedList() {
            RaffleResult r = new RaffleResult();
            com.verygana2.dtos.raffle.responses.RaffleSummaryResultResponseDTO dto =
                    new com.verygana2.dtos.raffle.responses.RaffleSummaryResultResponseDTO();

            when(raffleResultRepository.findLastRaffleResults()).thenReturn(List.of(r));
            when(raffleResultMapper.toRaffleSummaryResultResponseDTO(r)).thenReturn(dto);

            var results = service.getLastRaffleResults();

            assertThat(results).containsExactly(dto);
        }
    }

    // ─── getDrawProof ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getDrawProof")
    class GetDrawProof {

        @Test
        @DisplayName("throws InvalidOperationException when drawProof is null")
        void throwsWhenProofNull() {
            RaffleResult result = new RaffleResult();
            result.setDrawProof(null);
            when(raffleResultRepository.findByRaffleId(1L)).thenReturn(Optional.of(result));

            assertThatThrownBy(() -> service.getDrawProof(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("not available");
        }

        @Test
        @DisplayName("throws InvalidOperationException when drawProof is blank")
        void throwsWhenProofBlank() {
            RaffleResult result = new RaffleResult();
            result.setDrawProof("   ");
            when(raffleResultRepository.findByRaffleId(1L)).thenReturn(Optional.of(result));

            assertThatThrownBy(() -> service.getDrawProof(1L))
                    .isInstanceOf(InvalidOperationException.class);
        }

        @Test
        @DisplayName("throws InvalidOperationException when drawProof is malformed JSON")
        void throwsWhenProofMalformedJson() throws Exception {
            RaffleResult result = new RaffleResult();
            result.setDrawProof("{bad-json}");
            when(raffleResultRepository.findByRaffleId(1L)).thenReturn(Optional.of(result));
            when(objectMapper.readValue(eq("{bad-json}"), eq(DrawProofResponseDTO.class)))
                    .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "bad json"));

            assertThatThrownBy(() -> service.getDrawProof(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("malformed");
        }

        @Test
        @DisplayName("deserializes valid JSON drawProof and returns DTO")
        void returnsDeserializedProof() throws Exception {
            DrawProofResponseDTO proof = DrawProofResponseDTO.builder()
                    .raffleId(5L)
                    .raffleTitle("Test Raffle")
                    .numberOfWinners(1)
                    .winners(List.of())
                    .build();

            String proofJson = "{\"raffleId\":5}";

            RaffleResult result = new RaffleResult();
            result.setDrawProof(proofJson);
            when(raffleResultRepository.findByRaffleId(5L)).thenReturn(Optional.of(result));
            when(objectMapper.readValue(eq(proofJson), eq(DrawProofResponseDTO.class))).thenReturn(proof);

            DrawProofResponseDTO returned = service.getDrawProof(5L);

            assertThat(returned.getRaffleId()).isEqualTo(5L);
            assertThat(returned.getRaffleTitle()).isEqualTo("Test Raffle");
        }
    }
}
