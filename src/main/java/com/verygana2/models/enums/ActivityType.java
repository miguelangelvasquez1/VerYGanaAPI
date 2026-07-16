package com.verygana2.models.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Actividades que generan XP.
 * Los valores base son del documento v3.0, sección 2.
 * Las llaves NO salen de aquí: se acreditan desde el presupuesto
 * de cada campaña/encuesta (ver RewardService y AdLikeServiceImpl).
 */
@Getter
@RequiredArgsConstructor
public enum ActivityType {

    //                    xpBase
    SURVEY_COMPLETED  (    30),   // encuesta
    VIDEO_WATCHED     (     5),   // video
    GAME_PLAYED       (    20),   // partida
    REFERRAL_ACTIVE   (    80),   // referido
    PURCHASE          (    10);   // compra en plataforma

    private final long xpBase;
}