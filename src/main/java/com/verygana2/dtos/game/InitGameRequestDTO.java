package com.verygana2.dtos.game;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitGameRequestDTO {
    

    private Long gameId; // Solo se usa si la request es NO sponsored

    @NotNull(message = "Sponsored flag is required")
    private Boolean sponsored;
}
