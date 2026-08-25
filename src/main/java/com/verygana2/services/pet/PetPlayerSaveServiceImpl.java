package com.verygana2.services.pet;

import com.verygana2.dtos.pet.PetSaveDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.pets.PetPlayerSave;
import com.verygana2.repositories.pet.PetPlayerSaveRepository;
import com.verygana2.services.interfaces.pet.PetPlayerSaveService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PetPlayerSaveServiceImpl implements PetPlayerSaveService {

    private final PetPlayerSaveRepository repository;

    /**
     * Tope de tamaño del blob. El contenido no se valida —es opaco— así que sin un
     * límite cualquiera podría llenar la tabla con un PUT. 64 KB sobra: el guardado
     * real son unas pocas decenas de claves cortas.
     */
    @Value("${pets.player-save.max-size-bytes:65536}")
    private int maxSizeBytes;

    @Override
    @Transactional(readOnly = true)
    public PetSaveDTO get(Long consumerId) {
        return repository.findById(consumerId)
                .map(s -> new PetSaveDTO(s.getData(), isoUtc(s.getUpdatedAt())))
                .orElse(null);
    }

    @Override
    public PetSaveDTO save(Long consumerId, String data) {
        if (data == null || data.isBlank()) {
            throw new InvalidRequestException("El guardado no puede venir vacío");
        }
        if (data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxSizeBytes) {
            throw new InvalidRequestException(
                    "El guardado supera el máximo de " + maxSizeBytes + " bytes");
        }

        PetPlayerSave entity = repository.findById(consumerId)
                .orElseGet(() -> new PetPlayerSave(consumerId, data));
        entity.setData(data);
        entity.setUpdatedAt(ZonedDateTime.now());

        PetPlayerSave saved = repository.save(entity);
        log.debug("Guardado de mascota actualizado para consumidor {} ({} bytes)",
                consumerId, data.length());

        return new PetSaveDTO(saved.getData(), isoUtc(saved.getUpdatedAt()));
    }

    /**
     * ISO-8601 en UTC. {@code ZonedDateTime.toString()} añade el identificador de zona
     * entre corchetes ("…-05:00[America/Bogota]"), que no es ISO válido: la respuesta
     * del PUT y la del GET salían con formatos distintos y cualquier parser estricto
     * del cliente se atragantaría con una de las dos.
     */
    private static String isoUtc(ZonedDateTime moment) {
        return moment == null ? null : moment.toInstant().toString();
    }
}
