package com.verygana2.models.declarations;

import java.time.ZonedDateTime;

import org.hibernate.annotations.Immutable;

import com.verygana2.models.enums.declarations.DeclarationCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Texto literal de una declaración contractual, versionado e inmutable. Se siembra por
 * migración: una versión nueva es una fila nueva, nunca una edición.
 */
@Entity
@Immutable
@Table(name = "declaration_texts",
        uniqueConstraints = @UniqueConstraint(name = "uk_declaration_text_version", columnNames = { "code", "version" }))
@Getter
@Setter
public class DeclarationText {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DeclarationCode code;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(name = "full_text", nullable = false, length = 4000)
    private String fullText;

    /** SHA-256 del texto: permite comprobar que la evidencia apunta exactamente a este texto. */
    @Column(name = "text_hash", nullable = false, length = 64)
    private String textHash;

    @Column(name = "published_at", nullable = false)
    private ZonedDateTime publishedAt;

    @Column(nullable = false)
    private boolean active;
}
