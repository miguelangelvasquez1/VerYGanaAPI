package com.verygana2.models;

import java.time.ZonedDateTime;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "phone_number_history")
@Data
@NoArgsConstructor
public class PhoneNumberHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private ZonedDateTime assignedAt;

    @Column(name = "detached_at")
    private ZonedDateTime detachedAt;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = ZonedDateTime.now();
        }
    }
}