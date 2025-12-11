package com.verygana2.dtos.generic;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EntityUpdatedResponseDTO {
    private Long id;
    private String message;
    private Instant timestamp;
}
