package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import com.verygana2.exceptions.StorageException;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.repositories.marketplace.ProductImageAssetRepository;
import com.verygana2.repositories.marketplace.ProductImageRef;
import com.verygana2.services.interfaces.marketplace.ProductImageRepairService.ProductImageIds;
import com.verygana2.services.interfaces.marketplace.ProductImageRepairService.ProductImageRepairReport;
import com.verygana2.storage.service.R2Service;

@DisplayName("ProductImageRepairServiceImpl")
class ProductImageRepairServiceImplTest {

    private ProductImageAssetRepository repository;
    private R2Service r2Service;
    private ProductImageRepairServiceImpl service;
    /** Objetos que "existen" en R2; copyObject agrega el destino para que la 2ª corrida lo vea. */
    private Set<String> bucket;

    @BeforeEach
    void setUp() {
        repository = mock(ProductImageAssetRepository.class);
        r2Service = mock(R2Service.class);
        service = new ProductImageRepairServiceImpl(repository, r2Service);
        bucket = new HashSet<>();
        when(r2Service.objectExists(anyString())).thenAnswer(inv -> bucket.contains(inv.<String>getArgument(0)));
        when(r2Service.copyObject(anyString(), anyString())).thenAnswer(inv -> {
            bucket.add(inv.<String>getArgument(1));
            return "ignored";
        });
    }

    private void givenRefs(ProductImageRef... refs) {
        when(repository.findImageRefsByProductStatus(eq(ProductStatus.ACTIVE), anyLong(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    long after = inv.getArgument(1);
                    return List.of(refs).stream().filter(r -> r.assetId() > after).toList();
                });
    }

    @Test
    @DisplayName("publica la imagen que solo está en privado y no borra la copia privada")
    void publishesActiveImageFoundOnlyInPrivate() {
        bucket.add("private/products/a.jpg");
        givenRefs(new ProductImageRef(1L, 10L, "products/a.jpg"));

        ProductImageRepairReport report = service.repairActiveProductImages();

        verify(r2Service).copyObject("private/products/a.jpg", "public/products/a.jpg");
        verify(r2Service, never()).deleteObject(anyString());
        assertThat(report.repaired()).isEqualTo(1);
        assertThat(report.checked()).isEqualTo(1);
        assertThat(bucket).contains("private/products/a.jpg", "public/products/a.jpg");
    }

    @Test
    @DisplayName("la segunda corrida no cambia nada")
    void secondRunChangesNothing() {
        bucket.add("private/products/a.jpg");
        givenRefs(new ProductImageRef(1L, 10L, "products/a.jpg"));
        service.repairActiveProductImages();

        ProductImageRepairReport second = service.repairActiveProductImages();

        verify(r2Service, times(1)).copyObject(anyString(), anyString());
        assertThat(second.alreadyPublic()).isEqualTo(1);
        assertThat(second.repaired()).isZero();
    }

    @Test
    @DisplayName("solo consulta productos ACTIVE")
    void onlyActiveProductsAreQueried() {
        givenRefs();

        service.repairActiveProductImages();

        verify(repository).findImageRefsByProductStatus(eq(ProductStatus.ACTIVE), anyLong(), any(Pageable.class));
        for (ProductStatus status : ProductStatus.values()) {
            if (status != ProductStatus.ACTIVE) {
                verify(repository, never()).findImageRefsByProductStatus(eq(status), anyLong(), any(Pageable.class));
            }
        }
    }

    @Test
    @DisplayName("reporta por id la imagen que no está en ninguna ruta y sigue con el resto")
    void reportsMissingImageByIdAndContinues() {
        bucket.add("private/products/b.jpg");
        givenRefs(new ProductImageRef(1L, 10L, "products/a.jpg"), new ProductImageRef(2L, 11L, "products/b.jpg"));

        ProductImageRepairReport report = service.repairActiveProductImages();

        assertThat(report.missing()).containsExactly(new ProductImageIds(1L, 10L));
        assertThat(report.repaired()).isEqualTo(1);
        verify(r2Service).copyObject("private/products/b.jpg", "public/products/b.jpg");
    }

    @Test
    @DisplayName("un error de almacenamiento va a failed, no a missing, y sigue con el resto")
    void storageErrorIsReportedApartAndContinues() {
        bucket.add("private/products/b.jpg");
        when(r2Service.objectExists("public/products/a.jpg")).thenThrow(new StorageException("Error verificando objeto"));
        givenRefs(new ProductImageRef(1L, 10L, "products/a.jpg"), new ProductImageRef(2L, 11L, "products/b.jpg"));

        ProductImageRepairReport report = service.repairActiveProductImages();

        assertThat(report.failed()).containsExactly(new ProductImageIds(1L, 10L));
        assertThat(report.missing()).isEmpty();
        assertThat(report.repaired()).isEqualTo(1);
    }

    @Test
    @DisplayName("recorre todos los lotes: el segundo pide después del último id del primero")
    void pagesThroughAllBatches() {
        ProductImageRef[] refs = LongStream.rangeClosed(1, 150)
                .mapToObj(i -> new ProductImageRef(i, i, "products/" + i + ".jpg")).toArray(ProductImageRef[]::new);
        for (ProductImageRef r : refs) {
            bucket.add("public/" + r.objectKey());
        }
        when(repository.findImageRefsByProductStatus(eq(ProductStatus.ACTIVE), anyLong(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    long after = inv.getArgument(1);
                    Pageable page = inv.getArgument(2);
                    return List.of(refs).stream().filter(r -> r.assetId() > after).limit(page.getPageSize()).toList();
                });

        ProductImageRepairReport report = service.repairActiveProductImages();

        ArgumentCaptor<Long> after = ArgumentCaptor.forClass(Long.class);
        verify(repository, times(2)).findImageRefsByProductStatus(eq(ProductStatus.ACTIVE), after.capture(), any(Pageable.class));
        assertThat(after.getAllValues()).containsExactly(0L, 100L);
        assertThat(report.checked()).isEqualTo(150);
        assertThat(report.alreadyPublic()).isEqualTo(150);
    }

    @Test
    @DisplayName("el reporte solo contiene ids y conteos")
    void reportContainsOnlyIds() {
        givenRefs(new ProductImageRef(1L, 10L, "products/secreto-123.jpg"));

        String text = service.repairActiveProductImages().toString();

        assertThat(text).doesNotContain("products/").doesNotContain("secreto").doesNotContain(".jpg");
        assertThat(text).contains("productId=1").contains("assetId=10");
    }
}
