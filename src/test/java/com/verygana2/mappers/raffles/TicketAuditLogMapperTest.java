package com.verygana2.mappers.raffles;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.raffle.responses.TicketAuditLogResponseDTO;
import com.verygana2.models.enums.raffles.AuditAction;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.TicketAuditLog;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TicketAuditLogMapper")
class TicketAuditLogMapperTest {

    private final TicketAuditLogMapper ticketAuditLogMapper = new TicketAuditLogMapperImpl();

    @Nested
    @DisplayName("toTicketAuditLogResponseDTO")
    class ToTicketAuditLogResponseDTO {

        @Test
        @DisplayName("ticketId sale de ticket.id y el resto de campos se mapean directo")
        void mapsFieldsIncludingTicketId() {
            RaffleTicket ticket = new RaffleTicket();
            ticket.setId(11L);
            ZonedDateTime createdAt = ZonedDateTime.now(ZoneOffset.UTC);
            TicketAuditLog auditLog = new TicketAuditLog();
            auditLog.setId(1L);
            auditLog.setTicket(ticket);
            auditLog.setAction(AuditAction.ISSUED);
            auditLog.setSourceType(RaffleTicketSource.PURCHASE);
            auditLog.setSourceId(5L);
            auditLog.setIpAddress("127.0.0.1");
            auditLog.setMetadata("{}");
            auditLog.setCreatedAt(createdAt);

            TicketAuditLogResponseDTO dto = ticketAuditLogMapper.toTicketAuditLogResponseDTO(auditLog);

            assertThat(dto.getTicketId()).isEqualTo(11L);
            assertThat(dto.getAction()).isEqualTo(AuditAction.ISSUED);
            assertThat(dto.getSourceType()).isEqualTo(RaffleTicketSource.PURCHASE);
            assertThat(dto.getSourceId()).isEqualTo(5L);
            assertThat(dto.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(dto.getMetadata()).isEqualTo("{}");
            assertThat(dto.getCreatedAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("ticket == null: ticketId queda null, sin NPE")
        void nullTicket_doesNotThrow() {
            TicketAuditLog auditLog = new TicketAuditLog();
            auditLog.setTicket(null);

            TicketAuditLogResponseDTO dto = ticketAuditLogMapper.toTicketAuditLogResponseDTO(auditLog);

            assertThat(dto.getTicketId()).isNull();
        }
    }
}
