package com.verygana2.dtos.referral.responses;

import com.verygana2.models.enums.UserState;

import java.time.ZonedDateTime;

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