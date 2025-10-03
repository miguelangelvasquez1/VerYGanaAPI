package com.verygana2.models;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Entity
@Data
public class UserVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private User user;

    private String documentType;
    private String documentNumber;
    private String documentImageUrl;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    private LocalDateTime submittedAt;
    private LocalDateTime verifiedAt;

    public enum VerificationStatus {
        PENDING, APPROVED, REJECTED
    }
}
