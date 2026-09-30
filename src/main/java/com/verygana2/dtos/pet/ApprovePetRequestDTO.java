package com.verygana2.dtos.pet;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Aprobación de una solicitud de integración al catálogo de mascotas.
 * Mismo contrato que {@code ApproveBrandingRequestDTO}: aprobar implica asignar
 * el diseñador que va a armar el ítem.
 */
public record ApprovePetRequestDTO(

        @NotNull(message = "El publicId del diseñador es requerido")
        UUID designerPublicId,

        @Size(max = 1000)
        String adminNotes
) {}
