package com.verygana2.dtos.branding;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitGameConfigDTO {

    @NotNull(message = "Game config is required")
    private Map<String, Object> config;
}
