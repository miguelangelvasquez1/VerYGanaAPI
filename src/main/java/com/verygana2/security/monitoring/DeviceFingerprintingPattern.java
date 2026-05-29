package com.verygana2.security.monitoring;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeviceFingerprintingPattern {
    private String deviceId;
    private int uniqueUserAgents;
    private int tokenCount;
    private Instant since;
}
