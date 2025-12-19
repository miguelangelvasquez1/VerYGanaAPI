package com.verygana2.dtos.game;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitGameRequestDTO {
    
    @NotNull
    private Long gameId;
}
