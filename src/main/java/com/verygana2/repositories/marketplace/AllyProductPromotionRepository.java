package com.verygana2.repositories.marketplace;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.marketplace.AllyProductPromotion;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.userDetails.CommercialDetails;

public interface AllyProductPromotionRepository extends JpaRepository<AllyProductPromotion, Long> {

    Optional<AllyProductPromotion> findByPremiumCommercial_IdAndProduct_Id(Long premiumCommercialId, Long productId);

    long countByPremiumCommercial_Id(Long premiumCommercialId);

    @Query("""
            SELECT a FROM AllyProductPromotion a
            JOIN FETCH a.product p
            WHERE a.premiumCommercial.id = :premiumCommercialId
            ORDER BY a.createdAt DESC
            """)
    List<AllyProductPromotion> findByPremiumCommercialIdOrderByCreatedAtDesc(
            @Param("premiumCommercialId") Long premiumCommercialId);

    @Query("""
            SELECT a.product FROM AllyProductPromotion a
            WHERE a.premiumCommercial.id = :premiumCommercialId
            AND a.product.status = com.verygana2.models.enums.marketplace.ProductStatus.ACTIVE
            """)
    List<Product> findPromotedActiveProducts(@Param("premiumCommercialId") Long premiumCommercialId);

    /** Aliados de un Premium: comerciales distintos dueños de los productos que promociona. */
    @Query("""
            SELECT DISTINCT a.product.commercial FROM AllyProductPromotion a
            WHERE a.premiumCommercial.id = :premiumCommercialId
            """)
    List<CommercialDetails> findDistinctAlliesOfPremium(@Param("premiumCommercialId") Long premiumCommercialId);

    /** Aliados de un Básico/Estándar: comerciales Premium distintos que promocionan alguno de sus productos. */
    @Query("""
            SELECT DISTINCT a.premiumCommercial FROM AllyProductPromotion a
            WHERE a.product.commercial.id = :commercialId
            """)
    List<CommercialDetails> findDistinctPromotersOfCommercial(@Param("commercialId") Long commercialId);
}
