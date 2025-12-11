package com.verygana2.dtos.generic;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EntityCreatedResponseDTO {
    private Long id;
    private String message;
    private Instant timestamp;

}