package com.verygana2.services.details;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;

import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConsumerDetailsServiceImpl implements ConsumerDetailsService{

    private final ConsumerDetailsRepository consumerDetailsRepository;

    @Override
    public ConsumerDetails getConsumerById(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("user id must be positive");
        }
        return consumerDetailsRepository.findByUser_Id(consumerId).orElseThrow(() -> new ObjectNotFoundException("User with id: " + consumerId + " not found", ConsumerDetails.class));
    }

    @Override
    public boolean existsConsumerById(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("user id must be positive");
        }

        return consumerDetailsRepository.existsById(consumerId);
    }
    
}
