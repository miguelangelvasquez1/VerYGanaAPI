package com.verygana2.dtos.raffle.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evidencia criptográfica del sorteo ejecutado por Random.org.
 * Para verificar: https://api.random.org/verify usando el serialNumber y signature.
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
    /** Firma RSA de Random.org sobre los datos generados. Verificable públicamente. */
    private String signature;
    /** Hash de la API key usada — confirma la identidad del solicitante sin exponer la key. */
    private String hashedApiKey;
    /** Licencia bajo la que se generaron los números (varía según el plan contratado). */
    private Object license;
}
