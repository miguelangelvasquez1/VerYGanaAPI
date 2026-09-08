package com.verygana2.mappers.raffles;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleTicket;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RaffleTicketMapper")
class RaffleTicketMapperTest {

    private final RaffleTicketMapper raffleTicketMapper = new RaffleTicketMapperImpl();

    @Nested
    @DisplayName("toRaffleTicketResponseDTO")
    class ToRaffleTicketResponseDTO {

        @Test
        @DisplayName("raffleId sale de raffle.id y el resto de campos se mapean directo")
        void mapsFieldsIncludingRaffleId() {
            Raffle raffle = new Raffle();
            raffle.setId(42L);
            RaffleTicket ticket = new RaffleTicket();
            ticket.setId(1L);
            ticket.setRaffle(raffle);
            ticket.setTicketNumber("000456");
            ticket.setStatus(RaffleTicketStatus.ACTIVE);
            ticket.setSource(RaffleTicketSource.PURCHASE);
            ticket.setSourceId(10L);
            ticket.setIsWinner(false);

            RaffleTicketResponseDTO dto = raffleTicketMapper.toRaffleTicketResponseDTO(ticket);

            assertThat(dto.getRaffleId()).isEqualTo(42L);
            assertThat(dto.getTicketNumber()).isEqualTo("000456");
            assertThat(dto.getStatus()).isEqualTo(RaffleTicketStatus.ACTIVE);
            assertThat(dto.getSource()).isEqualTo(RaffleTicketSource.PURCHASE);
            assertThat(dto.getSourceId()).isEqualTo(10L);
            assertThat(dto.getIsWinner()).isFalse();
        }

        @Test
        @DisplayName("raffle == null: raffleId queda null, sin NPE")
        void nullRaffle_doesNotThrow() {
            RaffleTicket ticket = new RaffleTicket();
            ticket.setRaffle(null);
            ticket.setTicketNumber("000789");

            RaffleTicketResponseDTO dto = raffleTicketMapper.toRaffleTicketResponseDTO(ticket);

            assertThat(dto.getRaffleId()).isNull();
            assertThat(dto.getTicketNumber()).isEqualTo("000789");
        }
    }
}
