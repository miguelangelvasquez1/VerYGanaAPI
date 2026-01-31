package com.verygana2.mappers.raffles;

import org.mapstruct.Mapper;

import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.models.raffles.RaffleTicket;

@Mapper(componentModel = "spring")
public interface RaffleTicketMapper {
    
    RaffleTicketResponseDTO toRaffleTicketResponseDTO (RaffleTicket raffleTicket);
}
