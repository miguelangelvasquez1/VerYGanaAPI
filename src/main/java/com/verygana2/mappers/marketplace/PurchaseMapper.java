package com.verygana2.mappers.marketplace;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.verygana2.dtos.purchase.responses.ConsumerPurchaseItemResponseDTO;
import com.verygana2.dtos.purchase.responses.ConsumerPurchaseResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseItemResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseResponseDTO;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.security.CodeEncryptor;
import com.verygana2.services.interfaces.marketplace.ProductReviewService;


@Mapper(componentModel = "spring")
public abstract class PurchaseMapper {

    @Autowired
    protected ProductReviewService productReviewService;

    @Autowired
    @Qualifier("productCodeEncryptor")
    protected CodeEncryptor productCodeEncryptor;

    @Mapping(target = "totalItems", expression = "java(getTotalItems(purchase))")
    public abstract PurchaseResponseDTO toPurchaseResponseDTO(Purchase purchase);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "imageUrl", source = "product.imageUrl")
    public abstract PurchaseItemResponseDTO toPurchaseItemResponseDTO(PurchaseItem purchaseItem);

    @Mapping(target = "totalItems", expression = "java(getTotalItems(purchase))")
    public abstract ConsumerPurchaseResponseDTO toConsumerPurchaseResponseDTO(Purchase purchase);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "imageUrl", source = "product.imageUrl")
    @Mapping(target = "canBeReviewed", expression = "java(productReviewService.canBeReviewed(purchaseItem.getProduct().getId(), purchaseItem.getPurchase().getConsumer().getId()))")
    @Mapping(target = "deliveredCode", expression = "java(decryptDeliveredCode(purchaseItem.getDeliveredCode()))")
    public abstract ConsumerPurchaseItemResponseDTO toConsumerPurchaseItemResponseDTO(PurchaseItem purchaseItem);

    protected Integer getTotalItems(Purchase purchase) {
        return purchase.getItems().size();
    }

    // deliveredCode se guarda cifrado (mismo ciphertext que ProductStock.code);
    // solo se descifra al exponerlo en la respuesta al consumidor dueño de la compra.
    protected String decryptDeliveredCode(String deliveredCode) {
        return deliveredCode == null ? null : productCodeEncryptor.decrypt(deliveredCode);
    }
}
