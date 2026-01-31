package com.verygana2.dtos.raffle.responses;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RandomOrgRandom {
    private List<Integer> data;  // ✅ Los índices aleatorios
    private String completionTime;
    @JsonProperty("serialNumber")
    private Long serialNumber;
}
