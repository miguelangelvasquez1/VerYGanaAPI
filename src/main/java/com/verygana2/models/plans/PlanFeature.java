    package com.verygana2.models.plans;

    import java.math.BigDecimal;

    import jakarta.persistence.Entity;
    import jakarta.persistence.GeneratedValue;
    import jakarta.persistence.GenerationType;
    import jakarta.persistence.Id;
    import jakarta.persistence.ManyToOne;
    import jakarta.persistence.Table;
    import lombok.AllArgsConstructor;
    import lombok.Builder;
    import lombok.Data;
    import lombok.NoArgsConstructor;

    @Entity
    @Table(name = "plan_features")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public class PlanFeature {
        
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne
        Plan plan;

        @ManyToOne
        Feature feature;

        // valores dinámicos
        Integer intValue;
        Boolean boolValue;
        BigDecimal decimalValue;
    }
