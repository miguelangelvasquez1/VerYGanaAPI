package com.verygana2.dtos.branding;

import java.time.Instant;

import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.enums.CommentAuthorRole;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BrandingRequestCommentDTO {

    private Long id;
    private String content;
    private String authorName;
    private CommentAuthorRole authorRole;
    private BrandingRequestStatus relatedStatus;
    private Instant createdAt;
}
