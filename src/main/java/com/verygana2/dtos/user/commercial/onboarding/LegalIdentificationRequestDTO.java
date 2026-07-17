package com.verygana2.dtos.user.commercial.onboarding;

import com.verygana2.models.enums.AnnualRevenueRange;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.enums.commercial.PersonType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LegalIdentificationRequestDTO {

    @NotNull(message = "El tipo de persona (natural o jurídica) es requerido")
    private PersonType personType;

    @NotBlank(message = "La razón social es requerida")
    @Size(max = 200)
    private String companyName;

    @NotBlank(message = "El NIT es requerido")
    @Size(max = 20)
    private String nit;

    @Size(max = 20, message = "Matrícula mercantil must not exceed 20 characters")
    private String mercantileRegistration;

    @NotBlank(message = "El nombre completo del representante legal es requerido")
    @Size(max = 200)
    private String legalRepFullName;

    @NotNull(message = "El tipo de documento del representante legal es requerido")
    private DocumentType legalRepDocType;

    @NotBlank(message = "El número de documento del representante legal es requerido")
    @Size(max = 20)
    private String legalRepDocNumber;

    @NotNull(message = "La declaración de PEP del representante legal es requerida")
    private Boolean legalRepPepDeclaration;

    private AnnualRevenueRange annualIncomeRange;

    @NotBlank(message = "La descripción de la actividad económica es requerida")
    @Size(max = 500)
    private String economicActivityDescription;

    @Size(max = 10)
    private String ciiuCode;

    @NotBlank(message = "El domicilio es requerido")
    @Size(max = 300)
    private String address;

    @Size(max = 5, message = "El código de municipio debe ser el código DANE de 5 dígitos")
    private String municipalityCode;
}
