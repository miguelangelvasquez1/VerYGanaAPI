package com.verygana2.dtos.branding;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitForReviewDTO {

    @Size(max = 2000, message = "Las notas no pueden superar los 2000 caracteres")
    private String notes;
}
