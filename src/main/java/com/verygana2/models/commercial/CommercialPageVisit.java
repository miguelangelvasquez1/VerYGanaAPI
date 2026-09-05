package com.verygana2.models.commercial;

import java.time.ZonedDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.verygana2.models.ads.Ad;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un clic de un consumer sobre el enlace que redirige a la página oficial del
 * empresario (métrica "Remisión", exclusiva del plan Premium — ver
 * {@code CAN_VIEW_PAGE_VISIT_METRICS}).
 *
 * <p>Se registra vía {@code POST /commercials/page-visits} y se consulta agregada
 * vía {@code GET /commercials/report/page-visits}. Cada fila se atribuye al
 * comercial y, cuando aplica, al anuncio de origen.
 */
@Entity
@Table(
    name = "commercial_page_visits",
    indexes = {
        @Index(name = "idx_page_visit_commercial_created", columnList = "commercial_id, created_at"),
        @Index(name = "idx_page_visit_ad", columnList = "ad_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialPageVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresario cuya página se visitó. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    /** Anuncio que llevaba el enlace (atribución de origen). Nullable para orígenes futuros. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ad_id")
    private Ad ad;

    /** Consumer que hizo el clic. Nullable por si a futuro se registra tráfico anónimo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id")
    private ConsumerDetails consumer;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private PageVisitSource source = PageVisitSource.AD;

    /** Hash no reversible del consumer (de {@code ConsumerDetails.userHash}) para conteo sin PII. */
    @Column(name = "user_hash", length = 64)
    private String userHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    /** De dónde vino el clic. Hoy solo desde anuncios; extensible a perfil/juego/encuesta. */
    public enum PageVisitSource {
        AD, PROFILE, GAME, SURVEY, OTHER
    }
}
