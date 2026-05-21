package com.verygana2.models;


import com.verygana2.models.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventType;        // ej: "REFERRAL_COMPLETED"

    @Column(nullable = false)
    private String payload;          // JSON con los datos del evento

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;     // PENDING, PROCESSING, DONE, FAILED

    @Column(nullable = false)
    private Instant createdAt;

    private Instant processedAt;

    private int retryCount;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }
}