package com.verygana2.services.interfaces.pqrs;

import java.io.IOException;
import java.util.List;

import com.verygana2.dtos.pqrs.requests.PreparePqrsAssetRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetResponseDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetUploadPermissionDTO;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.pqrs.PqrsAsset;

import jakarta.servlet.http.HttpServletResponse;

public interface PqrsAssetService {

    /** Genera la URL prefirmada de subida y persiste el registro en PENDING. */
    PqrsAssetUploadPermissionDTO prepareUpload(Long ownerUserId, PreparePqrsAssetRequestDTO dto);

    /** Verifica el objeto ya subido en R2 (tamaño + mime real) y lo pasa a VALIDATED. */
    PqrsAssetResponseDTO confirmUpload(Long ownerUserId, Long assetId);

    /**
     * Reclama para {@code pqrs} los assets indicados, validando dueño, estado
     * VALIDATED y que no estén ya reclamados por otro Pqrs. Lista null/vacía
     * es un no-op inmediato — la evidencia es opcional.
     */
    List<PqrsAsset> validateAndClaimAssets(List<Long> assetIds, Long ownerUserId, Pqrs pqrs);

    /**
     * Hace streaming directo del objeto privado desde R2 hacia el cliente
     * (sin URL prefirmada) — mismo patrón que
     * ProductServiceImpl.streamPrivateProductImage, evita depender de CORS
     * configurado en el bucket. Solo el dueño del archivo o un admin pueden verlo.
     */
    void streamAsset(Long assetId, Long callerUserId, boolean isAdmin, HttpServletResponse response) throws IOException;
}
