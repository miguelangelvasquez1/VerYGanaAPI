package com.verygana2.services.details;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.user.admin.consumers.ConsumerResponseDTO;
import com.verygana2.dtos.user.admin.consumers.ConsumerSummaryResponseDTO;
import com.verygana2.dtos.user.consumer.requests.ConsumerUpdateProfileRequestDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerInitialDataResponseDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerProfileResponseDTO;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.enums.Gender;
import com.verygana2.models.enums.UserLevel;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ConsumerDetailsServiceImpl implements ConsumerDetailsService{

    private final ConsumerDetailsRepository consumerDetailsRepository;
    private final UserMapper consumerDetailsMapper;

    @Value("${financial.key-value-cents:1000}")
    private long keyValueCents;

    @Override
    @Transactional(readOnly = true)
    public Long getConsumerAvailableKeys(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }
        return getConsumerById(consumerId).getKeyWallet().getAvailableKeysCents() / keyValueCents;
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumerDetails getConsumerById(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }
        return consumerDetailsRepository.findById(consumerId).orElseThrow(() -> new ObjectNotFoundException("Consumer with id:" + consumerId + " not found ", ConsumerDetails.class));
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumerInitialDataResponseDTO getConsumerInitialData(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }
        ConsumerDetails consumer = consumerDetailsRepository.findById(consumerId).orElseThrow(() -> new ObjectNotFoundException("Consumer with id:" + consumerId + " not found", ConsumerDetails.class));
        ConsumerInitialDataResponseDTO initialData = consumerDetailsMapper.toConsumerInitialDataResponseDTO(consumer);
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
        return consumerDetailsRepository.existsById(Objects.requireNonNull(consumerId));
    }

    @Override
    public PagedResponse<ConsumerSummaryResponseDTO> getConsumers(UserLevel level, String search, UserState userState,
            Integer maxAge, Integer minAge, Gender gender, String departmentCode, String municipalityCode, ZonedDateTime startDate,
            ZonedDateTime endDate, Pageable pageable) {

        return PagedResponse.from(consumerDetailsRepository.getConsumers(level, search, userState, maxAge, minAge, gender, departmentCode, municipalityCode, startDate, endDate, pageable).map(consumerDetailsMapper::toConsumerSummaryResponseDTO));
    }

    @Override
    public ConsumerResponseDTO getConsumer(UUID publicId) {
        return consumerDetailsMapper.toConsumerResponseDTO(consumerDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("Consumer with public id: " + publicId + " not found"))); 
    }
    
}
