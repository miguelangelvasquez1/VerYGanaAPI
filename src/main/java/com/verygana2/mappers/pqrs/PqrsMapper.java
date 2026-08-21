package com.verygana2.mappers.pqrs;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import com.verygana2.dtos.pqrs.responses.PqrsAdminDetailDTO;
import com.verygana2.dtos.pqrs.responses.PqrsCommercialContextDTO;
import com.verygana2.dtos.pqrs.responses.PqrsProductContextDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.utils.pqrs.RequesterNameResolver;

@Mapper(componentModel = "spring")
public abstract class PqrsMapper {

    @Autowired
    protected RequesterNameResolver requesterNameResolver;

    // "radicado" se resuelve automáticamente desde Pqrs.getBased() (mismo nombre de propiedad).
    // reasonCode se mapea automático (mismo nombre de propiedad en ambos lados).
    // assets se ignora: lo llena el servicio (necesita R2Service para las viewUrl, el mapper es puro).
    @Mapping(target = "purchaseItemId", source = "purchaseItem.id")
    @Mapping(target = "assets", ignore = true)
    public abstract PqrsResponseDTO toResponseDTO(Pqrs pqrs);

    @Mapping(target = "requesterId", source = "requester.id")
    @Mapping(target = "requesterEmail", source = "requester.email")
    @Mapping(target = "requesterPhone", source = "requester.phoneNumber")
    @Mapping(target = "requesterName", ignore = true)
    @Mapping(target = "purchaseItemId", source = "purchaseItem.id")
    @Mapping(target = "assets", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "commercial", ignore = true)
    public abstract PqrsAdminDetailDTO toAdminDetailDTO(Pqrs pqrs);

    @AfterMapping
    protected void setAdminComputedFields(@MappingTarget PqrsAdminDetailDTO dto, Pqrs pqrs) {
        dto.setRequesterName(requesterNameResolver.resolve(pqrs.getRequester()));
        setMarketplaceContext(dto, pqrs);
    }

    /**
     * Contexto de producto/comercial para que el admin resuelva una disputa de
     * marketplace sin salir del detalle del PQRS — null-safe: un PQRS genérico
     * (radicado desde /pqrs) no tiene purchaseItem.
     */
    private void setMarketplaceContext(PqrsAdminDetailDTO dto, Pqrs pqrs) {
        PurchaseItem item = pqrs.getPurchaseItem();
        if (item == null || item.getProduct() == null) {
            return;
        }
        Product product = item.getProduct();
        dto.setProduct(toProductContext(product));
        dto.setCommercial(toCommercialContext(product.getCommercial()));
    }

    @Mapping(target = "categoryName", source = "productCategory.name")
    @Mapping(target = "imageUrl", expression = "java(product.getImageUrl())")
    protected abstract PqrsProductContextDTO toProductContext(Product product);

    @Mapping(target = "commercialUserId", source = "user.id")
    @Mapping(target = "contactEmail", source = "user.email")
    @Mapping(target = "contactPhone", source = "user.phoneNumber")
    @Mapping(target = "currentPlanName", expression = "java(commercial.getCurrentPlanName())")
    protected abstract PqrsCommercialContextDTO toCommercialContext(CommercialDetails commercial);
}
