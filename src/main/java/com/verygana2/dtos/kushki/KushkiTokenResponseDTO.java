package com.verygana2.dtos.kushki;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KushkiTokenResponseDTO {

    @JsonProperty("token")
    private String token;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;
}
