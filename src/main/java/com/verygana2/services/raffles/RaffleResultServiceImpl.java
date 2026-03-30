package com.verygana2.services.raffles;

import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.raffle.responses.DrawProofResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleResultResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResultResponseDTO;
import com.verygana2.exceptions.rafflesExceptions.InvalidOperationException;
import com.verygana2.mappers.raffles.RaffleResultMapper;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.repositories.raffles.RaffleResultRepository;
import com.verygana2.services.interfaces.raffles.RaffleResultService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RaffleResultServiceImpl implements RaffleResultService {

    private final RaffleResultRepository raffleResultRepository;
    private final RaffleResultMapper raffleResultMapper;
    private final ObjectMapper objectMapper;

    @Override
    public RaffleResult getByRaffleId(Long raffleId) {
        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        return raffleResultRepository.findByRaffleId(raffleId).orElseThrow(() -> new ObjectNotFoundException(
                "Raffle result with raffle id: " + raffleId + " not found ", RaffleResult.class));
    }

    @Override
    public RaffleResultResponseDTO getResponseByRaffleId(Long raffleId) {
        return raffleResultMapper.toRaffleResultDTO(getByRaffleId(raffleId));
    }

    @Override
    public List<RaffleSummaryResultResponseDTO> getLastRaffleResults() {
        List<RaffleResult> results = raffleResultRepository.findLastRaffleResults();
        return results.stream().map(raffleResultMapper::toRaffleSummaryResultResponseDTO).toList();
    }

    @Override
    public DrawProofResponseDTO getDrawProof(Long raffleId) {
        RaffleResult result = getByRaffleId(raffleId);

        if (result.getDrawProof() == null || result.getDrawProof().isBlank()) {
            throw new InvalidOperationException(
                    "Draw proof not available for raffle: " + raffleId);
        }

        try {
            // El drawProof ya es un JSON serializado por DrawingServiceImpl
            // Lo deserializamos al DTO público
            return objectMapper.readValue(result.getDrawProof(), DrawProofResponseDTO.class);
        } catch (JsonProcessingException e) {
            throw new InvalidOperationException("Draw proof is malformed for raffle: " + raffleId);
        }
    }

}
