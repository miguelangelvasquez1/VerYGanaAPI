package com.verygana2.dtos.pqrs.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RespondPqrsRequestDTO {

    @NotBlank(message = "La respuesta es obligatoria")
    @Size(max = 4000, message = "La respuesta no debe superar los 4000 caracteres")
    private String response;
}
