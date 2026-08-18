package com.verygana2.services.pet;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.pet.PetAssetUploadRequestDTO;
import com.verygana2.dtos.pet.PetImageUploadPermissionDTO;
import com.verygana2.models.enums.PetAssetKind;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.userDetails.GameDesignerDetails;
import com.verygana2.repositories.details.GameDesignerDetailsRepository;
import com.verygana2.services.interfaces.pet.PetAssetService;
import com.verygana2.storage.service.R2Service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Subida de assets del diseñador al bucket de mascotas: sprites del catálogo y
 * objetos de escena.
 *
 * Mismo esquema de URL pre-firmada que usa el comercial para la foto del producto
 * (ver CatalogIntegrationRequestServiceImpl.prepareImageUpload): el archivo va
 * directo del navegador a R2, sin pasar por el backend.
 *
 * Va separado del servicio de solicitudes a propósito: el diseñador también crea
 * escenas y edita el catálogo sin que haya ninguna solicitud de por medio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetAssetServiceImpl implements PetAssetService {

    @Value("${cloudflare.r2.pets-bucket-name:verygana-pets}")
    private String petsBucketName;

    @Value("${pets.designer-asset.max-size-bytes:26214400}")
    private long maxAssetSizeBytes;

    private final GameDesignerDetailsRepository designerDetailsRepository;
    private final R2Service r2Service;

    @Override
    public PetImageUploadPermissionDTO prepareUpload(Long designerUserId, PetAssetUploadRequestDTO dto) {
        GameDesignerDetails designer = designerDetailsRepository.findByUser_Id(designerUserId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Diseñador no encontrado para userId=" + designerUserId));

        if (dto.sizeBytes() > maxAssetSizeBytes) {
            throw new ValidationException(
                    "Archivo muy grande. Máximo permitido: " + (maxAssetSizeBytes / 1024 / 1024) + " MB");
        }

        PetAssetKind kind = dto.kind();
        SupportedMimeType mime = resolveMime(dto.contentType(), kind);

        // El id del diseñador va en la clave por trazabilidad: a diferencia del
        // comercial no se usa para validar pertenencia, porque las escenas y el
        // catálogo son compartidos y cualquier diseñador puede editarlos.
        String objectKey = kind.getKeyPrefix() + designer.getId() + "/" + UUID.randomUUID();

        FileUploadPermissionDTO permission =
                r2Service.generateUploadUrlInBucket(petsBucketName, objectKey, mime.getMime());

        log.info("Permiso de subida {} generado para diseñador {} → {}",
                kind, designer.getId(), objectKey);

        return new PetImageUploadPermissionDTO(
                objectKey, permission.getUploadUrl(), permission.getExpiresInSeconds());
    }

    private SupportedMimeType resolveMime(String contentType, PetAssetKind kind) {
        SupportedMimeType mime;
        try {
            mime = SupportedMimeType.fromValue(contentType);
        } catch (ValidationException e) {
            throw new ValidationException(formatosPermitidos(kind));
        }
        if (!kind.allows(mime)) {
            throw new ValidationException(formatosPermitidos(kind));
        }
        return mime;
    }

    private String formatosPermitidos(PetAssetKind kind) {
        String lista = kind.getAllowedTypes().stream()
                .map(SupportedMimeType::getMime)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return "Formato no soportado para " + kind + ". Permitidos: " + lista;
    }
}
