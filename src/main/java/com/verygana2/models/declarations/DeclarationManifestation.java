package com.verygana2.models.declarations;

import java.time.ZonedDateTime;

import org.hibernate.annotations.Immutable;

import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.enums.declarations.DeclarationCode;
import com.verygana2.models.enums.declarations.ManifestationStatus;
import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Lo que ocurrió con la declaración en el apartado 9 de la Hoja Final del contrato: una
 * manifestación expresa, su ausencia, una negativa expresa o una falla técnica. Inmutable.
 * La ficha tributaria y el expediente la muestran por referencia; nunca piden otra.
 */
@Entity
@Immutable
@Table(name = "declaration_manifestations", indexes = {
        @Index(name = "idx_manifestation_commercial_code", columnList = "commercial_id, code")
})
@Getter
@Setter
public class DeclarationManifestation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DeclarationCode code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "declaration_text_id", nullable = false)
    private DeclarationText declarationText;

    /** Contrato en cuya Hoja Final se recogió, y la versión del PDF que el empresario revisó. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private CommercialContract contract;

    @Column(name = "contract_version", nullable = false)
    private int contractVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ManifestationStatus status;

    /** Motivo que dio el empresario al negarse, o descripción de la falla técnica. */
    @Column(length = 1000)
    private String detail;

    // ---- Actor y sus facultades al momento de actuar ----

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_legal_rep_name", length = 200)
    private String actorLegalRepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_legal_rep_doc_type", length = 5)
    private DocumentType actorLegalRepDocType;

    @Column(name = "actor_legal_rep_doc_number", length = 20)
    private String actorLegalRepDocNumber;

    // ---- Contexto ----

    @Column(name = "occurred_at", nullable = false)
    private ZonedDateTime occurredAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** Un reenvío con la misma clave devuelve esta fila en lugar de crear otra. */
    @Column(name = "idempotency_key", length = 120, unique = true)
    private String idempotencyKey;
}
