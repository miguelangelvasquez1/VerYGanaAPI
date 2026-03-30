package com.verygana2.services.interfaces;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.impactStory.CreateImpactStoryRequestDTO;
import com.verygana2.dtos.impactStory.ImpactStoryResponseDTO;
import com.verygana2.dtos.impactStory.UpdateImpactStoryRequestDTO;
import com.verygana2.models.ImpactStory.StoryStatus;

public interface ImpactStoryService {
    
    ImpactStoryResponseDTO create(CreateImpactStoryRequestDTO request);
    Page<ImpactStoryResponseDTO> findAllForConsumer(Pageable pageable);
    Page<ImpactStoryResponseDTO> findAll(Pageable pageable);
    Page<ImpactStoryResponseDTO> findByStatus(StoryStatus status, Pageable pageable);
    ImpactStoryResponseDTO findById(Long id);
    ImpactStoryResponseDTO update(Long id, UpdateImpactStoryRequestDTO request);
    void delete(Long id);
}
