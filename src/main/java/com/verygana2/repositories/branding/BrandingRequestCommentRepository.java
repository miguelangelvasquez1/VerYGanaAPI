package com.verygana2.repositories.branding;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.branding.BrandingRequestComment;

public interface BrandingRequestCommentRepository extends JpaRepository<BrandingRequestComment, Long> {

    List<BrandingRequestComment> findByBrandingRequest_IdOrderByCreatedAtAsc(Long brandingRequestId);
}
