package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.purchase.responses.PurchaseItemResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseResponseDTO;
import com.verygana2.models.products.Purchase;
import com.verygana2.models.products.PurchaseItem;

@Mapper(componentModel = "spring")
public interface PurchaseMapper {
    
    @Mapping(target = "totalItems", expression = "java(getTotalItems(purchase))")
    PurchaseResponseDTO toPurchaseResponseDTO (Purchase purchase);

    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "imageUrl", source = "product.imageUrl")
    @Mapping(target = "productId", source = "product.id")
    PurchaseItemResponseDTO toPurchaseItemResponseDTO(PurchaseItem purchaseItem);

    default Integer getTotalItems (Purchase purchase){
        return purchase.getItems().size();
    }
}
