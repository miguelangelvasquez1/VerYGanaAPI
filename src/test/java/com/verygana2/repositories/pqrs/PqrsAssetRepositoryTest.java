package com.verygana2.repositories.pqrs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.pqrs.PqrsAssetStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.pqrs.PqrsAsset;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PqrsAssetRepository. Cubre la
 * búsqueda por lote de ids y, sobre todo, la consulta de limpieza
 * {@code findDeletableAssets}, cuyo caso más delicado es que un asset
 * VALIDATED ya reclamado (con {@code pqrs} seteado) NUNCA debe considerarse
 * basura aunque esté vencido.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:pqrs-asset-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PqrsAssetRepository (integración H2)")
class PqrsAssetRepositoryTest {

    private static final AtomicLong SEQ = new AtomicLong(1);

    @Autowired
    private EntityManager em;

    @Autowired
    private PqrsAssetRepository pqrsAssetRepository;

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    /** Builder base con los NOT NULL cubiertos; cada test ajusta status/createdAt/pqrs según el caso. */
    private PqrsAsset.PqrsAssetBuilder baseAsset(Long ownerUserId) {
        long n = SEQ.getAndIncrement();
        return PqrsAsset.builder()
                .ownerUserId(ownerUserId)
                .objectKey("pqrs/assets/object-" + n)
                .originalFileName("evidencia-" + n + ".png")
                .sizeBytes(1024L)
                .mimeType(SupportedMimeType.IMAGE_PNG)
                .mediaType(MediaType.IMAGE);
    }

    /**
     * PqrsAsset.createdAt usa @CreationTimestamp, así que Hibernate lo pisa al
     * persistir. Para simular assets "vencidos" (createdAt en el pasado), se
     * fuerza el valor con un UPDATE nativo después del insert inicial.
     */
    private PqrsAsset persistWithCreatedAt(PqrsAsset asset, ZonedDateTime createdAt) {
        em.persist(asset);
        em.flush();
        em.createNativeQuery("UPDATE pqrs_assets SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, asset.getId())
                .executeUpdate();
        em.flush();
        em.clear();
        return em.find(PqrsAsset.class, asset.getId());
    }

    private Pqrs persistPqrs(ConsumerDetails consumer) {
        Pqrs pqrs = Pqrs.builder()
                .type(PqrsType.RECLAMO)
                .requester(consumer.getUser())
                .subject("Asunto asset repo")
                .description("Descripción asset repo")
                .dueDate(now().plusDays(5))
                .build();
        em.persist(pqrs);
        em.flush();
        return pqrs;
    }

    // ==================== findAllByIdIn ====================

    @Nested
    @DisplayName("findAllByIdIn")
    class FindAllByIdIn {

        @Test
        @DisplayName("trae exactamente los ids pedidos, ignorando los que no existen")
        void returnsExactlyRequestedIdsIgnoringMissingOnes() {
            PqrsAsset a1 = baseAsset(1L).status(PqrsAssetStatus.VALIDATED).build();
            em.persist(a1);
            PqrsAsset a2 = baseAsset(1L).status(PqrsAssetStatus.VALIDATED).build();
            em.persist(a2);
            PqrsAsset other = baseAsset(1L).status(PqrsAssetStatus.VALIDATED).build();
            em.persist(other);
            em.flush();

            List<Long> requestedIds = List.of(a1.getId(), a2.getId(), 999_999L);

            List<PqrsAsset> result = pqrsAssetRepository.findAllByIdIn(requestedIds);

            assertThat(result).extracting(PqrsAsset::getId)
                    .containsExactlyInAnyOrder(a1.getId(), a2.getId());
        }
    }

    // ==================== findDeletableAssets ====================

    @Nested
    @DisplayName("findDeletableAssets")
    class FindDeletableAssets {

        @Test
        @DisplayName("un asset ORPHANED vencido aparece")
        void orphanedExpiredAssetAppears() {
            ZonedDateTime threshold = now();
            PqrsAsset orphaned = persistWithCreatedAt(
                    baseAsset(1L).status(PqrsAssetStatus.ORPHANED).build(), threshold.minusDays(1));

            List<PqrsAsset> result = pqrsAssetRepository.findDeletableAssets(threshold);

            assertThat(result).extracting(PqrsAsset::getId).contains(orphaned.getId());
        }

        @Test
        @DisplayName("un asset PENDING vencido aparece")
        void pendingExpiredAssetAppears() {
            ZonedDateTime threshold = now();
            PqrsAsset pending = persistWithCreatedAt(
                    baseAsset(1L).status(PqrsAssetStatus.PENDING).build(), threshold.minusDays(1));

            List<PqrsAsset> result = pqrsAssetRepository.findDeletableAssets(threshold);

            assertThat(result).extracting(PqrsAsset::getId).contains(pending.getId());
        }

        @Test
        @DisplayName("un asset VALIDATED nunca reclamado (pqrs=null) y vencido aparece")
        void validatedUnclaimedExpiredAssetAppears() {
            ZonedDateTime threshold = now();
            PqrsAsset validatedUnclaimed = persistWithCreatedAt(
                    baseAsset(1L).status(PqrsAssetStatus.VALIDATED).pqrs(null).build(), threshold.minusDays(1));

            List<PqrsAsset> result = pqrsAssetRepository.findDeletableAssets(threshold);

            assertThat(result).extracting(PqrsAsset::getId).contains(validatedUnclaimed.getId());
        }

        @Test
        @DisplayName("caso delicado: un asset VALIDATED ya reclamado (pqrs seteado) NO aparece aunque esté vencido")
        void validatedClaimedExpiredAssetDoesNotAppear() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            Pqrs pqrs = persistPqrs(consumer);
            ZonedDateTime threshold = now();

            PqrsAsset validatedClaimed = persistWithCreatedAt(
                    baseAsset(consumer.getUser().getId()).status(PqrsAssetStatus.VALIDATED).pqrs(pqrs).build(),
                    threshold.minusDays(1));

            List<PqrsAsset> result = pqrsAssetRepository.findDeletableAssets(threshold);

            assertThat(result).extracting(PqrsAsset::getId).doesNotContain(validatedClaimed.getId());
        }

        @Test
        @DisplayName("cualquier asset con createdAt >= threshold no aparece sin importar el status")
        void assetNotYetExpiredNeverAppearsRegardlessOfStatus() {
            ZonedDateTime threshold = now();

            PqrsAsset freshOrphaned = persistWithCreatedAt(
                    baseAsset(1L).status(PqrsAssetStatus.ORPHANED).build(), threshold.plusDays(1));
            PqrsAsset freshPending = persistWithCreatedAt(
                    baseAsset(1L).status(PqrsAssetStatus.PENDING).build(), threshold.plusDays(1));
            PqrsAsset freshValidatedUnclaimed = persistWithCreatedAt(
                    baseAsset(1L).status(PqrsAssetStatus.VALIDATED).pqrs(null).build(), threshold.plusDays(1));

            List<PqrsAsset> result = pqrsAssetRepository.findDeletableAssets(threshold);

            assertThat(result).extracting(PqrsAsset::getId)
                    .doesNotContain(freshOrphaned.getId(), freshPending.getId(), freshValidatedUnclaimed.getId());
        }
    }
}
