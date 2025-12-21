package com.verygana2.dtos.game;

import java.util.List;

import com.verygana2.models.enums.DevicePlatform;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EndSessionDTO {
    
    private DevicePlatform devicePlatform;
    private Integer finalScore;
    private List<GameMetricDTO> finalMetrics; // Aquí se puede incluir recompensas, puntuaciones, desempeño y engagement?.
}
