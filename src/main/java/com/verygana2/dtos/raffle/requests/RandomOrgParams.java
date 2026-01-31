package com.verygana2.dtos.raffle.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RandomOrgParams {
    
    private String apiKey;
    private int n;              // Cantidad de números a generar
    private int min;            // Valor mínimo (0)
    private int max;            // Valor máximo (tickets.size() - 1)
    private boolean replacement; // false = sin repetición
}
