package com.verygana2.security.systemFeatures;

import java.time.ZonedDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_features")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String featureKey;

    @Column(nullable = false, unique = true)
    private String endpointPrefix;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureCategory category;

    private String description;

    private ZonedDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void updateDate() {
        updatedAt = ZonedDateTime.now();
    }

    public enum FeatureStatus {
        ENABLED,
        MAINTENANCE,
        READ_ONLY,
        DISABLED
    }

    public enum FeatureCategory {
        MONETIZACION,
        FINANCIERO,
        ADQUISICION,
        NOTIFICACIONES,
        IMPACTO,
        PERFILES,
        ADMINISTRACION
    }
}
