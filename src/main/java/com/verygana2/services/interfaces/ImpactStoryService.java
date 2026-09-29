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
    /**
     * Una historia DELETED no existe para nadie (404). Un consumidor solo ve las PUBLISHED; un admin
     * también las DRAFT y ARCHIVED. Para quien no puede verla la respuesta es 404, no 403, así no
     * se revela que existe.
     */
    ImpactStoryResponseDTO findById(Long id, boolean isAdmin);
    ImpactStoryResponseDTO update(Long id, UpdateImpactStoryRequestDTO request);
    void delete(Long id);
}
