package com.verygana2.dtos.user.commercial.onboarding;

import com.verygana2.models.enums.AnnualRevenueRange;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.enums.commercial.PersonType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Resumen de lo capturado en el paso 3 (identificación jurídica), de solo lectura. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegalIdentificationSummaryDTO {
    private PersonType personType;
    private String companyName;
    private String nit;
    private String mercantileRegistration;
    private String legalRepFirstName;
    private String legalRepLastName;
    private DocumentType legalRepDocType;
    private String legalRepDocNumber;
    private boolean legalRepPepDeclaration;
    private AnnualRevenueRange annualIncomeRange;
    private String ciiuCode;
    private String economicActivityDescription;
    private String address;
    private String municipalityName;
    private String departmentName;
}
