package com.verygana2.models.pets;

import java.time.Instant;

import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.models.enums.CommentAuthorRole;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Hilo de conversación de una solicitud de integración al catálogo, entre el
 * comercial que la pidió, el diseñador asignado y el admin.
 *
 * Mismo modelo que {@code BrandingRequestComment} del flujo de juegos brandeados:
 * el nombre del autor se copia al escribir en vez de resolverse por join, para que
 * el hilo siga siendo legible aunque después cambie el perfil o se borre la cuenta.
 */
@Entity
@Table(name = "catalog_request_comments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogRequestComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_request_id", nullable = false)
    private CatalogIntegrationRequest catalogRequest;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private Long authorUserId;

    @Column(nullable = false, length = 200)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentAuthorRole authorRole;

    /** En qué estado estaba la solicitud cuando se escribió: da contexto al leer el hilo. */
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private CatalogRequestStatus relatedStatus;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
