package com.verygana2.dtos.branding;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDesignerNotesDTO {

    @Size(max = 1000, min = 1, message = "Notes must not exceed 1000 characters")
    private String notes;
}
