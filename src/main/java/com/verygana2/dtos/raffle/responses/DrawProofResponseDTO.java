package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;
import java.util.List;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawProofResponseDTO {
    private Long raffleId;
    private String raffleTitle;
    private String configuredDrawMethod;
    private String actualDrawMethod;
    private String drawMethodNote;
    /** Solo presente cuando actualDrawMethod == RANDOM_ORG. Null en sorteos internos. */
    private RandomOrgDrawMetadata randomOrgMetadata;
    private ZonedDateTime drawDate;
    private ZonedDateTime executedAt;
    private Integer totalParticipants;
    private Integer totalTickets;
    /**
     * SHA-256 del listado de tickets activos (ordenados por ticketNumber, separados por coma)
     * computado ANTES de ejecutar el sorteo. Permite verificar que el pool no fue alterado.
     */
    private String ticketPoolHash;
    private Integer numberOfWinners;
    private List<WinnerProofResponseDTO> winners;
}
