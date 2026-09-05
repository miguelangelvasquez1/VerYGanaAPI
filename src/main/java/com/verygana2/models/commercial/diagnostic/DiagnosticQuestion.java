package com.verygana2.models.commercial.diagnostic;

import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.enums.commercial.diagnostic.DiagnosticQuestionType;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Una pregunta del cuestionario de diagnóstico.
 *
 * {@code fieldName} coincide con el campo homónimo de
 * {@code CommercialDiagnosticRequestDTO}; cada {@code value} de sus opciones
 * coincide con una constante del enum de ese campo. {@code dependsOnQuestionCode}
 * + {@code dependsOnValues} (CSV) modelan la adaptividad: la pregunta se muestra
 * solo si la respuesta a esa otra pregunta está entre esos valores (null = siempre).
 */
@Entity
@Table(name = "diagnostic_question")
@Getter
@Setter
public class DiagnosticQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private DiagnosticSection section;

    /** Identificador de la pregunta en el insumo técnico (p. ej. "M-1", "PR-5"). */
    @Column(nullable = false, length = 10)
    private String code;

    @Column(name = "field_name", nullable = false, length = 60)
    private String fieldName;

    @Column(name = "question_text", nullable = false, length = 500)
    private String text;

    /** Contenido del "¿Por qué me preguntan esto?" (§2). */
    @Column(name = "help_text", length = 1500)
    private String helpText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiagnosticQuestionType type;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "max_selections")
    private Integer maxSelections;

    /** true para MULTI_CHOICE cuyo orden de selección es la prioridad (M-1, P-1). */
    @Column(nullable = false)
    private boolean ordered;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "depends_on_question_code", length = 10)
    private String dependsOnQuestionCode;

    @Column(name = "depends_on_values", length = 300)
    private String dependsOnValues;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<DiagnosticQuestionOption> options = new ArrayList<>();

    public void addOption(DiagnosticQuestionOption option) {
        option.setQuestion(this);
        options.add(option);
    }
}
