package com.verygana2.models.inforequests;

import com.verygana2.models.enums.commercial.CommercialDocumentType;
import com.verygana2.models.enums.inforequests.RequestItemType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Elemento concreto que se pide: un campo, un documento o una aclaración. Nunca "actualice
 * toda su información".
 */
@Entity
@Table(name = "information_request_items")
@Getter
@Setter
public class InformationRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private InformationRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private RequestItemType itemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 30)
    private CommercialDocumentType documentType;

    /** Campo de la ficha o del perfil (p. ej. "taxProfile.currentCapacity"). */
    @Column(name = "field_path", length = 100)
    private String fieldPath;

    /** Sede a la que se refiere, si aplica: un permiso de otra sede no la satisface. */
    @Column(name = "establishment_id")
    private Long establishmentId;

    @Column(nullable = false, length = 500)
    private String description;

    /** Qué respuesta puede satisfacerlo: aclaración, corrección, documento actualizado... */
    @Column(name = "acceptable_response", length = 500)
    private String acceptableResponse;

    /** Evidencia ya aportada que se vincula en lugar de pedirla otra vez. */
    @Column(name = "linked_document_id")
    private Long linkedDocumentId;
}
