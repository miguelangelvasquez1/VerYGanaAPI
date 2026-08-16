package com.verygana2.dtos.pet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCatalogRequestCommentDTO(
        @NotBlank(message = "El contenido del comentario no puede estar vacío")
        @Size(max = 2000, message = "El comentario no puede superar los 2000 caracteres")
        String content
) {}
