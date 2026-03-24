package com.verygana2.models.surveys;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.enums.TargetGender;
import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "surveys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Survey {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    @NotBlank(message = "Title is required")
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String title;
 
    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    @ManyToOne
    @JoinColumn(name = "creator_id", nullable = true)
    private CommercialDetails creator;
 
    @NotNull(message = "Reward amount is required")
    @DecimalMin(value = "0.0", inclusive = false)
    @Column(name = "reward_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal rewardAmount;
 
    @Column(name = "max_responses")
    private Integer maxResponses;
 
    @Column(name = "response_count")
    @Builder.Default
    private Integer responseCount = 0;
 
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SurveyStatus status = SurveyStatus.DRAFT;
 
    @Column(name = "starts_at")
    private LocalDateTime startsAt;
 
    @Column(name = "ends_at")
    private LocalDateTime endsAt;
 
    // Targeting fields (mirrors Ad targeting)
    @ManyToMany
    @JoinTable(
        name = "survey_categories",
        joinColumns = @JoinColumn(name = "survey_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @NotNull(message = "Categories are required")
    @Size(min = 1, message = "At least one category must be selected")
    @Builder.Default
    private List<Category> categories = new ArrayList<>();
 
    @ManyToMany
    @JoinTable(
        name = "survey_municipalities",
        joinColumns = @JoinColumn(name = "survey_id"),
        inverseJoinColumns = @JoinColumn(name = "municipality_code")
    )
    @Builder.Default
    private List<Municipality> targetMunicipalities = new ArrayList<>();
 
    @Column(name = "min_age")
    @Min(value = 13, message = "La edad mínima debe ser 13")
    private Integer minAge;
 
    @Column(name = "max_age")
    @Max(value = 100, message = "La edad máxima debe ser 100")
    private Integer maxAge;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "target_gender", length = 10)
    private TargetGender targetGender;
 
    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<SurveyQuestion> questions = new ArrayList<>();
 
    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SurveyResponse> responses = new ArrayList<>();
 
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
 
    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
 
    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        return status == SurveyStatus.ACTIVE
            && (startsAt == null || !now.isBefore(startsAt))
            && (endsAt == null || !now.isAfter(endsAt))
            && (maxResponses == null || responseCount < maxResponses);
    }
 
    public enum SurveyStatus {
        DRAFT, ACTIVE, PAUSED, CLOSED
    }
}