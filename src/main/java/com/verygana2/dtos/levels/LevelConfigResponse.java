package com.verygana2.dtos.levels;

import com.verygana2.models.enums.UserLevel;

public record LevelConfigResponse(
        UserLevel level,
        long xpMin,
        String xpMax,          // "∞" para Diamante
        double multiplier,
        int referralKeys,
        int referralTickets
) {}
