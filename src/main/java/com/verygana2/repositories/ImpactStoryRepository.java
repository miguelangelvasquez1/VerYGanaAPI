package com.verygana2.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.ImpactStory.ImpactStory;
import com.verygana2.models.ImpactStory.StoryStatus;

@Repository
public interface ImpactStoryRepository extends JpaRepository<ImpactStory, Long> {
    
    Page<ImpactStory> findByStatus(StoryStatus status, Pageable pageable);
    Page<ImpactStory> findByStatusNot(StoryStatus status, Pageable pageable);
}
