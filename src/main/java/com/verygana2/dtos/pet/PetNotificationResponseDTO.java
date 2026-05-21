package com.verygana2.dtos.pet;


import java.time.LocalDate;

public record PetNotificationResponseDTO(
        String id,
        String title,
        String message,
        String imageUrl,
        String buttonLabel,
        String buttonUrl,
        LocalDate date
) {}