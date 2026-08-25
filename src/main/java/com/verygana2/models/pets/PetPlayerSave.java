package com.verygana2.models.pets;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Progreso de la mascota de un consumidor.
 *
 * El contenido es un JSON que produce y consume el propio juego; el backend lo trata
 * como opaco. Es deliberado: el juego puede añadir claves sin que haya que tocar aquí.
 *
 * IMPORTANTE — límite de confianza: esto es estado DECLARADO POR EL CLIENTE. No debe
 * usarse como fuente de verdad para nada que valga dinero. El saldo real de llaves vive
 * en {@code key_wallets} y solo lo modifica el servidor a través de /spend; lo que venga
 * en este blob sobre monedas es cosmético.
 */
@Entity
@Table(name = "pet_player_saves")
@Data
@NoArgsConstructor
public class PetPlayerSave {

    /** Un guardado por consumidor; la PK es el propio consumerId, no hay secuencia. */
    @Id
    @Column(name = "consumer_id")
    private Long consumerId;

    /**
     * La longitud explícita es obligatoria: con {@code @Lob} y sin ella, Hibernate 6
     * asume el 255 por defecto y crea un TINYTEXT, que trunca cualquier guardado real
     * ("Data too long for column 'data'"). Con este tamaño genera MEDIUMTEXT.
     *
     * El límite efectivo no es este sino el de PetPlayerSaveServiceImpl, que rechaza
     * por encima de 64 KB antes de llegar a la base.
     */
    @Lob
    @Column(name = "data", nullable = false, length = 1_000_000)
    private String data;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public PetPlayerSave(Long consumerId, String data) {
        this.consumerId = consumerId;
        this.data = data;
        this.updatedAt = ZonedDateTime.now();
    }
}
