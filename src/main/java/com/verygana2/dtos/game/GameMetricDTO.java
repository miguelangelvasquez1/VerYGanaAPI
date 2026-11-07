package com.verygana2.dtos.game;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.verygana2.models.enums.MetricType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameMetricDTO {
    private String key;
    // Un tipo desconocido se lee como nulo en vez de tumbar el envío (spec 013, D4)
    @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
    private MetricType type;
    private Object value;

}
