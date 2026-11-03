package com.verygana2.models.declarations;

import java.time.ZonedDateTime;

import org.hibernate.annotations.Immutable;

import com.verygana2.models.enums.declarations.DisclosureContext;
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
 * Evidencia de que el texto se puso a disposición del empresario. No es una aceptación:
 * consultar la declaración nunca la acepta.
 */
@Entity
@Immutable
@Table(name = "declaration_disclosures", indexes = {
        @Index(name = "idx_declaration_disclosure_commercial", columnList = "commercial_id")
})
@Getter
@Setter
public class DeclarationDisclosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "declaration_text_id", nullable = false)
    private DeclarationText declarationText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisclosureContext context;

    @Column(name = "disclosed_at", nullable = false)
    private ZonedDateTime disclosedAt;
}
