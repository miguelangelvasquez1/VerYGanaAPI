package com.verygana2.services.interfaces.pet;

import com.verygana2.dtos.pet.PetSaveDTO;

public interface PetPlayerSaveService {

    /** Guardado del consumidor, o null si todavía no ha guardado nada. */
    PetSaveDTO get(Long consumerId);

    /**
     * Sustituye el guardado completo. Última escritura gana: si el jugador tiene dos
     * pestañas abiertas, la que guarde de última se impone. Es asumible para progreso
     * de una mascota y evita el coste de resolver conflictos.
     */
    PetSaveDTO save(Long consumerId, String data);
}
