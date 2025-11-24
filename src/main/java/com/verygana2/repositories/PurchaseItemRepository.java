package com.verygana2.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.products.PurchaseItem;

@Repository
public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long>{
    
    @Query("SELECT SUM(p.quantity) FROM PurchaseItem p WHERE p.seller = :sellerId")
    Long countTotalSalesBySellerId (@Param("sellerId") Long sellerId);

}
