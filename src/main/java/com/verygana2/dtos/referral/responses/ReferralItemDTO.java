package com.verygana2.dtos.referral.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.UserState;

/**
 * Información de cada consumer referido por el usuario autenticado.
 */
public record ReferralItemDTO(
        String   name,
        String   lastName,
        String   userName,
        String   email,
        String   department,
        String   municipality,
        UserState userState,
        ZonedDateTime registeredDate
) {}