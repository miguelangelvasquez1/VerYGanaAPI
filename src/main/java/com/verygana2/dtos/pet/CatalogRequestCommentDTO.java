package com.verygana2.dtos.pet;

import java.time.Instant;

import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.models.enums.CommentAuthorRole;

public record CatalogRequestCommentDTO(
        Long id,
        String content,
        String authorName,
        CommentAuthorRole authorRole,
        CatalogRequestStatus relatedStatus,
        Instant createdAt
) {}
