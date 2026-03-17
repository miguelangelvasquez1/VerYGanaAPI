// PrizeImageAssetRepository.java
package com.verygana2.repositories.raffles;

import org.springframework.data.jpa.repository.JpaRepository;
import com.verygana2.models.raffles.PrizeImageAsset;

public interface PrizeImageAssetRepository extends JpaRepository<PrizeImageAsset, Long> {
}