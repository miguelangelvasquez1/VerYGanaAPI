package com.verygana2.dtos.recognitions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelRecognitionRequestDTO(@NotBlank @Size(max = 500) String cause) {
}
