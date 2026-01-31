package com.verygana2.services.raffles;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.CreatePrizeRequestDTO;
import com.verygana2.dtos.raffle.requests.CreateRaffleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateRaffleRequestDTO;
import com.verygana2.dtos.raffle.responses.ParticipantLeaderboardDTO;
import com.verygana2.dtos.raffle.responses.RaffleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleStatsResponseDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.rafflesExceptions.InvalidOperationException;
import com.verygana2.mappers.raffles.PrizeMapper;
import com.verygana2.mappers.raffles.RaffleMapper;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.repositories.raffles.PrizeRepository;
import com.verygana2.repositories.raffles.RaffleParticipationRepository;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.services.interfaces.raffles.RaffleService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RaffleServiceImpl implements RaffleService {

    private final RaffleRepository raffleRepository;
    private final RaffleTicketRepository raffleTicketRepository;
    private final RaffleParticipationRepository raffleParticipationRepository;
    private final PrizeRepository prizeRepository;
    private final RaffleMapper raffleMapper;
    private final PrizeMapper prizeMapper;

    @SuppressWarnings("null")
    @Override
    public EntityCreatedResponseDTO createRaffle(CreateRaffleRequestDTO request) {

        if (!request.getDrawDate().isAfter(request.getEndDate())) {
            throw new InvalidRequestException("Draw date must be after end date");
        }

        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new InvalidRequestException("End date must be after start date");
        }

        long uniquePositions = request.getPrizes().stream()
                .map(CreatePrizeRequestDTO::getPosition)
                .distinct()
                .count();

        if (uniquePositions != request.getPrizes().size()) {
            throw new InvalidRequestException("Prize positions must be unique");
        }

        Raffle raffle = raffleMapper.toRaffle(request);

        Raffle savedRaffle = raffleRepository.save(raffle);

        List<Prize> rafflePrizes = request.getPrizes().stream().map(prizeRequest -> {
            Prize prize = prizeMapper.toPrize(prizeRequest);
            prize.setRaffle(savedRaffle);
            return prize;
        }).toList();

        prizeRepository.saveAll(rafflePrizes);

        return new EntityCreatedResponseDTO(savedRaffle.getId(), "Raffle created successfully", Instant.now());
    }

    @Override
    public EntityUpdatedResponseDTO updateRaffle(Long raffleId, UpdateRaffleRequestDTO request) {

        if (!request.getDrawDate().isAfter(request.getEndDate())) {
            throw new InvalidRequestException("Draw date must be after end date");
        }

        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new InvalidRequestException("End date must be after start date");
        }

        Raffle raffleUpdated = getRaffleById(raffleId);

        raffleUpdated.setTitle(request.getTitle());
        raffleUpdated.setDescription(request.getDescription());
        raffleUpdated.setRaffleType(request.getRaffleType());
        raffleUpdated.setStartDate(request.getStartDate());
        raffleUpdated.setEndDate(request.getEndDate());
        raffleUpdated.setDrawDate(request.getDrawDate());

        raffleRepository.save(raffleUpdated);

        return new EntityUpdatedResponseDTO(raffleUpdated.getId(), "Raffle updated successfully", Instant.now());
    }

    @Override
    public void activateRaffle(Long raffleId) {

        Raffle raffle = getRaffleById(raffleId);

        if (raffle.getPrizes() == null || raffle.getPrizes().isEmpty()) {
            throw new InvalidOperationException("Cannot activate raffle without prizes");
        }

        if (ZonedDateTime.now().isAfter(raffle.getEndDate())) {
            throw new InvalidOperationException("Cannot activate expired raffle");
        }

        raffle.setRaffleStatus(RaffleStatus.ACTIVE);
        raffleRepository.save(raffle);
    }

    @Override
    public void closeRaffle(Long raffleId) {

        Raffle raffle = getRaffleById(raffleId);
        raffle.setRaffleStatus(RaffleStatus.CLOSED);
        raffleRepository.save(raffle);
    }

    @Override
    public Raffle getRaffleById(Long raffleId) {

        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        return raffleRepository.findById(raffleId).orElseThrow(
                () -> new ObjectNotFoundException("Raffle with id: " + raffleId + " not found ", Raffle.class));
    }

    @Override
    public RaffleResponseDTO getRaffleResponseDTOById(Long raffleId) {
        
        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        Raffle raffle = raffleRepository.findById(raffleId).orElseThrow(
                () -> new ObjectNotFoundException("Raffle with id: " + raffleId + " not found ", Raffle.class));

        return raffleMapper.toRaffleResponseDTO(raffle);
    }

    @Override
    public PagedResponse<RaffleResponseDTO> getRafflesByStatusAndType(RaffleStatus status, RaffleType type,
            Pageable pageable) {

        Page<Raffle> rafflesFound = raffleRepository.findByRaffleStatusAndRaffleType(status, type, pageable);
        Page<RaffleResponseDTO> response = rafflesFound.map(raffleMapper::toRaffleResponseDTO);

        return PagedResponse.from(response);
    }

    @Override
    public RaffleStatsResponseDTO getRaffleStats(Long raffleId) {

        Raffle raffle = getRaffleById(raffleId);
        RaffleStatsResponseDTO response = raffleMapper.toRaffleStatsResponseDTO(raffle);

        List<Object[]> stats = raffleTicketRepository.countTicketsBySource(raffleId);

        Map<RaffleTicketSource, Long> ticketsBySource = stats.stream()
                .collect(Collectors.toMap(
                        entry -> (RaffleTicketSource) entry[0],
                        entry -> (Long) entry[1]));

        response.setTicketsBySource(ticketsBySource);

        return response;

    }

    @Override
    public List<ParticipantLeaderboardDTO> getRaffleLeaderBoard(Long raffleId) {
        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        return raffleParticipationRepository.findLeaderboard(raffleId, PageRequest.of(0, 10));
    }

}
