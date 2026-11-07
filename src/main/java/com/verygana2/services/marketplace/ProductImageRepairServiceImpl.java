package com.verygana2.services.marketplace;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.verygana2.exceptions.StorageException;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.repositories.marketplace.ProductImageAssetRepository;
import com.verygana2.repositories.marketplace.ProductImageRef;
import com.verygana2.services.interfaces.marketplace.ProductImageRepairService;
import com.verygana2.storage.service.R2Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageRepairServiceImpl implements ProductImageRepairService {

    private static final int BATCH_SIZE = 100;
    private static final String PRIVATE_PREFIX = "private/";
    private static final String PUBLIC_PREFIX = "public/";

    private final ProductImageAssetRepository productImageAssetRepository;
    private final R2Service r2Service;

    @Override
    public ProductImageRepairReport repairActiveProductImages() {
        int checked = 0;
        int alreadyPublic = 0;
        int repaired = 0;
        List<ProductImageIds> missing = new ArrayList<>();
        List<ProductImageIds> failed = new ArrayList<>();

        long afterAssetId = 0L;
        List<ProductImageRef> batch;
        do {
            batch = productImageAssetRepository.findImageRefsByProductStatus(
                    ProductStatus.ACTIVE, afterAssetId, PageRequest.of(0, BATCH_SIZE));

            for (ProductImageRef ref : batch) {
                checked++;
                afterAssetId = ref.assetId();
                ProductImageIds ids = new ProductImageIds(ref.productId(), ref.assetId());
                try {
                    if (r2Service.objectExists(PUBLIC_PREFIX + ref.objectKey())) {
                        alreadyPublic++;
                    } else if (r2Service.objectExists(PRIVATE_PREFIX + ref.objectKey())) {
                        // No se borra la copia privada: un procedimiento de un solo uso no destruye nada.
                        r2Service.copyObject(PRIVATE_PREFIX + ref.objectKey(), PUBLIC_PREFIX + ref.objectKey());
                        repaired++;
                    } else {
                        missing.add(ids);
                    }
                } catch (StorageException e) {
                    // No se pudo hablar con R2: no es lo mismo que "no existe".
                    log.warn("Reparación de imagen: error de almacenamiento. productId={}, assetId={}",
                            ref.productId(), ref.assetId());
                    failed.add(ids);
                }
            }
        } while (batch.size() == BATCH_SIZE);

        return new ProductImageRepairReport(checked, alreadyPublic, repaired,
                List.copyOf(missing), List.copyOf(failed));
    }
}
