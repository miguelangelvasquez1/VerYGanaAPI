package com.verygana2.models.plans;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "features")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feature {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    String code; // MAX_PRODUCTS, CAN_ADVERTISE, MAX_ADS
    String name;

    @Enumerated(EnumType.STRING)
    FeatureType type; // BOOLEAN, LIMIT, PERCENTAGE

    public enum FeatureType {
        BOOLEAN,
        LIMIT,
        PERCENTAGE
    }
}
