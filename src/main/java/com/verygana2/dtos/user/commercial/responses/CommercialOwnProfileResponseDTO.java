package com.verygana2.dtos.user.commercial.responses;

import com.verygana2.models.enums.DocumentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Perfil propio del comercial autenticado (GET /commercials/profile), con los
 * campos editables vía PUT /commercials/profile/edit más algo de identidad de
 * solo lectura para contexto en el formulario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialOwnProfileResponseDTO {

    // Solo lectura: corrección exclusiva de soporte/compliance.
    private String companyName;
    private String nit;
    private String mercantileRegistration;

    // Editables (ver CommercialUpdateProfileRequestDTO).
    private String email;
    private String phoneNumber;
    private String address;
    private String legalRepFirstName;
    private String legalRepLastName;
    private DocumentType legalRepDocType;
    private String legalRepDocNumber;
    private boolean legalRepPepDeclaration;
    private boolean whatsappAvailable;
    private String whatsappNumber;
}
