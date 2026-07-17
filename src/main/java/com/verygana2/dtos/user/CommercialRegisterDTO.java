package com.verygana2.dtos.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.verygana2.models.enums.AnnualRevenueRange;
import com.verygana2.models.enums.DocumentType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CommercialRegisterDTO {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must have at least 6 characters")
    private String password;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "NIT is required")
    private String nit;

    // ==================== KYC / SAGRILAFT ====================

    @NotBlank(message = "CIIU code is required")
    @Size(max = 10, message = "CIIU code must not exceed 10 characters")
    @JsonProperty("ciiuCode")
    private String codigoCIIU;

    @Size(max = 20, message = "Matrícula mercantil must not exceed 20 characters")
    @JsonProperty("mercantileRegistration")
    private String matriculaMercantil;

    @NotNull(message = "Representative document type is required")
    @JsonProperty("legalRepDocumentType")
    private DocumentType representanteDocType;

    @NotBlank(message = "Representative document number is required")
    @Size(max = 20, message = "Document number must not exceed 20 characters")
    @JsonProperty("legalRepDocumentNumber")
    private String representanteDocNumero;

    @NotNull(message = "PEP declaration is required")
    @JsonProperty("legalRepPepDeclaration")
    private Boolean isPEP;

    @JsonProperty("annualIncomeRange")
    private AnnualRevenueRange ingresosAnualesRango;

    @Size(max = 5, message = "Municipality code must be a 5-digit DANE code")
    private String municipalityCode;

}
