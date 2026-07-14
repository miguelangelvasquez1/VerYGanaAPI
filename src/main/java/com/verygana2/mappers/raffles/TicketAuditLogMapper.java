package com.verygana2.mappers.raffles;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.raffle.responses.TicketAuditLogResponseDTO;
import com.verygana2.models.raffles.TicketAuditLog;

@Mapper(componentModel = "spring")
public interface TicketAuditLogMapper {
    @Mapping(target = "ticketId", source = "ticket.id")
    TicketAuditLogResponseDTO toTicketAuditLogResponseDTO(TicketAuditLog ticketAuditLog);
}
