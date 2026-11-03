package com.verygana2.models.inforequests;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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
 * Respuesta del empresario: aclaración y/o documentos del expediente (nuevas versiones; las
 * anteriores quedan en el historial). Recibirla no cierra el requerimiento.
 */
@Entity
@Table(name = "information_request_responses")
@Getter
@Setter
public class InformationRequestResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private InformationRequest request;

    @Column(name = "responder_user_id", nullable = false)
    private Long responderUserId;

    @Column(length = 2000)
    private String message;

    @ElementCollection
    @CollectionTable(name = "information_request_response_documents",
            joinColumns = @JoinColumn(name = "response_id"))
    @Column(name = "document_id", nullable = false)
    private List<Long> documentIds = new ArrayList<>();

    @Column(name = "received_at", nullable = false)
    private ZonedDateTime receivedAt;
}
