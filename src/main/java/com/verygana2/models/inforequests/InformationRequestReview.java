package com.verygana2.models.inforequests;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.inforequests.RequestReviewResult;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Revisión de pertinencia, correspondencia y suficiencia de lo recibido. */
@Entity
@Table(name = "information_request_reviews")
@Getter
@Setter
public class InformationRequestReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private InformationRequest request;

    @Column(name = "reviewer_user_id", nullable = false)
    private Long reviewerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private RequestReviewResult result;

    @Column(length = 1000)
    private String notes;

    @Column(name = "reviewed_at", nullable = false)
    private ZonedDateTime reviewedAt;
}
