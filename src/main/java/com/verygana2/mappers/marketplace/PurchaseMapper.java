package com.verygana2.mappers.marketplace;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import com.verygana2.dtos.purchase.responses.CommercialPendingClaimResponseDTO;
import com.verygana2.dtos.purchase.responses.ConsumerPurchaseItemResponseDTO;
import com.verygana2.dtos.purchase.responses.ConsumerPurchaseResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseItemResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseResponseDTO;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.services.interfaces.marketplace.ProductReviewService;
import com.verygana2.storage.service.PublicUrlBuilder;


@Mapper(componentModel = "spring", uses = PublicUrlBuilder.class)
public abstract class PurchaseMapper {

    @Autowired
    protected ProductReviewService productReviewService;

    @Autowired
    protected ProductCodeEncryptor productCodeEncryptor;

    @Mapping(target = "totalItems", expression = "java(getTotalItems(purchase))")
    public abstract PurchaseResponseDTO toPurchaseResponseDTO(Purchase purchase);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", expression = "java(resolveProductName(purchaseItem))")
    @Mapping(target = "imageUrl", source = "product.imageAsset.objectKey", qualifiedByName = "publicUrl")
    public abstract PurchaseItemResponseDTO toPurchaseItemResponseDTO(PurchaseItem purchaseItem);

    @Mapping(target = "totalItems", expression = "java(getTotalItems(purchase))")
    public abstract ConsumerPurchaseResponseDTO toConsumerPurchaseResponseDTO(Purchase purchase);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", expression = "java(resolveProductName(purchaseItem))")
    @Mapping(target = "imageUrl", source = "product.imageAsset.objectKey", qualifiedByName = "publicUrl")
    @Mapping(target = "canBeReviewed", expression = "java(resolveCanBeReviewed(purchaseItem))")
    public abstract ConsumerPurchaseItemResponseDTO toConsumerPurchaseItemResponseDTO(PurchaseItem purchaseItem);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "imageUrl", source = "product.imageAsset.objectKey", qualifiedByName = "publicUrl")
    // Quien se presenta a recoger: el comprador, o el beneficiario si compró VERYGANA.
    @Mapping(target = "buyerName", source = "purchase.recipient.name")
    @Mapping(target = "documentType", source = "purchase.recipient.documentType")
    @Mapping(target = "documentNumber", source = "purchase.recipient.documentNumber")
    @Mapping(target = "buyerType", source = "purchase.buyerType")
    @Mapping(target = "purchaseAt", source = "purchase.completedAt")
    public abstract CommercialPendingClaimResponseDTO toCommercialPendingClaimResponseDTO (PurchaseItem purchaseItem);

    protected Integer getTotalItems(Purchase purchase) {
        return purchase.getItems().size();
    }

    /**
     * El producto puede haber sido purgado permanentemente (ver
     * ProductServiceImpl.purgeProduct), quedando product = null. En ese caso
     * usamos el snapshot tomado al momento de la compra.
     */
    protected String resolveProductName(PurchaseItem purchaseItem) {
        return purchaseItem.getProduct() != null
                ? purchaseItem.getProduct().getName()
                : purchaseItem.getProductNameSnapshot();
    }

    protected boolean resolveCanBeReviewed(PurchaseItem purchaseItem) {
        // Solo reseña quien compró: el beneficiario de un reconocimiento no es comprador.
        if (purchaseItem.getProduct() == null || purchaseItem.getPurchase().getConsumer() == null) {
            return false;
        }
        return productReviewService.canBeReviewed(
                purchaseItem.getProduct().getId(),
                purchaseItem.getPurchase().getConsumer().getId());
    }
}
