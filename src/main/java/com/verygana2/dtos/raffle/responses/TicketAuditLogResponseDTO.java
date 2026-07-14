package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.AuditAction;
import com.verygana2.models.enums.raffles.RaffleTicketSource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketAuditLogResponseDTO {
    private Long id;
    private Long ticketId;
    private AuditAction action;
    private RaffleTicketSource sourceType;
    private Long sourceId;
    private String ipAddress;
    private String metadata;
    private ZonedDateTime createdAt;
}
