package com.verygana2.security.monitoring;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SecurityAlert {
    private SecurityAlertType type;
    private AlertSeverity severity;
    private String source;
    private String description;
    private Map<String, Object> additionalData;

    @Builder.Default
    private Instant detectedAt = Instant.now();
}
