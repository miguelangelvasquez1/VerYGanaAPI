package com.verygana2.dtos.game;

import com.verygana2.models.enums.MetricType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameMetricRequestDTO {
    private String key;
    private MetricType type;
    private Object value;
}
