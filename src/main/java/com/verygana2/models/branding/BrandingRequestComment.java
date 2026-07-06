package com.verygana2.models.branding;

import java.time.Instant;

import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.enums.CommentAuthorRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "branding_request_comments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandingRequestComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branding_request_id", nullable = false)
    private BrandingRequest brandingRequest;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private Long authorUserId;

    @Column(nullable = false, length = 200)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentAuthorRole authorRole;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private BrandingRequestStatus relatedStatus;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
