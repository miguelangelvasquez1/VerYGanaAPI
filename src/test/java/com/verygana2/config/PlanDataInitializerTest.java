package com.verygana2.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.finance.plans.Feature;
import com.verygana2.models.finance.plans.Feature.FeatureType;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.PlanFeature;
import com.verygana2.repositories.finance.plans.FeatureRepository;
import com.verygana2.repositories.finance.plans.PlanFeatureRepository;
import com.verygana2.repositories.finance.plans.PlanRepository;

/**
 * En una base nueva Flyway corre antes que este initializer, y V9__prosperity_ledger ya
 * inserta la feature PROSPERITY_THRESHOLD_MULTIPLIER. Si el initializer la vuelve a crear,
 * choca con el UNIQUE de features.code y la app no arranca.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanDataInitializer")
class PlanDataInitializerTest {

    private static final String PROSPERITY = "PROSPERITY_THRESHOLD_MULTIPLIER";

    @Mock private PlanRepository planRepository;
    @Mock private FeatureRepository featureRepository;
    @Mock private PlanFeatureRepository planFeatureRepository;

    @InjectMocks private PlanDataInitializer initializer;

    @BeforeEach
    void setUp() {
        lenient().when(planRepository.count()).thenReturn(0L);
        lenient().when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(featureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("base nueva: reutiliza la feature que creó la V9 en vez de insertarla otra vez")
    void reusesFeatureCreatedByMigration() {
        Feature fromMigration = Feature.builder().id(99L).code(PROSPERITY)
                .name("Multiplicador de umbral de prosperidad").type(FeatureType.LIMIT).build();
        when(featureRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(featureRepository.findByCode(PROSPERITY)).thenReturn(Optional.of(fromMigration));

        initializer.run(null);

        ArgumentCaptor<Feature> saved = ArgumentCaptor.forClass(Feature.class);
        verify(featureRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Feature::getCode).doesNotContain(PROSPERITY);

        // El STANDARD sigue recibiendo su multiplicador ×4, apuntando a la fila de la migración.
        assertThat(standardProsperity().getFeature()).isSameAs(fromMigration);
        assertThat(standardProsperity().getIntValue()).isEqualTo(4);
    }

    @Test
    @DisplayName("sin la fila de la migración, la crea como antes")
    void createsFeatureWhenMissing() {
        when(featureRepository.findByCode(anyString())).thenReturn(Optional.empty());

        initializer.run(null);

        ArgumentCaptor<Feature> saved = ArgumentCaptor.forClass(Feature.class);
        verify(featureRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Feature::getCode).containsOnlyOnce(PROSPERITY);
    }

    @Test
    @DisplayName("con planes ya sembrados no toca nada")
    void skipsWhenPlansExist() {
        when(planRepository.count()).thenReturn(3L);

        initializer.run(null);

        verify(featureRepository, never()).save(any());
        verify(planFeatureRepository, never()).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    private PlanFeature standardProsperity() {
        ArgumentCaptor<List<PlanFeature>> captor = ArgumentCaptor.forClass(List.class);
        verify(planFeatureRepository).saveAll(captor.capture());
        return captor.getValue().stream()
                .filter(pf -> pf.getPlan().getCode() == PlanCode.STANDARD
                        && PROSPERITY.equals(pf.getFeature().getCode()))
                .findFirst()
                .orElseThrow();
    }
}
