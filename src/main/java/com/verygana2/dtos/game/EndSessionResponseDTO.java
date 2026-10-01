package com.verygana2.dtos.game;

/**
 * Lo que el juego puede mostrarle al jugador al cerrar la partida.
 *
 * @param rewardGranted si la sesión le cobró a la campaña (y por tanto acreditó llaves)
 * @param keysEarned    llaves acreditadas, ya con el multiplicador de nivel (enteras, igual
 *                      que la respuesta de un like: la fracción queda en la billetera)
 */
public record EndSessionResponseDTO(boolean rewardGranted, long keysEarned) {}
