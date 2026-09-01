package com.verygana2.models.commercial.diagnostic;

import jakarta.persistence.Column;
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

/** Una opción de respuesta. {@code value} = constante del enum del campo; para BOOLEAN, "true"/"false". */
@Entity
@Table(name = "diagnostic_question_option")
@Getter
@Setter
public class DiagnosticQuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private DiagnosticQuestion question;

    @Column(name = "option_value", nullable = false, length = 50)
    private String value;

    @Column(nullable = false, length = 300)
    private String label;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** true para "Ninguna"/"Ninguno": al marcarla el front debe deseleccionar las demás. */
    @Column(nullable = false)
    private boolean exclusive;
}
