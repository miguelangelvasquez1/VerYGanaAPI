package com.verygana2.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.products.PurchaseItem;

@Repository
public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long>{

    // ✅ Obtener items entregados sin review del usuario
    @Query("""
        SELECT pi FROM PurchaseItem pi 
        LEFT JOIN FETCH pi.product p
        LEFT JOIN FETCH pi.review r
        WHERE pi.purchase.consumer.id = :consumerId 
        AND pi.status = 'DELIVERED'
        AND pi.review IS NULL
        ORDER BY pi.deliveredAt DESC
    """)
    List<PurchaseItem> findDeliveredItemsWithoutReview(@Param("consumerId") Long consumerId);

    // ✅ Verificar que el PurchaseItem pertenece al usuario y puede ser revisado
    @Query("""
        SELECT CASE WHEN COUNT(pi) > 0 THEN true ELSE false END 
        FROM PurchaseItem pi 
        WHERE pi.id = :purchaseItemId 
        AND pi.purchase.consumer.id = :consumerId 
        AND pi.status = 'DELIVERED'
        AND pi.review IS NULL
    """)
    boolean canUserReviewPurchaseItem(
        @Param("purchaseItemId") Long purchaseItemId, 
        @Param("consumerId") Long consumerId
    );

     // ✅ Obtener PurchaseItem con validación de pertenencia
    @Query("""
        SELECT pi FROM PurchaseItem pi 
        LEFT JOIN FETCH pi.product 
        LEFT JOIN FETCH pi.purchase p
        WHERE pi.id = :purchaseItemId 
        AND p.consumer.id = :consumerId
    """)
    Optional<PurchaseItem> findByIdAndConsumerId(
        @Param("purchaseItemId") Long purchaseItemId, 
        @Param("consumerId") Long consumerId
    );
    
    @Query("SELECT SUM(p.quantity) FROM PurchaseItem p WHERE p.product.seller.id = :sellerId")
    Long countTotalSalesBySellerId (@Param("sellerId") Long sellerId);

}
