package com.verygana2.repositories.inforequests;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.inforequests.TechnicalIncident;

public interface TechnicalIncidentRepository extends JpaRepository<TechnicalIncident, Long> {

    List<TechnicalIncident> findByCommercial_IdOrderByOccurredAtDesc(Long commercialId);
}
