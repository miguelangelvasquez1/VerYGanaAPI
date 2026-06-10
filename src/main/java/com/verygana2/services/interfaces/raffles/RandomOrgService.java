package com.verygana2.services.interfaces.raffles;

import com.verygana2.dtos.raffle.responses.RandomOrgDrawResult;

public interface RandomOrgService {

    /**
     * Genera {@code count} enteros únicos en [min, max] usando Random.org.
     * Retorna los índices junto con la metadata del request (serialNumber,
     * completionTime, bits) para incluirla en la prueba de sorteo.
     *
     * @throws com.verygana2.exceptions.rafflesExceptions.RandomOrgException si el servicio no está disponible.
     */
    RandomOrgDrawResult generateRandomIntegers(int min, int max, int count);

}
