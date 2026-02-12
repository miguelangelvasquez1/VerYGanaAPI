package com.verygana2.services.interfaces.raffles;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.dtos.raffle.responses.TicketBalanceResponseDTO;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.enums.raffles.RaffleType;

public interface RaffleTicketService {

    List<RaffleTicketResponseDTO> issueTickets(Long consumerId, Long raffleId, Integer quantity, RaffleTicketSource source, Long sourceId);

    boolean canUserReceiveTickets(Long consumerId, RaffleType raffleType);

    Long getUserTicketBalanceInRaffle(Long consumerId, Long raffleId, RaffleTicketStatus status);

    Long getUserTotalTickets(Long consumerId, RaffleTicketStatus status);

    List<TicketBalanceResponseDTO> getUserTicketBalanceByRaffle(Long consumerId);

    PagedResponse<RaffleTicketResponseDTO> getUserTickets(Long consumerId, RaffleTicketStatus status,
            RaffleTicketSource source, ZonedDateTime issuedAt, Pageable pageable);

    PagedResponse<RaffleTicketResponseDTO> getTicketsByRaffle(Long raffleId, RaffleTicketStatus status,
            RaffleTicketSource source,ZonedDateTime issuedAt, Pageable pageable);

    boolean validateTicket(String ticketNumber);

    void expireTickets(Long raffleId);
}
