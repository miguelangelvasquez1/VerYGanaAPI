package com.verygana2.security.monitoring;

import java.time.Duration;
import java.util.Set;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BruteForcePattern {
    private String ipAddress;
    private int attemptCount;
    private Duration timeWindow;
    private boolean escalatingPattern;
    private boolean multipleUsers;
    private Set<String> affectedUsernames;
    private int riskScore;
}
