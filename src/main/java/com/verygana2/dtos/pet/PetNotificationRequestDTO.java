package com.verygana2.dtos.pet;

import java.time.LocalDate;

public record PetNotificationRequestDTO(
        String externalId,
        String title,
        String message,
        String imageUrl,
        String buttonLabel,
        String buttonUrl,
        LocalDate date,
        Boolean active
) {}