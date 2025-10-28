package com.verygana2.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.verygana2.models.treasury.PlatformTreasury;

public interface PlatformTreasuryRepository extends JpaRepository<PlatformTreasury, Long>{
    @Query("SELECT pt FROM PlatformTreasury pt WHERE pt.id = 1")
    Optional<PlatformTreasury> findTreasury();
}
