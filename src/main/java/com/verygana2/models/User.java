package com.verygana2.models;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.UserDetails;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Users")
@Data
@NoArgsConstructor
public class User{

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true, optional = true)
    @JsonManagedReference
    private UserDetails userDetails;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false, length = 36)
    private UUID publicId;

    @Column(unique = true, nullable = false)
    private String email;
    @Column(unique = true, nullable = false)
    private String phoneNumber;
    @JsonIgnore
    private String password;
    @Column(nullable = false)

    @Enumerated(EnumType.STRING)
    private UserState userState;

    private ZonedDateTime registeredDate;

    @Column(name = "password_configured", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean passwordConfigured = true;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserVerification verification;

    // ── Bloqueo por intentos fallidos ───────────────────────────────────────
    @Column(name = "failed_login_attempts", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int failedLoginAttempts = 0;

    @Column(name = "account_locked_at")
    private Instant accountLockedAt;

    @PrePersist
    private void generatePublicId() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID();
        }
    }
}

