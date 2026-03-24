package com.verygana2.models.enums.raffles;

public enum DrawEventType {
    WAITING_ROOM_UPDATE,  // Actualizaciones del contador y espectadores
    DRAWING_STARTED,      // Arranca la animación de boletas
    WINNER_REVEALED,      // Un ganador aparece (se emite N veces, una por ganador)
    DRAW_COMPLETED,      // Sorteo terminado, mostrar resultados finales
    DRAW_ERROR           // Para el manejo de errores
}
