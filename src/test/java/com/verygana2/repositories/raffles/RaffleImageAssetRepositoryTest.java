package com.verygana2.repositories.raffles;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleImageAsset;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para RaffleImageAssetRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:raffle-image-asset-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("RaffleImageAssetRepository (integración H2)")
class RaffleImageAssetRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private RaffleImageAssetRepository raffleImageAssetRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private Raffle persistRaffle(String title) {
        Raffle raffle = new Raffle();
        raffle.setTitle(title);
        raffle.setDescription("desc " + title);
        raffle.setRaffleType(RaffleType.STANDARD);
        raffle.setStartDate(now().minusDays(10));
        raffle.setEndDate(now().minusDays(2));
        raffle.setDrawDate(now().minusDays(1));
        raffle.setDrawMethod(DrawMethod.SYSTEM_RANDOM);
        raffle.setCreatedBy(1L);
        em.persist(raffle);
        em.flush();
        return raffle;
    }

    /**
     * Persiste el asset con objectKey/sizeBytes fijos y status/uploadedAt
     * explícitos. Como ambos van no-nulos, el @PrePersist del entity (que
     * solo aplica defaults cuando el campo es null) no los sobrescribe.
     */
    private RaffleImageAsset persistAsset(Raffle raffle, String objectKey, AssetStatus status,
            ZonedDateTime uploadedAt) {
        RaffleImageAsset asset = new RaffleImageAsset();
        asset.setObjectKey(objectKey);
        asset.setSizeBytes(1024L);
        asset.setStatus(status);
        asset.setUploadedAt(uploadedAt);
        asset.setRaffle(raffle);
        em.persist(asset);
        em.flush();
        return asset;
    }

    // ==================== findByRaffleId ====================

    @Nested
    @DisplayName("findByRaffleId")
    class FindByRaffleId {

        @Test
        @DisplayName("encuentra el asset asociado a la rifa")
        void findsAssetForRaffle() {
            Raffle raffle = persistRaffle("Rifa con asset");
            RaffleImageAsset asset = persistAsset(raffle, "raffle/with-asset.png", AssetStatus.VALIDATED, now());

            Optional<RaffleImageAsset> found = raffleImageAssetRepository.findByRaffleId(raffle.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(asset.getId());
        }

        @Test
        @DisplayName("retorna Optional.empty si la rifa no tiene asset")
        void returnsEmptyWhenNoAsset() {
            Raffle raffle = persistRaffle("Rifa sin asset");

            Optional<RaffleImageAsset> found = raffleImageAssetRepository.findByRaffleId(raffle.getId());

            assertThat(found).isEmpty();
        }
    }

    // ==================== findDeletableAssets ====================

    @Nested
    @DisplayName("findDeletableAssets")
    class FindDeletableAssets {

        @Test
        @DisplayName("trae solo los assets del status buscado con uploadedAt antes del threshold")
        void returnsOnlyMatchingStatusBeforeThreshold() {
            ZonedDateTime threshold = now();
            Raffle raffle = persistRaffle("Rifa assets vencidos");

            RaffleImageAsset deletable = persistAsset(raffle, "raffle/deletable.png", AssetStatus.ORPHANED,
                    threshold.minusDays(1));
            RaffleImageAsset wrongStatus = persistAsset(persistRaffle("Rifa status distinto"),
                    "raffle/wrong-status.png", AssetStatus.VALIDATED, threshold.minusDays(1));
            RaffleImageAsset tooRecent = persistAsset(persistRaffle("Rifa muy reciente"), "raffle/too-recent.png",
                    AssetStatus.ORPHANED, threshold.plusDays(1));

            List<RaffleImageAsset> found = raffleImageAssetRepository.findDeletableAssets(AssetStatus.ORPHANED,
                    threshold);

            assertThat(found).extracting(RaffleImageAsset::getId).containsExactly(deletable.getId());
            assertThat(wrongStatus).isNotNull();
            assertThat(tooRecent).isNotNull();
        }

        @Test
        @DisplayName("retorna lista vacía si nada coincide")
        void returnsEmptyWhenNoMatch() {
            Raffle raffle = persistRaffle("Rifa sin coincidencias");
            persistAsset(raffle, "raffle/no-match.png", AssetStatus.VALIDATED, now().minusDays(1));

            List<RaffleImageAsset> found = raffleImageAssetRepository.findDeletableAssets(AssetStatus.ORPHANED,
                    now());

            assertThat(found).isEmpty();
        }
    }
}
