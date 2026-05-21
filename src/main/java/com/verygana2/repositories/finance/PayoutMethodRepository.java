package com.verygana2.repositories.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.PayoutMethod;

@Repository
public interface PayoutMethodRepository extends JpaRepository<PayoutMethod, Long>{
    
    Page<PayoutMethod> findByCommercialId(Long commercialId, Pageable pageable);
}
