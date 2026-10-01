package com.verygana2.dtos.pqrs.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PreparePqrsAssetRequestDTO {

    @NotBlank(message = "El nombre del archivo es requerido")
    private String originalFileName;

    @NotBlank(message = "El content-type es requerido")
    private String contentType;

    @NotNull(message = "El tamaño del archivo es requerido")
    @Positive(message = "El tamaño del archivo debe ser positivo")
    private Long sizeBytes;
}
