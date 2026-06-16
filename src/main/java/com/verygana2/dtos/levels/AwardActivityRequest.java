package com.verygana2.dtos.levels;

import com.verygana2.models.enums.ActivityType;
import jakarta.validation.constraints.NotNull;

public record AwardActivityRequest(
        @NotNull ActivityType activityType
) {}
