package com.verygana2.dtos.generic;

import java.time.Instant;

public record EntityCreatedResponse (
    String message,
    Instant timestamp
){
    
}
