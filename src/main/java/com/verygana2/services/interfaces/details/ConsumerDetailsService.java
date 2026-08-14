package com.verygana2.services.interfaces.details;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.user.admin.consumers.ConsumerResponseDTO;
import com.verygana2.dtos.user.admin.consumers.ConsumerSummaryResponseDTO;
import com.verygana2.dtos.user.consumer.requests.ConsumerUpdateProfileRequestDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerInitialDataResponseDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerProfileResponseDTO;
import com.verygana2.models.enums.Gender;
import com.verygana2.models.enums.UserLevel;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.ConsumerDetails;

public interface ConsumerDetailsService {
    Long getConsumerAvailableKeys(Long consumerId);
    ConsumerInitialDataResponseDTO getConsumerInitialData(Long consumerId);
    ConsumerProfileResponseDTO getConsumerProfile(Long consumerId);
    EntityUpdatedResponseDTO updateConsumerProfile(Long consumerId, ConsumerUpdateProfileRequestDTO request);
    ConsumerDetails getConsumerById (Long consumerId);
    boolean existsConsumerById(Long consumerId);
    PagedResponse<ConsumerSummaryResponseDTO> getConsumers (UserLevel level, String search, UserState userState,
    Integer maxAge, Integer minAge, Gender gender, String departmentCode, String municipalityCode, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable);
    ConsumerResponseDTO getConsumer (UUID publicId);
}

