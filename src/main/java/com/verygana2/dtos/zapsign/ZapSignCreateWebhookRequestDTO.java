package com.verygana2.dtos.zapsign;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/** Body de POST /api/v1/user/company/webhook/ — registra un webhook a nivel de cuenta ZapSign. */
@Data
@Builder
public class ZapSignCreateWebhookRequestDTO {

    @JsonProperty("url")
    private String url;

    /** "" = todos los eventos, o un tipo específico (ej. "doc_signed"). */
    @JsonProperty("type")
    private String type;

    @JsonProperty("headers")
    private List<ZapSignWebhookHeaderDTO> headers;
}
