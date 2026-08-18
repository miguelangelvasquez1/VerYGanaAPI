package com.verygana2.dtos.pet;

/**
 * Objeto de escena tal como lo necesita el panel del diseñador.
 *
 * Se separa de {@link PetSceneObjectResponseDTO} —el que consume el juego— porque
 * ambos quieren cosas distintas del mismo objeto:
 *
 *   · el juego recibe {@code id} y {@code url}: lo único que necesita para pintar.
 *   · el panel necesita {@code objectId} y {@code objectKey}, que son los valores
 *     que va a reenviar al guardar. Reutilizando el DTO del juego, el formulario
 *     abría esos dos campos vacíos y el diseñador no podía editar nada.
 *
 * {@code url} viaja igual para poder mostrar una miniatura del asset.
 */
public record PetSceneObjectAdminResponseDTO(
        String objectId,
        String type,
        String objectKey,
        String url,
        Integer x,
        Integer y,
        Integer width,
        Integer height,
        Double scaleMultiplier
) {}
