package com.verygana2.dtos.game;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameMetricsRequestDTO {
    private Long sessionId;
    private List<GameMetricRequestDTO> metrics;
}
