package com.verygana2.dtos.pqrs.requests;

import com.verygana2.models.enums.pqrs.PqrsType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePqrsRequestDTO {

    @NotNull(message = "El tipo de PQRS es obligatorio")
    private PqrsType type;

    @NotBlank(message = "El asunto es obligatorio")
    @Size(max = 200, message = "El asunto no debe superar los 200 caracteres")
    private String subject;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 4000, message = "La descripción no debe superar los 4000 caracteres")
    private String description;
}
