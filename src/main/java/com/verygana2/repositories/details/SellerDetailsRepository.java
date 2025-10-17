package com.verygana2.repositories.details;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.userDetails.SellerDetails;

@Repository
public interface SellerDetailsRepository extends JpaRepository<SellerDetails, Long>{
    Optional<SellerDetails> findByShopName(String shopName);
    @Query("SELECT s from SellerDetails s WHERE s.userId = :userId")
    Optional<SellerDetails> findBySellerId(@Param("userId") Long userId);
}
