package com.verygana2.services.raffles;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.verygana2.dtos.raffle.responses.DrawProofResponseDTO;
import com.verygana2.exceptions.rafflesExceptions.InvalidOperationException;
import com.verygana2.mappers.raffles.RaffleResultMapper;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.repositories.raffles.RaffleResultRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link RaffleResultServiceImpl}: consulta del resultado de una
 * rifa y deserialización del draw proof (el JSON generado por
 * {@code DrawingServiceImpl}) al DTO público.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleResultServiceImpl")
class RaffleResultServiceImplTest {

    @Mock private RaffleResultRepository raffleResultRepository;
    @Mock private RaffleResultMapper raffleResultMapper;

    private RaffleResultServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new RaffleResultServiceImpl(raffleResultRepository, raffleResultMapper, objectMapper);
    }

    @Test
    @DisplayName("getByRaffleId: id inválido lanza IllegalArgumentException")
    void getByRaffleId_invalidId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.getByRaffleId(0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getByRaffleId: sin resultado registrado lanza ObjectNotFoundException")
    void getByRaffleId_notFound_throwsObjectNotFoundException() {
        when(raffleResultRepository.findByRaffleId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getByRaffleId(1L)).isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    @DisplayName("getDrawProofByRaffleId: sin draw proof aún lanza InvalidOperationException")
    void getDrawProofByRaffleId_noProof_throwsInvalidOperationException() {
        RaffleResult result = new RaffleResult();
        result.setDrawProof(null);
        when(raffleResultRepository.findByRaffleId(1L)).thenReturn(Optional.of(result));

        assertThatThrownBy(() -> service.getDrawProofByRaffleId(1L)).isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("getDrawProofByRaffleId: JSON corrupto lanza InvalidOperationException")
    void getDrawProofByRaffleId_malformedJson_throwsInvalidOperationException() {
        RaffleResult result = new RaffleResult();
        result.setDrawProof("{not valid json");
        when(raffleResultRepository.findByRaffleId(1L)).thenReturn(Optional.of(result));

        assertThatThrownBy(() -> service.getDrawProofByRaffleId(1L)).isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("getDrawProofByRaffleId: JSON válido se deserializa correctamente al DTO")
    void getDrawProofByRaffleId_validJson_deserializesCorrectly() {
        RaffleResult result = new RaffleResult();
        result.setDrawProof("{\"raffleId\":42,\"raffleTitle\":\"Rifa de prueba\"}");
        when(raffleResultRepository.findByRaffleId(1L)).thenReturn(Optional.of(result));

        DrawProofResponseDTO proof = service.getDrawProofByRaffleId(1L);

        assertThat(proof.getRaffleId()).isEqualTo(42L);
        assertThat(proof.getRaffleTitle()).isEqualTo("Rifa de prueba");
    }
}
