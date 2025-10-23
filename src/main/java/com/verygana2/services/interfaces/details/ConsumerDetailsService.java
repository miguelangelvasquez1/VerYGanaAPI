package com.verygana2.services.interfaces.details;

import com.verygana2.models.userDetails.ConsumerDetails;

public interface ConsumerDetailsService {
    ConsumerDetails getConsumerById (Long consumerId);
    boolean existsConsumerById(Long consumerId);

}
