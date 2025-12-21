package com.verygana2.dtos.game.campaign;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCampaignRequestDTO {

    @NotNull(message = "Juego requerido")
    private Long gameId;

    @NotNull(message = "Assets requeridos")
    private List<CreateAssetRequestDTO> assets;
}