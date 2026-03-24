package com.verygana2.models.surveys;

import java.util.ArrayList;
import java.util.List;

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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "survey_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyQuestion {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;
 
    @NotBlank(message = "Question text is required")
    @Column(nullable = false, length = 500)
    private String text;
 
    @NotNull(message = "Question type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionType type;
 
    @Column(name = "order_index", nullable = false)
    @Min(0)
    @Builder.Default
    private Integer orderIndex = 0;
 
    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private Boolean required = true;
 
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<QuestionOption> options = new ArrayList<>();
 
    public enum QuestionType {
        SINGLE_CHOICE,
        MULTIPLE_CHOICE,
        TEXT,
        RATING,
        YES_NO
    }
}