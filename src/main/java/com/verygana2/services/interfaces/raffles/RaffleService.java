package com.verygana2.services.interfaces.raffles;


import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.ConfirmRaffleCreationRequestDTO;
import com.verygana2.dtos.raffle.requests.CreateRaffleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateRaffleRequestDTO;
import com.verygana2.dtos.raffle.responses.ParticipantLeaderboardDTO;
import com.verygana2.dtos.raffle.responses.PrepareRaffleCreationResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleStatsResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResponseDTO;
import com.verygana2.dtos.raffle.responses.UserRaffleSummaryResponseDTO;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Raffle;

public interface RaffleService {
    
    PrepareRaffleCreationResponseDTO prepareRaffleCreation (Long adminId, CreateRaffleRequestDTO raffleData, FileUploadRequestDTO raffleImageMetadata, List<FileUploadRequestDTO> prizeImageMetadataList);
    EntityCreatedResponseDTO confirmRaffleCreation(Long adminId, ConfirmRaffleCreationRequestDTO request);
    EntityUpdatedResponseDTO updateRaffle(Long adminId, Long raffleId, UpdateRaffleRequestDTO request);
    void activateRaffle(Long raffleId);
    void closeRaffle(Long raffleId);
    void liveRaffle (Long raffleId);
    void cancelRaffle (Long raffleId);
    void deleteRaffle(Long raffleId);
    Raffle getRaffleById(Long raffleId);
    RaffleResponseDTO getRaffleResponseDTOById(Long raffleId);
    PagedResponse<RaffleSummaryResponseDTO> getSummaryRafflesByStatusAndType(RaffleStatus status, RaffleType type, Pageable pageable);
    RaffleStatsResponseDTO getRaffleStats(Long raffleId);
    List<ParticipantLeaderboardDTO> getRaffleLeaderBoard(Long raffleId);
    List<Raffle> getActiveRafflesOrderedByDrawDate(ZonedDateTime drawDate);
    Long countRafflesByStatus(RaffleStatus status);
    List<RaffleSummaryResponseDTO> getLiveRaffles();
    PagedResponse<RaffleSummaryResponseDTO> getActiveRaffles(RaffleType type, int pageNumber);
    PagedResponse<UserRaffleSummaryResponseDTO> getMyRafflesByStatus (Long consumerId, RaffleStatus status, Pageable pageable);
    Long countMyRafflesByStatus (Long consumerId, RaffleStatus status);
}
