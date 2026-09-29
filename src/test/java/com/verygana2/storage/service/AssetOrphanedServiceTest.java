package com.verygana2.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.ImpactStory.ImpactStory;
import com.verygana2.models.ImpactStory.StoryMediaAsset;
import com.verygana2.models.ImpactStory.StoryMediaAsset.MediaAssetStatus;
import com.verygana2.models.branding.CorporateResource;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.PrizeImageAsset;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleImageAsset;
import com.verygana2.repositories.AdAssetRepository;
import com.verygana2.repositories.StoryMediaAssetRepository;
import com.verygana2.repositories.branding.CorporateResourceRepository;
import com.verygana2.repositories.finance.PayoutMethodCertificateAssetRepository;
import com.verygana2.repositories.games.AssetRepository;
import com.verygana2.repositories.marketplace.ProductCategoryImageAssetRepository;
import com.verygana2.repositories.marketplace.ProductImageAssetRepository;
import com.verygana2.repositories.raffles.PrizeImageAssetRepository;
import com.verygana2.repositories.raffles.RaffleImageAssetRepository;

/**
 * Guardas de los marcados ORPHANED que se hacen desde un {@code catch} de "confirm". Lo que
 * importa es qué NO se orfana: si el cliente reintenta o envía dos veces el mismo confirm, el
 * segundo intento falla al validar el asset ya reclamado y cae en ese catch. Sin la guarda, un
 * archivo que una rifa/historia/solicitud ya usa quedaría ORPHANED y el barrido lo borraría.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssetOrphanedService — guardas de vinculación")
class AssetOrphanedServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AdAssetRepository adAssetRepository;
    @Mock private StoryMediaAssetRepository storyMediaAssetRepository;
    @Mock private ProductImageAssetRepository productImageAssetRepository;
    @Mock private ProductCategoryImageAssetRepository productCategoryImageAssetRepository;
    @Mock private PayoutMethodCertificateAssetRepository payoutMethodCertificateAssetRepository;
    @Mock private RaffleImageAssetRepository raffleImageAssetRepository;
    @Mock private PrizeImageAssetRepository prizeImageAssetRepository;
    @Mock private CorporateResourceRepository corporateResourceRepository;

    @InjectMocks private AssetOrphanedService service;

    // ─── Rifas ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("imagen de rifa sin vincular (PENDING o VALIDATED): se orfana")
    void raffleImage_unattached_isOrphaned() {
        RaffleImageAsset pending = RaffleImageAsset.builder().id(1L).status(AssetStatus.PENDING).build();
        RaffleImageAsset validated = RaffleImageAsset.builder().id(2L).status(AssetStatus.VALIDATED).build();
        when(raffleImageAssetRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(pending, validated));

        service.markRaffleImageAssetsAsOrphanedByIds(List.of(1L, 2L));

        assertThat(pending.getStatus()).isEqualTo(AssetStatus.ORPHANED);
        assertThat(validated.getStatus()).isEqualTo(AssetStatus.ORPHANED);
    }

    @Test
    @DisplayName("imagen de una rifa ya creada (doble envío del confirm): NO se orfana")
    void raffleImage_attachedToRaffle_isUntouched() {
        RaffleImageAsset inUse = RaffleImageAsset.builder().id(3L).status(AssetStatus.VALIDATED)
                .raffle(new Raffle()).build();
        when(raffleImageAssetRepository.findAllById(List.of(3L))).thenReturn(List.of(inUse));

        service.markRaffleImageAssetsAsOrphanedByIds(List.of(3L));

        assertThat(inUse.getStatus()).isEqualTo(AssetStatus.VALIDATED);
    }

    @Test
    @DisplayName("imagen de premio: solo se orfana si sigue sin premio")
    void prizeImage_onlyUnattachedIsOrphaned() {
        PrizeImageAsset free = PrizeImageAsset.builder().id(4L).status(AssetStatus.PENDING).build();
        PrizeImageAsset inUse = PrizeImageAsset.builder().id(5L).status(AssetStatus.VALIDATED)
                .prize(new Prize()).build();
        when(prizeImageAssetRepository.findAllById(List.of(4L, 5L))).thenReturn(List.of(free, inUse));

        service.markPrizeImageAssetsAsOrphanedByIds(List.of(4L, 5L));

        assertThat(free.getStatus()).isEqualTo(AssetStatus.ORPHANED);
        assertThat(inUse.getStatus()).isEqualTo(AssetStatus.VALIDATED);
    }

    @Test
    @DisplayName("un asset ya DELETED u ORPHANED no cambia de estado")
    void alreadyTerminal_isUntouched() {
        RaffleImageAsset deleted = RaffleImageAsset.builder().id(6L).status(AssetStatus.DELETED).build();
        when(raffleImageAssetRepository.findAllById(List.of(6L))).thenReturn(List.of(deleted));

        service.markRaffleImageAssetsAsOrphanedByIds(List.of(6L));

        assertThat(deleted.getStatus()).isEqualTo(AssetStatus.DELETED);
    }

    // ─── Historias de impacto ───────────────────────────────────────────────

    @Test
    @DisplayName("media de historia: sin historia se orfana; ya vinculada a una historia NO")
    void storyMedia_onlyUnattachedIsOrphaned() {
        StoryMediaAsset free = StoryMediaAsset.builder().id(7L).status(MediaAssetStatus.PENDING).build();
        StoryMediaAsset inUse = StoryMediaAsset.builder().id(8L).status(MediaAssetStatus.VALIDATED)
                .impactStory(new ImpactStory()).build();
        when(storyMediaAssetRepository.findAllById(List.of(7L, 8L))).thenReturn(List.of(free, inUse));

        service.markImpactStoryAssetsAsOrphanedByIds(List.of(7L, 8L));

        assertThat(free.getStatus()).isEqualTo(MediaAssetStatus.ORPHANED);
        assertThat(inUse.getStatus()).isEqualTo(MediaAssetStatus.VALIDATED);
    }

    // ─── Recursos corporativos ──────────────────────────────────────────────

    @Test
    @DisplayName("recurso corporativo PENDING (confirmación fallida): se orfana")
    void corporateResource_pending_isOrphaned() {
        CorporateResource resource = CorporateResource.builder().id(9L).status(AssetStatus.PENDING).build();
        when(corporateResourceRepository.findById(9L)).thenReturn(Optional.of(resource));

        service.markCorporateResourceAsOrphaned(9L);

        assertThat(resource.getStatus()).isEqualTo(AssetStatus.ORPHANED);
    }

    @Test
    @DisplayName("recurso corporativo VALIDATED (en uso por la solicitud): NO se orfana")
    void corporateResource_validated_isUntouched() {
        CorporateResource resource = CorporateResource.builder().id(10L).status(AssetStatus.VALIDATED).build();
        when(corporateResourceRepository.findById(10L)).thenReturn(Optional.of(resource));

        service.markCorporateResourceAsOrphaned(10L);

        assertThat(resource.getStatus()).isEqualTo(AssetStatus.VALIDATED);
    }
}
