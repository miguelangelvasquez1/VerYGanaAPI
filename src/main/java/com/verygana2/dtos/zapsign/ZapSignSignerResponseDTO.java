package com.verygana2.dtos.zapsign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZapSignSignerResponseDTO {

    @JsonProperty("token")
    private String token;

    @JsonProperty("sign_url")
    private String signUrl;

    @JsonProperty("status")
    private String status;

    @JsonProperty("name")
    private String name;

    @JsonProperty("email")
    private String email;
}
