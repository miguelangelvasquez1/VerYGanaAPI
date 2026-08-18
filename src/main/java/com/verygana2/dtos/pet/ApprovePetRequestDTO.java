package com.verygana2.dtos.pet;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Aprobación de una solicitud de integración al catálogo de mascotas.
 * Mismo contrato que {@code ApproveBrandingRequestDTO}: aprobar implica asignar
 * el diseñador que va a armar el ítem.
 */
public record ApprovePetRequestDTO(

        @NotNull(message = "El id de usuario del diseñador es requerido")
        Long designerUserId,

        @Size(max = 1000)
        String adminNotes
) {}
