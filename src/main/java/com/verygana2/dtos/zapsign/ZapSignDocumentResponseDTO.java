package com.verygana2.dtos.zapsign;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** Respuesta de POST /api/v1/docs/ de ZapSign. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZapSignDocumentResponseDTO {

    @JsonProperty("token")
    private String token;

    @JsonProperty("status")
    private String status;

    @JsonProperty("name")
    private String name;

    @JsonProperty("original_file")
    private String originalFile;

    @JsonProperty("signed_file")
    private String signedFile;

    @JsonProperty("signers")
    private List<ZapSignSignerResponseDTO> signers;
}
