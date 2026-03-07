package com.verygana2.models.ImpactStory;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "impact_stories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpactStory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "story_date", nullable = false)
    private LocalDate storyDate;

    @Column(name = "beneficiaries_count", nullable = false)
    private Integer beneficiariesCount;

    @Column(name = "invested_amount", precision = 15, scale = 2)
    private BigDecimal investedAmount;

    @Column(name = "invested_currency", length = 10)
    @Builder.Default
    private String investedCurrency = "COP";

    @Column(length = 100)
    private String location;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StoryStatus status = StoryStatus.DRAFT;

    @Column(name = "author_name", length = 150)
    private String authorName;

    /** Etiquetas separadas por coma (ej. "agua,comunidad,2024") */
    @Column(name = "tags", length = 500)
    private String tags;

    @OneToMany(mappedBy = "impactStory", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<StoryMediaAsset> mediaFiles = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void addMedia(StoryMediaAsset asset) {
        mediaFiles.add(asset);
        asset.setImpactStory(this);
    }

    public void removeMedia(StoryMediaAsset asset) {
        mediaFiles.remove(asset);
        asset.setImpactStory(null);
    }
}