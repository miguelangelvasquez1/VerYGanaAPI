package com.verygana2.dtos.zapsign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** Respuesta de POST /api/v1/user/company/webhook/. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZapSignWebhookResponseDTO {

    @JsonProperty("id")
    private String id;
}
