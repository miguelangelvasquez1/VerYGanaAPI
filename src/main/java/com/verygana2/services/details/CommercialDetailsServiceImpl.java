package com.verygana2.services.details;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.interfaces.details.CommercialDetailsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CommercialDetailsServiceImpl implements CommercialDetailsService {
    
    private final CommercialDetailsRepository commercialrDetailsRepository;
    private final UserMapper commercialDetailsMapper;

    @Override
    @Transactional(readOnly = true)
    public CommercialInitialDataResponseDTO getCommercialInitialData(Long commercialId) {
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }
        CommercialDetails commercialDetails = commercialrDetailsRepository.findById(commercialId)
            .orElseThrow(() -> new ObjectNotFoundException("Commercial with id:" + commercialId + " not found", CommercialDetails.class));
        CommercialInitialDataResponseDTO initialData = commercialDetailsMapper.toCommercialInitialDataResponseDTO(commercialDetails);
        return initialData;
    }
}
