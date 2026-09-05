package com.verygana2.models.commercial.diagnostic;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Catálogo versionado del cuestionario de diagnóstico comercial (secciones 6–12
 * del "Insumo técnico de caracterización empresarial"). Es contenido de solo
 * lectura para el front: se sirve en
 * {@code GET /commercials/onboarding/diagnostic/questionnaire} y se siembra desde
 * {@code db/seed/diagnostic-questionnaire-v1.json} vía
 * {@code DiagnosticQuestionnaireDataInitializer}.
 *
 * Las respuestas que envía el empresario siguen viajando en
 * {@code CommercialDiagnosticRequestDTO} — cada {@link DiagnosticQuestion#getFieldName()}
 * coincide con un campo de ese DTO.
 */
@Entity
@Table(name = "diagnostic_questionnaire")
@Getter
@Setter
public class DiagnosticQuestionnaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer version;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "opening_message", length = 1500)
    private String openingMessage;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "diagnostic_questionnaire_actions",
            joinColumns = @JoinColumn(name = "questionnaire_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "action_label", length = 120)
    private List<String> openingActions = new ArrayList<>();

    @OneToMany(mappedBy = "questionnaire", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<DiagnosticSection> sections = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }

    public void addSection(DiagnosticSection section) {
        section.setQuestionnaire(this);
        sections.add(section);
    }
}
