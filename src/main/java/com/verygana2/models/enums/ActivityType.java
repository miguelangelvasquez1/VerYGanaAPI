package com.verygana2.models.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Actividades que generan XP y llaves.
 * Los valores base son del documento v3.0, sección 2.
 */
@Getter
@RequiredArgsConstructor
public enum ActivityType {

    //                    xpBase  keysBase
    SURVEY_COMPLETED  (    30,     50),   // encuesta
    VIDEO_WATCHED     (     5,     10),   // video
    GAME_PLAYED       (    20,     30),   // partida
    REFERRAL_ACTIVE   (    80,      0),   // referido (llaves/tickets según rifa activa)
    PURCHASE          (    10,      5);   // compra en plataforma

    private final long xpBase;
    private final long keysBase;
}
