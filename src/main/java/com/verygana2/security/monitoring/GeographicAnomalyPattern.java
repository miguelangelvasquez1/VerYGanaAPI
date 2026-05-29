package com.verygana2.security.monitoring;

import java.time.Duration;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeographicAnomalyPattern {
    private String username;
    private int estimatedDistance;
    private Duration timeDifference;
    private boolean anomalous;
    private List<String> locations;
}
