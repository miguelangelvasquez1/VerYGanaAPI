package com.verygana2.dtos.generic;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EntityCreatedResponse {
    private String message;
    private Instant timestamp;

}