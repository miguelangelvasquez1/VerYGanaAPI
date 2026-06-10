package com.verygana2.dtos.raffle.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evidencia criptográfica del sorteo ejecutado por Random.org.
 * El serialNumber permite verificar los números en https://api.random.org/verify
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RandomOrgDrawMetadata {
    private Long serialNumber;
    private String completionTime;
    private int bitsUsed;
    private int bitsLeft;
}
