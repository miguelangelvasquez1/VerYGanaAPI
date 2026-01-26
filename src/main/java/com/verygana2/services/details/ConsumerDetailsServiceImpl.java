package com.verygana2.services.details;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.user.consumer.requests.ConsumerUpdateProfileRequestDTO;
import com.verygana2.dtos.user.consumer.responses.BalanceResponseDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerInitialDataResponseDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerProfileResponseDTO;
import com.verygana2.mappers.ConsumerDetailsMapper;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.interfaces.WalletService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ConsumerDetailsServiceImpl implements ConsumerDetailsService{

    private final ConsumerDetailsRepository consumerDetailsRepository;
    private final WalletService walletService;
    private final ConsumerDetailsMapper consumerDetailsMapper;

    @Override
    @Transactional(readOnly = true)
    public BalanceResponseDTO getConsumerBalance(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id exists");
        }
        BigDecimal availableBalance = walletService.getAvailableBalance(consumerId);
        return new BalanceResponseDTO(availableBalance);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumerDetails getConsumerById(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("user id must be positive");
        }
        return consumerDetailsRepository.findById(consumerId).orElseThrow(() -> new ObjectNotFoundException("Consumer with id:" + consumerId + " not found", ConsumerDetails.class));
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumerInitialDataResponseDTO getConsumerInitialData(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }
        ConsumerDetails consumerDetails = consumerDetailsRepository.findById(consumerId).orElseThrow(() -> new ObjectNotFoundException("Consumer with id:" + consumerId + " not found", ConsumerDetails.class));
        ConsumerInitialDataResponseDTO initialData = consumerDetailsMapper.toConsumerInitialDataResponseDTO(consumerDetails);
        initialData.setWalletAvailableBalance(walletService.getAvailableBalance(consumerId));
        return initialData;
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumerProfileResponseDTO getConsumerProfile(Long consumerId) {
        ConsumerDetails consumerData = consumerDetailsRepository.findConsumerProfileById(consumerId).orElseThrow(() -> new ObjectNotFoundException("Consumer with id: " + consumerId + " not found", ConsumerDetails.class));
        return consumerDetailsMapper.toConsumerProfileResponseDTO(consumerData);
    }

    @Override
    @SuppressWarnings("null")
    public EntityUpdatedResponseDTO updateConsumerProfile(Long consumerId, ConsumerUpdateProfileRequestDTO request) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }
        ConsumerDetails consumer = consumerDetailsRepository.findById(consumerId).orElseThrow(() -> new ObjectNotFoundException("Consumer with id:" + consumerId + " not found", ConsumerDetails.class));
        consumerDetailsMapper.updateConsumerFromDto(request, consumer);
        consumerDetailsRepository.save(consumer);
        return EntityUpdatedResponseDTO.builder().id(consumerId).message("Profile updated succesfully").timestamp(Instant.now()).build();
    }
    

    @Override
    @Transactional(readOnly = true)
    public boolean existsConsumerById(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return consumerDetailsRepository.existsById(consumerId);
    }

    
}
