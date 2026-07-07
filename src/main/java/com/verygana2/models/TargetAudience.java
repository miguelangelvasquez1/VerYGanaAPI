package com.verygana2.models;

import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.enums.TargetGender;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "target_audiences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetAudience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToMany
    @JoinTable(
        name = "target_audience_categories",
        joinColumns = @JoinColumn(name = "target_audience_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @Builder.Default
    private List<Category> categories = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "target_audience_municipalities",
        joinColumns = @JoinColumn(name = "target_audience_id"),
        inverseJoinColumns = @JoinColumn(name = "municipality_code")
    )
    @Builder.Default
    private List<Municipality> targetMunicipalities = new ArrayList<>();

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_gender", length = 10)
    private TargetGender targetGender;
}
