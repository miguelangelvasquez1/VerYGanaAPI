package com.verygana2.services.compliance;

import com.verygana2.models.enums.ScreeningList;
import com.verygana2.models.enums.ScreeningStatus;

public record ScreeningOutcome(
        ScreeningList lista,
        ScreeningStatus status,
        String referenceId,
        String rawResponse
) {}