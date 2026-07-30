package com.verygana2.dtos.zapsign;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/** Header custom que ZapSign envía junto con cada webhook, para que el receptor lo valide. */
@Data
@Builder
public class ZapSignWebhookHeaderDTO {

    @JsonProperty("name")
    private String name;

    @JsonProperty("value")
    private String value;
}
