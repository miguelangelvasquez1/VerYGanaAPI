package com.verygana2.mappers.pqrs;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Value;

import com.verygana2.dtos.pqrs.responses.PqrsAssetResponseDTO;
import com.verygana2.models.pqrs.PqrsAsset;

/**
 * Mapeo puro, sin I/O. {@code viewUrl} NO es una URL prefirmada de R2 —
 * apunta al endpoint propio del backend que hace streaming del objeto
 * privado (mismo patrón que ProductController.getPrivateProductImage /
 * ProductServiceImpl.streamPrivateProductImage). Esto evita depender de que
 * el navegador pueda golpear directo el endpoint S3-compatible de R2
 * (requiere CORS configurado en el bucket, que este proyecto no usa para
 * objetos privados — ver PqrsController.streamAsset).
 *
 * viewUrl se arma absoluta (con appBaseUrl) igual que
 * ProductServiceImpl.resolveImageUrl: una ruta relativa se resuelve contra
 * el origen del frontend que la renderiza (ej. el panel admin), no contra
 * este backend, y el <img>/<video> nunca llega a golpear el endpoint.
 */
@Mapper(componentModel = "spring")
public abstract class PqrsAssetMapper {

    @Value("${app.base-url}")
    protected String appBaseUrl;

    @Mapping(target = "viewUrl", expression = "java(appBaseUrl + \"/pqrs/assets/\" + asset.getId() + \"/view\")")
    public abstract PqrsAssetResponseDTO toResponseDTO(PqrsAsset asset);
}
