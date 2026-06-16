package com.verygana2.dtos.user.gamedesigner;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateGameDesignerProfileDTO {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    private String bio;
}
