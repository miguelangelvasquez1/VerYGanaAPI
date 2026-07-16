package com.verygana2.dtos.levels;

import com.verygana2.models.enums.UserLevel;

public record LevelProfileResponse(
        UserLevel currentLevel,
        Long xpTotal,
        Long xpToNextLevel,
        double multiplier,
        boolean benefitsPaused,
        boolean reactivationMissionActive,
        Long reactivationXpGoal,
        Long reactivationXpProgress
) {}