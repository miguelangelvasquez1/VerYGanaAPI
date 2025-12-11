package com.verygana2.services.interfaces.details;

import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.user.consumer.requests.ConsumerUpdateProfileRequestDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerInitialDataResponseDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerProfileResponseDTO;
import com.verygana2.models.userDetails.ConsumerDetails;

public interface ConsumerDetailsService {
    ConsumerInitialDataResponseDTO getConsumerInitialData(Long consumerId);
    ConsumerProfileResponseDTO getConsumerProfile(Long consumerId);
    EntityUpdatedResponseDTO updateConsumerProfile(Long consumerId, ConsumerUpdateProfileRequestDTO request);
    ConsumerDetails getConsumerById (Long consumerId);
    boolean existsConsumerById(Long consumerId);

}

