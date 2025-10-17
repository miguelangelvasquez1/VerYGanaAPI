package com.verygana2.services.details;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;

import com.verygana2.models.userDetails.SellerDetails;
import com.verygana2.repositories.details.SellerDetailsRepository;
import com.verygana2.services.interfaces.details.SellerDetailsService;

@Service
public class SellerDetailsServiceImpl implements SellerDetailsService{

    private SellerDetailsRepository sellerDetailsRepository;

    public SellerDetailsServiceImpl(SellerDetailsRepository sellerDetailsRepository){
        this.sellerDetailsRepository = sellerDetailsRepository;
    }

    @Override
    public SellerDetails getSellerById(Long sellerId) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("The seller id must be positive");
        }
        return sellerDetailsRepository.findByUser_Id(sellerId).orElseThrow(() -> new ObjectNotFoundException("The seller with id: " + sellerId + " not found", SellerDetails.class));
    }

    @Override
    public SellerDetails getSellerByShopName(String shopName) {
        if (shopName.isBlank()) {
            throw new IllegalArgumentException("The shop name cannot be empty");
        }
        return sellerDetailsRepository.findByShopName(shopName).orElseThrow(() -> new ObjectNotFoundException("The seller with shop name: " + shopName + " not found", SellerDetails.class));
    }
    
}
