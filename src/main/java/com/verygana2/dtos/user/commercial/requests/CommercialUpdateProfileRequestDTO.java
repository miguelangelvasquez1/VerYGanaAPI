package com.verygana2.dtos.user.commercial.requests;

import com.verygana2.models.enums.DocumentType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Edición del perfil comercial post-onboarding: contacto (teléfono/correo/WhatsApp),
 * domicilio y representante legal (incluye cambiarlo por otra persona: nombre, documento
 * y declaración PEP). NIT y matrícula mercantil NO se editan aquí — quedan congelados por
 * el screening SAGRILAFT y el Contrato Marco ya firmado (ver
 * CommercialOnboardingServiceImpl#submitLegalIdentification); su corrección sigue
 * siendo exclusiva de soporte/compliance.
 */
@Data
public class CommercialUpdateProfileRequestDTO {

    @NotBlank(message = "El correo no puede estar vacío")
    @Email(message = "El formato del correo no es válido")
    private String email;

    @NotBlank(message = "El teléfono no puede estar vacío")
    @Pattern(regexp = "\\d{7,15}", message = "El teléfono debe tener entre 7 y 15 dígitos")
    private String phoneNumber;

    @NotBlank(message = "La dirección no puede estar vacía")
    @Size(max = 300)
    private String address;

    @NotBlank(message = "El nombre del representante legal es requerido")
    @Size(max = 100)
    private String legalRepFirstName;

    @NotBlank(message = "El apellido del representante legal es requerido")
    @Size(max = 100)
    private String legalRepLastName;

    // Identidad del representante legal: junto con el nombre, un cambio en cualquiera de
    // estos campos se trata como cambio de representante (bloqueado con un contrato en curso
    // y auditado). Ver CommercialDetailsServiceImpl#updateCommercialProfile.
    @NotNull(message = "El tipo de documento del representante legal es requerido")
    private DocumentType legalRepDocType;

    @NotBlank(message = "El número de documento del representante legal es requerido")
    @Size(max = 20)
    private String legalRepDocNumber;

    @NotNull(message = "La declaración de PEP del representante legal es requerida")
    private Boolean legalRepPepDeclaration;

    @NotNull(message = "Debe indicar si tiene WhatsApp disponible")
    private Boolean whatsappAvailable;

    // Requerido solo cuando whatsappAvailable = true (validado en el servicio,
    // ver CommercialDetailsServiceImpl#updateCommercialProfile).
    @Pattern(regexp = "\\d{7,15}", message = "El número de WhatsApp debe tener entre 7 y 15 dígitos")
    private String whatsappNumber;
}
