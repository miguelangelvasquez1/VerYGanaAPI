package com.verygana2.dtos.game;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitGameRequestDTO {
    
    @NotNull(message = "Game ID is required")
    private Long gameId; // Debería ser aleatorio si es sponsored

    @NotNull(message = "Sponsored flag is required")
    private Boolean sponsored;
}
