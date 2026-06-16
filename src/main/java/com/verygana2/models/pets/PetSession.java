package com.verygana2.models.pets;

import com.verygana2.models.userDetails.ConsumerDetails;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Entity
@Table(name = "pet_sessions")
@Data
@NoArgsConstructor
public class PetSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_token", unique = true, nullable = false)
    private String sessionToken;

    @Column(name = "user_hash", nullable = false)
    private String userHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id", nullable = false)
    private ConsumerDetails consumer;

    @Column(name = "start_time", nullable = false)
    private ZonedDateTime startTime;

    @Column(name = "expired", nullable = false)
    private boolean expired = false;

    public static PetSession create(ConsumerDetails consumer) {
        PetSession session = new PetSession();
        session.sessionToken = java.util.UUID.randomUUID().toString();
        session.userHash = consumer.getUserHash();
        session.consumer = consumer;
        session.startTime = ZonedDateTime.now();
        session.expired = false;
        return session;
    }
}
