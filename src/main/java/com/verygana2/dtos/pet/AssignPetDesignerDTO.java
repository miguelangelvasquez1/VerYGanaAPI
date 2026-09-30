package com.verygana2.dtos.pet;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Asignación o reasignación del diseñador que armará el ítem del catálogo. */
public record AssignPetDesignerDTO(

        @NotNull(message = "El publicId del diseñador es requerido")
        UUID designerPublicId
) {}
