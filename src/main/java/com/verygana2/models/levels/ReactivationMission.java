package com.verygana2.models.levels;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Misión especial que se activa cuando el usuario vuelve tras 31+ días inactivo.
 * Meta: ganar 200 XP en 7 días para restaurar beneficios.
 */
@Entity
@Table(name = "reactivation_mission")
@Getter @Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ReactivationMission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer_id", nullable = false)
    private Long consumerId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime startedAt;

    /** startedAt + 7 días */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** Meta fija según el documento: 200 XP en 7 días */
    @Column(name = "xp_goal", nullable = false)
    @Builder.Default
    private Long xpGoal = 200L;

    @Column(name = "xp_progress", nullable = false)
    @Builder.Default
    private Long xpProgress = 0L;

    @Column(nullable = false)
    @Builder.Default
    private boolean completed = false;

    /** Si expiró sin completarse */
    @Column(nullable = false)
    @Builder.Default
    private boolean expired = false;
}