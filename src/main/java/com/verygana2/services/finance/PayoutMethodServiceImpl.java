package com.verygana2.services.finance;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.repositories.finance.PayoutMethodRepository;
import com.verygana2.services.interfaces.finance.PayoutMethodService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayoutMethodServiceImpl implements PayoutMethodService{

    private PayoutMethodRepository payoutMethodRepository;

    // Deberia retornar DTO
    @Override
    public PagedResponse<PayoutMethod> getByCommercialId(Long commercialId, Pageable pageable) {
        return PagedResponse.from(payoutMethodRepository.findByCommercialId(commercialId, pageable));
    }
    
}
