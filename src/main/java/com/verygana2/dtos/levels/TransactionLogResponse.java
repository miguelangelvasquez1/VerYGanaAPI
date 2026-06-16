package com.verygana2.dtos.levels;

import java.time.LocalDateTime;
import com.verygana2.models.enums.ActivityType;

public record TransactionLogResponse(
        Long id,
        ActivityType activityType,
        Long xpEarned,
        Double multiplierApplied,
        LocalDateTime createdAt
) {}