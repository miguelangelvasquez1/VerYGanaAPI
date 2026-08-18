package com.verygana2.dtos.pet;

import jakarta.validation.constraints.NotNull;

/** Asignación o reasignación del diseñador que armará el ítem del catálogo. */
public record AssignPetDesignerDTO(

        @NotNull(message = "El id de usuario del diseñador es requerido")
        Long designerUserId
) {}
