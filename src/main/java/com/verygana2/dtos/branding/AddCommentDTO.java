package com.verygana2.dtos.branding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddCommentDTO {

    @NotBlank(message = "El contenido del comentario no puede estar vacío")
    @Size(max = 2000, message = "El comentario no puede superar los 2000 caracteres")
    private String content;
}
