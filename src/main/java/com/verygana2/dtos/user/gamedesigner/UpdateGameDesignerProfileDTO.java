package com.verygana2.dtos.user.gamedesigner;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateGameDesignerProfileDTO {

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    private String bio;
}
