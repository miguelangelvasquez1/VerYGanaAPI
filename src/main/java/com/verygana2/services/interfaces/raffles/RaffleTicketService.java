package com.verygana2.services.interfaces.raffles;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.dtos.raffle.responses.SuspiciousIpActivityResponseDTO;
import com.verygana2.dtos.raffle.responses.TicketAuditLogResponseDTO;
import com.verygana2.dtos.raffle.responses.TicketBalanceResponseDTO;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.enums.raffles.RaffleType;

public interface RaffleTicketService {

    List<RaffleTicketResponseDTO> issueTickets(Long consumerId, Long raffleId, Integer quantity, RaffleTicketSource source, Long sourceId);

    boolean canUserReceiveTickets(Long consumerId, RaffleType raffleType);

    Long getUserTotalTickets(Long consumerId, RaffleTicketStatus status);

    Long getUserWinnerTotalTickets (Long consumerId);

    List<TicketBalanceResponseDTO> getUserTicketBalanceByRaffle(Long consumerId);

    PagedResponse<RaffleTicketResponseDTO> getUserTicketsByRaffle(Long consumerId, Long raffleId, Pageable pageable);

    PagedResponse<RaffleTicketResponseDTO> getUserWinnerTickets(Long consumerId, Pageable pageable);

    void expireTickets(Long raffleId);

    List<TicketAuditLogResponseDTO> getAuditLogsByTicketId(Long ticketId);

    PagedResponse<TicketAuditLogResponseDTO> getAuditLogsBetweenDates(LocalDate from, LocalDate to,
            Pageable pageable);

    List<SuspiciousIpActivityResponseDTO> getSuspiciousActivity(LocalDate since, long threshold);
}
