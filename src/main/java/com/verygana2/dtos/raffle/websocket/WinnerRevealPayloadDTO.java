package com.verygana2.dtos.raffle.websocket;

import java.math.BigDecimal;
import com.verygana2.models.enums.raffles.PrizeType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WinnerRevealPayloadDTO {
    private int position;           
    private String ticketNumber;    
    private String userName; 
    private String userAvatarUrl;       
    private String prizeTitle;      
    private String prizeImageUrl;
    private BigDecimal prizeValue;  
    private PrizeType prizeType;    
    private int revealOrder;        // En qué número de revelación estamos (1 de 3, 2 de 3...)
    private int totalWinners;       
}
