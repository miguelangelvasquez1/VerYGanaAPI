package com.verygana2.models.games;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game_session_metrics")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameSessionMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    private GameSession session;

    @Column(nullable = false)
    private String metricKey; // "moves", "accuracy", "reaction_time"

    @Column(nullable = false)
    private String metricType; // INT, DECIMAL, BOOLEAN, STRING

    @Column(nullable = false)
    private String metricValue; // valor serializado

    private String unit; // ms, %, points

    private ZonedDateTime recordedAt;
}
