// RaffleImageAssetRepository.java
package com.verygana2.repositories.raffles;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.verygana2.models.raffles.RaffleImageAsset;

public interface RaffleImageAssetRepository extends JpaRepository<RaffleImageAsset, Long> {
    Optional<RaffleImageAsset> findByRaffleId(Long raffleId);
}