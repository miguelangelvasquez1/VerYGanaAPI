package com.verygana2.dtos.zapsign;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/** Body de POST /api/v1/checks/ — solicita una consulta de antecedentes (persona o empresa). */
@Data
@Builder
public class ZapSignCreateCheckRequestDTO {

    @JsonProperty("country")
    private String country;

    /** "person", "company", "credit_person_co", "credit_company_co", etc. */
    @JsonProperty("type")
    private String type;

    @JsonProperty("user_authorized")
    private boolean userAuthorized;

    @JsonProperty("national_id")
    private String nationalId;

    @JsonProperty("tax_id")
    private String taxId;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("company_name")
    private String companyName;

    /** true = fuerza una consulta nueva; false = reutiliza el resultado más reciente si existe. */
    @JsonProperty("force_creation")
    private boolean forceCreation;

    /** Referencia propia (max 128 chars) para correlacionar sin depender solo del check_id. */
    @JsonProperty("custom_input")
    private String customInput;
}
