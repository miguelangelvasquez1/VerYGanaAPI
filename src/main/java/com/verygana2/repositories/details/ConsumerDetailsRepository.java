package com.verygana2.repositories.details;

import java.util.Optional;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.verygana2.models.userDetails.ConsumerDetails;

@Repository
public interface ConsumerDetailsRepository extends JpaRepository<ConsumerDetails, Long> {
    Optional<ConsumerDetails> findByUser_Id(Long userId);
    boolean existsById(Long consumerId);
}
