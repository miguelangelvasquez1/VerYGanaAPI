package com.VerYGana.dtos2.generic;

import java.time.Instant;

public record EntityCreatedResponse (
    String message,
    Instant timestamp
){
    
}
