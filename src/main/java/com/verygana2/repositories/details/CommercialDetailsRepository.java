package com.verygana2.repositories.details;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.userDetails.CommercialDetails;

@Repository
public interface CommercialDetailsRepository extends JpaRepository<CommercialDetails, Long>{
    Optional<CommercialDetails> findByCompanyName(String companyName);
    Optional<CommercialDetails> findByUser_Id(Long userId);
}
