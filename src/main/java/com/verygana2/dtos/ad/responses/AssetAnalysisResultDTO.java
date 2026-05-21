package com.verygana2.dtos.ad.responses;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssetAnalysisResultDTO {
 
    /** Duration of the asset in seconds (ceiling). */
    private Double durationSeconds;
 
    /**
     * Minimum price per view in cents.
     * = roundUpToMultipleOf10( ceil(durationSeconds) * costPerSecondCents )
     * The user may choose any value >= this that is also a multiple of 10.
     */
    private Long minPricePerLike;
}
 