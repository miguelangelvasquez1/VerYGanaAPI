package com.verygana2.repositories.details;

import java.util.Optional;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.verygana2.models.userDetails.ConsumerDetails;

@Repository
public interface ConsumerDetailsRepository extends JpaRepository<ConsumerDetails, Long> {
    @Query("""
            SELECT c FROM ConsumerDetails c
            JOIN FETCH c.user u
            WHERE c.id = :consumerId
            """)
    Optional<ConsumerDetails> findConsumerProfileById(@Param("consumerId") Long consumerId);
}
