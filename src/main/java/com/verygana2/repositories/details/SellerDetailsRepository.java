package com.verygana2.repositories.details;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.userDetails.SellerDetails;

@Repository
public interface SellerDetailsRepository extends JpaRepository<SellerDetails, Long>{
    Optional<SellerDetails> findByShopName(String shopName);
    Optional<SellerDetails> findByUser_Id(Long userId);
    boolean existsById(Long sellerId);
}