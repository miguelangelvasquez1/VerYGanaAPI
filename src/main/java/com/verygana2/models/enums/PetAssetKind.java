package com.verygana2.models.enums;

import java.util.Set;

/**
 * Destinos de los assets que sube el diseñador al bucket de mascotas.
 *
 * Cada destino define su propia carpeta y sus formatos: un sprite de catálogo tiene
 * que ser imagen, pero un objeto de escena puede ser un video (el build tiene
 * SpawnImage y SpawnVideoDelayed).
 */
public enum PetAssetKind {

    /** Sprite de un ítem del catálogo → PetCatalogItem.spriteObjectKey */
    CATALOG_SPRITE(
            "catalog-sprites/",
            Set.of(SupportedMimeType.IMAGE_PNG,
                   SupportedMimeType.IMAGE_JPEG,
                   SupportedMimeType.IMAGE_JPG,
                   SupportedMimeType.IMAGE_WEBP)),

    /** Objeto de una escena → PetSceneObject.objectKey */
    SCENE_OBJECT(
            "scene-objects/",
            Set.of(SupportedMimeType.IMAGE_PNG,
                   SupportedMimeType.IMAGE_JPEG,
                   SupportedMimeType.IMAGE_JPG,
                   SupportedMimeType.IMAGE_WEBP,
                   SupportedMimeType.VIDEO_MP4,
                   SupportedMimeType.VIDEO_QUICK_TIME));

    private final String keyPrefix;
    private final Set<SupportedMimeType> allowedTypes;

    PetAssetKind(String keyPrefix, Set<SupportedMimeType> allowedTypes) {
        this.keyPrefix = keyPrefix;
        this.allowedTypes = allowedTypes;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public Set<SupportedMimeType> getAllowedTypes() {
        return allowedTypes;
    }

    public boolean allows(SupportedMimeType type) {
        return allowedTypes.contains(type);
    }
}
