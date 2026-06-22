package com.verygana2.models.levels;

import java.time.LocalDateTime;

import com.verygana2.models.enums.UserLevel;
import com.verygana2.models.userDetails.ConsumerDetails;

import jakarta.persistence.*;
import lombok.*;

/**
 * Perfil de nivel de un ConsumerDetails.
 * Se crea automáticamente al registrarse el usuario (ver LevelService).
 */
@Entity
@Table(name = "user_level_profile")
@Getter @Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UserLevelProfile {

    @Id
    @Column(name = "consumer_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "consumer_id")
    private ConsumerDetails consumer;

    @Column(nullable = false)
    @Builder.Default
    private Long xpTotal = 0L;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserLevel currentLevel = UserLevel.BRONCE;

    /** true = beneficios pausados por inactividad > 31 días */
    @Column(nullable = false)
    @Builder.Default
    private boolean benefitsPaused = false;

    /** Última vez que el usuario realizó cualquier actividad */
    @Column(nullable = false)
    private LocalDateTime lastActivityAt;

    /** true = hay misión de reactivación activa */
    @Column(nullable = false)
    @Builder.Default
    private boolean reactivationMissionActive = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
