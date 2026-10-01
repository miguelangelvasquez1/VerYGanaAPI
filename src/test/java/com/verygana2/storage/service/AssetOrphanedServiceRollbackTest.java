package com.verygana2.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.verygana2.models.ImpactStory.StoryMediaAsset;
import com.verygana2.models.ImpactStory.StoryMediaAsset.MediaAssetStatus;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.raffles.RaffleImageAsset;
import com.verygana2.repositories.StoryMediaAssetRepository;
import com.verygana2.repositories.raffles.RaffleImageAssetRepository;

import jakarta.persistence.EntityManager;

/**
 * Demuestra por qué los marcados ORPHANED de los flujos "confirm" van por
 * {@link AssetOrphanedService} (REQUIRES_NEW): esos flujos relanzan la excepción desde su
 * {@code catch}, la transacción se revierte, y un marcado hecho DENTRO de ella se pierde —
 * el asset queda PENDING, el barrido no lo ve y el archivo se queda en R2.
 *
 * <p>Sin transacción de test (NOT_SUPPORTED) para que los commits sean reales. Flyway se
 * desactiva porque las migraciones de {@code db/migration} son para MySQL, no para H2.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:asset-orphaned-rollback-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ AssetOrphanedService.class, AssetOrphanedServiceRollbackTest.ConfirmLikeFlow.class })
@DisplayName("AssetOrphanedService — el marcado sobrevive al rollback del confirm (integración H2)")
class AssetOrphanedServiceRollbackTest {

    /** Reproduce la forma de los "confirm": transacción de clase, catch, marcar, relanzar. */
    @Service
    @Transactional
    static class ConfirmLikeFlow {
        @Autowired private AssetOrphanedService assetOrphanedService;
        @Autowired private RaffleImageAssetRepository raffleImageAssetRepository;

        /** Forma corregida: el marcado va por AssetOrphanedService. */
        public void confirmFailsUsingAssetOrphanedService(Long raffleAssetId, Long storyAssetId) {
            try {
                throw new IllegalStateException("falla el confirm");
            } catch (RuntimeException e) {
                assetOrphanedService.markRaffleImageAssetsAsOrphanedByIds(List.of(raffleAssetId));
                assetOrphanedService.markImpactStoryAssetsAsOrphanedByIds(List.of(storyAssetId));
                throw e;
            }
        }

        /** Forma anterior: el marcado se hacía con el repositorio, dentro de la misma transacción. */
        public void confirmFailsMarkingInSameTransaction(Long raffleAssetId) {
            try {
                throw new IllegalStateException("falla el confirm");
            } catch (RuntimeException e) {
                RaffleImageAsset asset = raffleImageAssetRepository.findById(raffleAssetId).orElseThrow();
                asset.setStatus(AssetStatus.ORPHANED);
                raffleImageAssetRepository.save(asset);
                throw e;
            }
        }
    }

    @Autowired private PlatformTransactionManager txManager;
    @Autowired private EntityManager em;
    @Autowired private ConfirmLikeFlow flow;
    @Autowired private RaffleImageAssetRepository raffleImageAssetRepository;
    @Autowired private StoryMediaAssetRepository storyMediaAssetRepository;

    private RaffleImageAsset persistPendingRaffleAsset(String key) {
        RaffleImageAsset asset = RaffleImageAsset.builder()
                .objectKey(key).sizeBytes(10L).status(AssetStatus.PENDING)
                .uploadedAt(ZonedDateTime.now()).build();
        em.persist(asset);
        return asset;
    }

    @Test
    @DisplayName("marcando por AssetOrphanedService el asset queda ORPHANED aunque el confirm se revierta")
    void marksSurviveRollback() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Long[] ids = tx.execute(s -> {
            RaffleImageAsset raffleAsset = persistPendingRaffleAsset("raffles/rb-1.jpg");
            StoryMediaAsset storyAsset = StoryMediaAsset.builder()
                    .objectKey("impact-stories/2026/rb-1.jpg").sizeBytes(10L)
                    .mediaType(MediaType.IMAGE).status(MediaAssetStatus.PENDING).build();
            em.persist(storyAsset);
            em.flush();
            return new Long[] { raffleAsset.getId(), storyAsset.getId() };
        });

        assertThatThrownBy(() -> flow.confirmFailsUsingAssetOrphanedService(ids[0], ids[1]))
                .isInstanceOf(IllegalStateException.class);

        AssetStatus raffleStatus = tx.execute(
                s -> raffleImageAssetRepository.findById(ids[0]).orElseThrow().getStatus());
        MediaAssetStatus storyStatus = tx.execute(
                s -> storyMediaAssetRepository.findById(ids[1]).orElseThrow().getStatus());

        assertThat(raffleStatus).isEqualTo(AssetStatus.ORPHANED);
        assertThat(storyStatus).isEqualTo(MediaAssetStatus.ORPHANED);
    }

    @Test
    @DisplayName("control: marcando dentro de la misma transacción (forma anterior) el ORPHANED se pierde y queda PENDING")
    void marksInSameTransactionAreLost() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Long id = tx.execute(s -> {
            RaffleImageAsset asset = persistPendingRaffleAsset("raffles/rb-2.jpg");
            em.flush();
            return asset.getId();
        });

        assertThatThrownBy(() -> flow.confirmFailsMarkingInSameTransaction(id))
                .isInstanceOf(IllegalStateException.class);

        AssetStatus status = tx.execute(s -> raffleImageAssetRepository.findById(id).orElseThrow().getStatus());

        assertThat(status)
                .as("el rollback deshace el marcado: ningún barrido de ORPHANED llegaría a este archivo")
                .isEqualTo(AssetStatus.PENDING);
    }
}
