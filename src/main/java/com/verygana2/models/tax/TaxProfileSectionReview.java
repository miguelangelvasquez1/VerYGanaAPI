package com.verygana2.models.tax;

import java.time.ZonedDateTime;

import org.hibernate.annotations.Immutable;

import com.verygana2.models.enums.tax.SectionReviewResult;
import com.verygana2.models.enums.tax.TaxProfileSection;

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
 * Revisión de una sección de una versión de la ficha: lo acreditado frente a lo declarado.
 * Se agregan filas, nunca se modifican; la vigente de cada sección es la más reciente.
 */
@Entity
@Immutable
@Table(name = "tax_profile_section_reviews", indexes = {
        @Index(name = "idx_tax_section_review_version", columnList = "tax_profile_version_id")
})
@Getter
@Setter
public class TaxProfileSectionReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_profile_version_id", nullable = false)
    private TaxProfileVersion version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaxProfileSection section;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SectionReviewResult result;

    @Column(length = 1000)
    private String notes;

    @Column(name = "reviewed_by_user_id", nullable = false)
    private Long reviewedByUserId;

    @Column(name = "reviewed_at", nullable = false)
    private ZonedDateTime reviewedAt;
}
