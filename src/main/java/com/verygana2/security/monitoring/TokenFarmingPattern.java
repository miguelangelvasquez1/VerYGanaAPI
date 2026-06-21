package com.verygana2.security.monitoring;

import java.time.Instant;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenFarmingPattern {
    private String username;
    private int tokenCount;
    private Set<String> uniqueIPs;
    private Instant since;
}
