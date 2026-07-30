package com.verygana2.repositories.compliance;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.compliance.BackgroundCheck;

@Repository
public interface BackgroundCheckRepository extends JpaRepository<BackgroundCheck, Long> {

    List<BackgroundCheck> findByContractIdOrderByRequestedAtDesc(Long contractId);

    Optional<BackgroundCheck> findByCheckId(String checkId);
}
