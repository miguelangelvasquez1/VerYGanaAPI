package com.VerYGana.dtos.generic;

import java.time.Instant;

public record EntityCreatedResponse (
    String message,
    Instant timestamp
){
    
}
