package com.verygana2.dtos.pet;

/**
 * Guardado de la mascota tal como viaja hacia y desde el juego.
 *
 * {@code data} es una cadena, no un objeto anidado, a propósito: JsonUtility de Unity
 * no maneja bien JSON arbitrario, así que el juego serializa su propio blob y lo manda
 * como texto. Para el backend es opaco.
 *
 * @param updatedAt null al enviar; lo rellena el servidor al responder.
 */
public record PetSaveDTO(String data, String updatedAt) {
    public PetSaveDTO(String data) { this(data, null); }
}
