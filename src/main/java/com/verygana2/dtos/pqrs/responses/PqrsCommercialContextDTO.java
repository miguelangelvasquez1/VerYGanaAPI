package com.verygana2.dtos.pqrs.responses;

import java.util.UUID;

import lombok.Data;

/** Contexto del comercial vendedor en disputa, para que el admin resuelva un PQRS de marketplace sin salir del detalle. */
@Data
public class PqrsCommercialContextDTO {
    private UUID commercialPublicId;
    private String companyName;
    private String nit;
    private String municipalityName;
    private String departmentName;
    private String contactEmail;
    private String contactPhone;
    private String currentPlanName;
}
