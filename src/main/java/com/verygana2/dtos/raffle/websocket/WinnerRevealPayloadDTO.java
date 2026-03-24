package com.verygana2.dtos.raffle.websocket;

import java.math.BigDecimal;
import com.verygana2.models.enums.raffles.PrizeType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WinnerRevealPayloadDTO {
    private int position;           // 1er, 2do, 3er lugar
    private String ticketNumber;    // Número de boleta ganadora
    private String userName;        // Nombre del ganador
    private String prizeTitle;      // "MacBook Pro 2025"
    private BigDecimal prizeValue;  // Valor del premio
    private PrizeType prizeType;    // PHYSICAL, DIGITAL, etc.
    private int revealOrder;        // En qué número de revelación estamos (1 de 3, 2 de 3...)
    private int totalWinners;       // Total de ganadores a revelar
}
