package com.verygana2.models.plans;

import jakarta.persistence.Column;
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

/**
 * Catálogo de capacidades configurables por plan.
 *
 * Códigos de referencia:
 *  MAX_PRODUCTS       → LIMIT   – número máximo de productos activos
 *  MAX_ADS            → LIMIT   – número máximo de anuncios activos
 *  MAX_BRANDED_GAMES  → LIMIT   – número máximo de juegos branded
 *  CAN_ADVERTISE      → BOOLEAN – puede crear y mostrar anuncios
 *  CAN_USE_GAMES      → BOOLEAN – puede usar experiencias gamificadas
 *  SALES_COMMISSION   → PERCENTAGE – porcentaje de comisión por venta
 *  VISIBILITY_BOOST   → PERCENTAGE – boost de visibilidad en la plataforma
 */
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

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureType type;

    public enum FeatureType {
        BOOLEAN,
        LIMIT,
        PERCENTAGE
    }
}