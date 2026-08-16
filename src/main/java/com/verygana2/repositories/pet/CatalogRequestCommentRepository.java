package com.verygana2.repositories.pet;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.pets.CatalogRequestComment;

public interface CatalogRequestCommentRepository extends JpaRepository<CatalogRequestComment, Long> {

    List<CatalogRequestComment> findByCatalogRequestIdOrderByCreatedAtAsc(Long catalogRequestId);
}
